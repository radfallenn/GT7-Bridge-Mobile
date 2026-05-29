package com.gt7.bridge.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bouncycastle.crypto.engines.Salsa20Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

public class MainActivity extends Activity {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Telemetry t = new Telemetry();
    private TextView status, values;
    private EditText ps5Ip;
    private WebView webView;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,24);
        root.setBackgroundColor(Color.rgb(245,245,245));

        TextView title = new TextView(this); title.setText("GT7 Bridge Mobile v1.5"); title.setTextSize(22); title.setTextColor(Color.BLACK); root.addView(title);
        ps5Ip = new EditText(this); ps5Ip.setHint("IP do PS5"); ps5Ip.setSingleLine(true); root.addView(ps5Ip);

        LinearLayout buttons = new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button start = new Button(this); start.setText("Iniciar Bridge"); buttons.addView(start, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button stop = new Button(this); stop.setText("Parar"); buttons.addView(stop, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(buttons);

        Button openGt7 = new Button(this); openGt7.setText("Abrir gt7.online no app"); root.addView(openGt7);
        Button copyEntry = new Button(this); copyEntry.setText("Copiar dados para Novo Registro"); root.addView(copyEntry);
        Button copyUrl = new Button(this); copyUrl.setText("Copiar URL do Bridge"); root.addView(copyUrl);
        status = new TextView(this); status.setText("Status: parado"); status.setTextSize(15); root.addView(status);
        values = new TextView(this); values.setTextSize(13); values.setTextColor(Color.DKGRAY); root.addView(values);

        webView = new WebView(this);
        webView.setVisibility(WebView.GONE);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.addJavascriptInterface(new BridgeJs(), "GT7AndroidBridge");
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String url){ injectBridgeHints(); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request){
                String u = request.getUrl().toString();
                if(u.startsWith("http://127.0.0.1:8787") || u.startsWith("http://localhost:8787")) return localBridgeResponse(u);
                return super.shouldInterceptRequest(view, request);
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        start.setOnClickListener(v -> startBridge());
        stop.setOnClickListener(v -> stopBridge());
        openGt7.setOnClickListener(v -> openGt7Online());
        copyEntry.setOnClickListener(v -> copyText("GT7 Novo Registro", t.entryJson(), "Dados do Novo Registro copiados"));
        copyUrl.setOnClickListener(v -> copyText("GT7 Bridge URL", "http://127.0.0.1:8787/api/fields", "URL do Bridge copiada"));
        new Thread(this::httpServer).start();
        new Timer().scheduleAtFixedRate(new TimerTask(){ public void run(){ runOnUiThread(() -> { values.setText(t.toPretty()); pushTelemetryToWebView(); }); }}, 500, 500);
    }

    private void copyText(String label, String text, String msg){
        ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, text));
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private WebResourceResponse localBridgeResponse(String url){
        String body;
        synchronized(t){ body = url.contains("/api/status") ? t.status() : (url.contains("/api/map") ? t.map() : t.json()); }
        Map<String,String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "*");
        headers.put("Access-Control-Allow-Private-Network", "true");
        return new WebResourceResponse("application/json", "UTF-8", 200, "OK", headers, new ByteArrayInputStream(body.getBytes()));
    }

    public class BridgeJs {
        @JavascriptInterface public String getFields(){ synchronized(t){ return t.json(); } }
        @JavascriptInterface public String getStatus(){ synchronized(t){ return t.status(); } }
        @JavascriptInterface public String getMap(){ synchronized(t){ return t.map(); } }
        @JavascriptInterface public String getEntry(){ synchronized(t){ return t.entryJson(); } }
    }

    private void openGt7Online(){
        if(!running.get()) startBridge();
        webView.setVisibility(WebView.VISIBLE);
        status.setText("Status: abrindo gt7.online dentro do APK");
        webView.loadUrl("https://gt7.online");
    }

    private void injectBridgeHints(){
        String js = "try{"+
            "localStorage.setItem('gt7TelemetryMode','mobile-apk-push');"+
            "localStorage.setItem('gt7BridgeHttpUrl','http://127.0.0.1:8787/api/fields');"+
            "localStorage.setItem('gt7BridgeStatusUrl','http://127.0.0.1:8787/api/status');"+
            "window.GT7_BRIDGE_MODE='mobile-apk-push';"+
            "window.GT7_BRIDGE_URL='http://127.0.0.1:8787/api/fields';"+
            "window.__gt7MobileBridgeActive=true;"+
            "window.__gt7MobileTelemetrySource='android-webview-push';"+
            "window.GT7AndroidBridgeCopyEntry=function(){return GT7AndroidBridge.getEntry();};"+
            "const originalFetch=window.fetch.bind(window);"+
            "window.fetch=function(input,init){const url=(typeof input==='string'?input:(input&&input.url)||''); if(url.includes('127.0.0.1:8787')||url.includes('localhost:8787')){let data=url.includes('/api/status')?GT7AndroidBridge.getStatus():(url.includes('/api/map')?GT7AndroidBridge.getMap():GT7AndroidBridge.getFields()); return Promise.resolve(new Response(data,{status:200,headers:{'Content-Type':'application/json','Access-Control-Allow-Origin':'*'}}));} return originalFetch(input,init);};"+
            "window.dispatchEvent(new CustomEvent('gt7-bridge-mobile-ready',{detail:{url:'http://127.0.0.1:8787/api/fields',mode:'mobile-apk-push'}}));"+
            "}catch(e){console.log('GT7 Bridge inject error',e)}";
        webView.evaluateJavascript(js, null);
        pushTelemetryToWebView();
    }

    private void pushTelemetryToWebView(){
        if(webView == null || webView.getVisibility() != WebView.VISIBLE) return;
        String payload;
        String entry;
        synchronized(t){ payload = t.json(); entry = t.entryJson(); }
        String js = "try{"+
            "var d="+payload+"; var e="+entry+";"+
            "window.__gt7MobileBridgeActive=true;"+
            "window.__gt7MobileTelemetry=d;"+
            "window.__gt7MobileEntry=e;"+
            "window.__gt7LastTelemetry=d;"+
            "try{localStorage.setItem('gt7LastMobileTelemetry',JSON.stringify(d));localStorage.setItem('gt7LastMobileEntry',JSON.stringify(e));}catch(e){}"+
            "window.dispatchEvent(new CustomEvent('gt7-mobile-telemetry',{detail:d}));"+
            "window.dispatchEvent(new CustomEvent('gt7-mobile-entry',{detail:e}));"+
            "window.dispatchEvent(new CustomEvent('gt7-telemetry',{detail:d}));"+
            "window.dispatchEvent(new CustomEvent('gt7-telemetry-update',{detail:d}));"+
            "if(typeof window.onGT7MobileTelemetry==='function'){window.onGT7MobileTelemetry(d);}"+
            "if(typeof window.onGT7MobileEntry==='function'){window.onGT7MobileEntry(e);}"+
            "}catch(e){console.log('GT7 Bridge push error',e)}";
        webView.evaluateJavascript(js, null);
    }

    private void startBridge() {
        if (running.get()) return;
        synchronized(t){ t.reset(); }
        running.set(true);
        status.setText("Status: bridge ativo em modo push para gt7.online");
        new Thread(this::udpReceiver).start();
        new Thread(this::heartbeat).start();
    }
    private void stopBridge(){ running.set(false); status.setText("Status: parado"); }

    private void heartbeat(){ while(running.get()){ try{ String ip=ps5Ip.getText().toString().trim(); if(ip.length()>6){ DatagramSocket s=new DatagramSocket(); byte[] hb="C".getBytes("UTF-8"); s.send(new DatagramPacket(hb,hb.length,InetAddress.getByName(ip),33739)); s.close(); } Thread.sleep(2000); }catch(Exception ignored){} } }
    private void udpReceiver(){ try(DatagramSocket socket=new DatagramSocket(33740)){ socket.setSoTimeout(1000); byte[] buf=new byte[4096]; while(running.get()){ try{ DatagramPacket p=new DatagramPacket(buf,buf.length); socket.receive(p); byte[] raw=Arrays.copyOf(p.getData(),p.getLength()); byte[] data=decode(raw); synchronized(t){ parse(data,raw.length,t); } }catch(SocketTimeoutException ignored){} } }catch(Exception e){ runOnUiThread(() -> status.setText("Erro UDP: "+e.getMessage())); } }

    private byte[] decode(byte[] raw) throws Exception { if(raw.length<0x44)return raw; ByteBuffer bb=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN); int iv1=bb.getInt(0x40); int keyXor=raw.length==296?0xDEADBEAF:raw.length==344?0x55FABB4F:0xDEADBEEF; int iv2=iv1^keyXor; byte[] iv=new byte[8]; putLE(iv,0,iv2); putLE(iv,4,iv1); byte[] key=new byte[32]; byte[] k="Simulator Interface Packet GT7 ver 0.0".getBytes("US-ASCII"); System.arraycopy(k,0,key,0,Math.min(32,k.length)); Salsa20Engine engine=new Salsa20Engine(); engine.init(false,new ParametersWithIV(new KeyParameter(key),iv)); byte[] out=new byte[raw.length]; engine.processBytes(raw,0,raw.length,out,0); return out; }
    private void putLE(byte[] a,int off,int v){ a[off]=(byte)v; a[off+1]=(byte)(v>>8); a[off+2]=(byte)(v>>16); a[off+3]=(byte)(v>>24); }

    private void parse(byte[] d,int packetSize,Telemetry x){ x.connected=true; x.updatedAt=System.currentTimeMillis(); x.lastPacketSize=packetSize; x.packetVersion=packetName(packetSize); if(d.length<296){x.valid=false;return;} ByteBuffer b=ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN); float rpm=f(b,0x3C), fuel=f(b,0x44), cap=f(b,0x48), speed=f(b,0x4C)*3.6f; int lap=b.getShort(0x74); int best=b.getInt(0x78); int last=b.getInt(0x7C); int flags=b.getShort(0x8E)&0xffff; int gearByte=d[0x90]&255, thr=d[0x91]&255, br=d[0x92]&255; boolean ok=finite(speed)&&speed>=0&&speed<800&&finite(rpm)&&rpm>=0&&rpm<30000&&lap>-20&&lap<1000; x.valid=ok; if(!ok){x.warning="Pacote recebido, mas valores ignorados por validacao.";return;} if(x.startMs==0)x.startMs=System.currentTimeMillis(); x.warning=""; x.speed=speed; x.rpm=rpm; x.fuel=Math.max(0,fuel); x.fuelCap=cap; x.fuelPct=cap>0.1f?clamp((fuel/cap)*100f,0,100):-1; x.gear=gearByte&15; x.gearLabel=x.gear==0?"N":x.gear==15?"R":String.valueOf(x.gear); x.throttle=Math.max(0,Math.min(100,Math.round(thr/255f*100f))); x.brake=Math.max(0,Math.min(100,Math.round(br/255f*100f))); if(lap>=0)x.laps=lap; x.best=best>0?best:-1; x.last=last>0?last:-1; x.tcs=(flags&(1<<11))!=0; x.raceMs=System.currentTimeMillis()-x.startMs; x.x=f(b,0x04); x.y=f(b,0x08); x.z=f(b,0x0C); x.map.add(new float[]{x.x,x.y,x.z}); if(x.map.size()>1500)x.map.remove(0); if(d.length>=368){int cur=b.getInt(0x15C); if(cur>0)x.current=cur; x.surface=ascii(d,0x158,4);} if(x.prevFuel>=0&&x.fuel>x.prevFuel+5&&x.speed<30)x.pits++; x.prevFuel=x.fuel; }
    private String packetName(int s){ return s==296?"A":s==316?"B":s==344?"~":s==368?"C":"?"; } private float f(ByteBuffer b,int o){try{return b.getFloat(o);}catch(Exception e){return 0;}} private boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);} private float clamp(float v,float a,float z){return Math.max(a,Math.min(z,v));} private String ascii(byte[] d,int o,int l){StringBuilder s=new StringBuilder(); for(int i=0;i<l&&o+i<d.length;i++){int c=d[o+i]&255;if(c>=32&&c<=126)s.append((char)c);} return s.toString().trim();}

    private void httpServer(){ try(ServerSocket server=new ServerSocket(8787)){ while(true){ Socket c=server.accept(); BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream())); String line=br.readLine(); String path="/api/fields"; if(line!=null){String[] p=line.split(" "); if(p.length>1)path=p[1];} String body; synchronized(t){body=path.startsWith("/api/status")?t.status():path.startsWith("/api/map")?t.map():path.startsWith("/api/entry")?t.entryJson():t.json();} byte[] bytes=body.getBytes("UTF-8"); OutputStream out=c.getOutputStream(); String h="HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Allow-Private-Network: true\r\nConnection: close\r\nContent-Length: "+bytes.length+"\r\n\r\n"; out.write(h.getBytes("UTF-8")); out.write(bytes); out.flush(); c.close(); } }catch(Exception ignored){} }

    static class Telemetry{ boolean connected=false,valid=false,tcs=false; long updatedAt=0,startMs=0,raceMs=0; int lastPacketSize=0,gear=0,throttle=0,brake=0,laps=0,pits=0,best=-1,last=-1,current=-1; float speed=0,rpm=0,fuel=0,fuelCap=0,fuelPct=-1,x=0,y=0,z=0,prevFuel=-1; String gearLabel="N",packetVersion="?",warning="",surface="",rain="desconhecido"; ArrayList<float[]> map=new ArrayList<>(); void reset(){connected=false;valid=false;updatedAt=0;startMs=0;raceMs=0;lastPacketSize=0;gear=0;gearLabel="N";throttle=0;brake=0;laps=0;pits=0;best=-1;last=-1;current=-1;speed=0;rpm=0;fuel=0;fuelCap=0;fuelPct=-1;x=0;y=0;z=0;prevFuel=-1;packetVersion="?";warning="";surface="";map.clear();} String json(){return "{\"connected\":"+connected+",\"decodeOk\":"+valid+",\"status\":\""+(valid?"ok":"aguardando_dados_validos")+"\",\"velocidade\":"+r(speed)+",\"rpm\":"+Math.round(rpm)+",\"marcha\":\""+gearLabel+"\",\"marchaNumero\":"+gear+",\"acelerador\":"+throttle+",\"freio\":"+brake+",\"combustivel\":"+r(fuel)+",\"combustivelPorcentagem\":"+r(fuelPct)+",\"melhorVolta\":\""+fmt(best)+"\",\"tempoTotalCorrida\":\""+fmt((int)raceMs)+"\",\"voltasCompletadas\":"+laps+",\"chuva\":\""+rain+"\",\"controleTracao\":\""+(tcs?"ativo":"inativo")+"\",\"numeroParadas\":"+pits+",\"packetVersion\":\""+packetVersion+"\",\"lastPacketSize\":"+lastPacketSize+",\"surfaceType\":\""+surface+"\",\"position\":{\"x\":"+r(x)+",\"y\":"+r(y)+",\"z\":"+r(z)+"},\"warning\":\""+warning+"\"}";} String entryJson(){String date=new SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(new Date()); int regLaps=laps>0?laps:1; return "{\"schema\":\"gt7_entry_v1\",\"source\":\"GT7 Bridge Mobile\",\"marca\":\"\",\"carro\":\"\",\"pneus_configuracao\":\"\",\"posicao\":1,\"ct\":"+(tcs?1:0)+",\"mapeamento\":1,\"melhor_volta\":\""+fmt(best)+"\",\"tempo_total\":\""+fmt((int)raceMs)+"\",\"data\":\""+date+"\",\"clima\":\"Sol\",\"transmissao\":\"Manual\",\"periferico\":\"Controle\",\"pits\":"+pits+",\"voltas\":"+regLaps+",\"driver\":\"rAd\",\"evidence_image\":\"\",\"telemetry\":"+json()+"}";} String status(){return "{\"connected\":"+connected+",\"decodeOk\":"+valid+",\"packetVersion\":\""+packetVersion+"\",\"lastPacketSize\":"+lastPacketSize+",\"warning\":\""+warning+"\"}";} String map(){StringBuilder s=new StringBuilder("{\"points\":["); for(int i=0;i<map.size();i++){float[] p=map.get(i); if(i>0)s.append(','); s.append("{\"x\":").append(r(p[0])).append(",\"y\":").append(r(p[1])).append(",\"z\":").append(r(p[2])).append('}');} return s.append("]}").toString();} String toPretty(){return "Status: "+(valid?"dados válidos":"aguardando dados válidos")+"\nPacote: "+packetVersion+" / "+lastPacketSize+"\nVelocidade: "+(int)speed+" km/h\nRPM: "+Math.round(rpm)+"\nMarcha: "+gearLabel+"\nAcelerador: "+throttle+"%\nFreio: "+brake+"%\nCombustível: "+r(fuel)+" L\nVoltas: "+laps+(warning.length()>0?"\nAviso: "+warning:"");} String r(float v){return v<0?"null":String.format(Locale.US,"%.2f",v);} String fmt(int ms){if(ms<=0)return "--";return String.format(Locale.US,"%d:%02d.%03d",ms/60000,(ms%60000)/1000,ms%1000);} }
}
