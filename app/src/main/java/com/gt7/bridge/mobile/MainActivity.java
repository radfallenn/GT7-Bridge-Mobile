package com.gt7.bridge.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
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
    private static final String VERSION = "1.4.7";
    private static final String DEFAULT_PS5_IP = "192.168.1.54";
    private static final int BG = Color.rgb(7, 11, 20);
    private static final int CARD = Color.rgb(16, 23, 38);
    private static final int CARD_2 = Color.rgb(20, 30, 50);
    private static final int STROKE = Color.rgb(44, 63, 95);
    private static final int TXT = Color.rgb(238, 244, 255);
    private static final int MUTED = Color.rgb(144, 160, 185);
    private static final int BLUE = Color.rgb(56, 139, 253);
    private static final int GREEN = Color.rgb(63, 220, 132);
    private static final int AMBER = Color.rgb(245, 183, 72);
    private static final int RED = Color.rgb(255, 91, 105);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Telemetry t = new Telemetry();
    private EditText ps5Ip;
    private WebView webView;
    private LinearLayout content;
    private LinearLayout detailsBox;
    private LinearLayout mainDashboard;
    private LinearLayout allDataDashboard;
    private TextView statusBadge, packetBadge, updatedBadge, detailToggle;
    private final HashMap<String, TextView> valueViews = new HashMap<>();
    private boolean detailsOpen = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        new Thread(this::httpServer).start();
        new Timer().scheduleAtFixedRate(new TimerTask(){ public void run(){ runOnUiThread(() -> refreshUi()); }}, 300, 500);
    }

    private void buildUi(){
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        TextView title = text("GT7 Bridge Mobile", 24, TXT, true);
        content.addView(title);
        TextView sub = text("Telemetria PS5 • v" + VERSION, 12, MUTED, false);
        sub.setPadding(0, dp(2), 0, dp(12));
        content.addView(sub);

        buildHeroCard();
        buildTabs();
        mainDashboard = new LinearLayout(this);
        mainDashboard.setOrientation(LinearLayout.VERTICAL);
        allDataDashboard = new LinearLayout(this);
        allDataDashboard.setOrientation(LinearLayout.VERTICAL);
        allDataDashboard.setVisibility(View.GONE);
        content.addView(mainDashboard);
        content.addView(allDataDashboard);
        buildMainDashboard();
        buildAllDataDashboard();

        webView = new WebView(this);
        webView.setVisibility(WebView.GONE);
        webView.setBackgroundColor(Color.WHITE);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new BridgeJs(), "GT7AndroidBridge");
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String url){ injectBridgeHints(); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request){
                String u = request.getUrl().toString();
                if(u.startsWith("http://127.0.0.1:8787") || u.startsWith("http://localhost:8787")) return localBridgeResponse(u);
                return super.shouldInterceptRequest(view, request);
            }
        });
        content.addView(webView, new LinearLayout.LayoutParams(-1, dp(0)));
    }

    private void buildHeroCard(){
        LinearLayout hero = card();
        hero.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.addView(hero);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = text("Status da Telemetria", 17, TXT, true);
        top.addView(h, new LinearLayout.LayoutParams(0, -2, 1));
        statusBadge = badge("PARADO", AMBER);
        top.addView(statusBadge);
        hero.addView(top);

        ps5Ip = new EditText(this);
        ps5Ip.setTextColor(TXT);
        ps5Ip.setHintTextColor(MUTED);
        ps5Ip.setTextSize(15);
        ps5Ip.setSingleLine(true);
        ps5Ip.setHint("IP do PS5");
        ps5Ip.setText(getSharedPreferences("gt7_bridge", MODE_PRIVATE).getString("ps5_ip", DEFAULT_PS5_IP));
        ps5Ip.setPadding(dp(12), 0, dp(12), 0);
        ps5Ip.setBackground(round(CARD_2, dp(14), STROKE));
        ps5Ip.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ getSharedPreferences("gt7_bridge", MODE_PRIVATE).edit().putString("ps5_ip", s.toString()).apply(); } public void afterTextChanged(Editable e){} });
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(-1, dp(48));
        ipLp.setMargins(0, dp(12), 0, dp(10));
        hero.addView(ps5Ip, ipLp);

        LinearLayout badges = new LinearLayout(this);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        packetBadge = badge("Pacote ? / 0", BLUE);
        updatedBadge = badge("Sem atualização", AMBER);
        badges.addView(packetBadge);
        Space sp = new Space(this); badges.addView(sp, new LinearLayout.LayoutParams(dp(8), 1));
        badges.addView(updatedBadge);
        hero.addView(badges);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1 = new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout r2 = new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL);
        Button start = actionButton("Iniciar Bridge", GREEN);
        Button stop = actionButton("Parar", RED);
        Button save = actionButton("Salvar Sessão", BLUE);
        Button open = actionButton("Abrir gt7.online", AMBER);
        r1.addView(start, new LinearLayout.LayoutParams(0, dp(44), 1));
        r1.addView(stop, new LinearLayout.LayoutParams(0, dp(44), 1));
        r2.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        r2.addView(open, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2); rlp.setMargins(0, dp(12), 0, 0);
        actions.addView(r1, rlp);
        LinearLayout.LayoutParams rlp2 = new LinearLayout.LayoutParams(-1, -2); rlp2.setMargins(0, dp(8), 0, 0);
        actions.addView(r2, rlp2);
        hero.addView(actions);
        start.setOnClickListener(v -> startBridge());
        stop.setOnClickListener(v -> { saveSession("auto_stop"); stopBridge(); });
        save.setOnClickListener(v -> saveSession("manual"));
        open.setOnClickListener(v -> openSite());

        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2); mlp.setMargins(0, dp(12), 0, 0);
        mini.addView(miniMetric("Vel.", "speedMini"), new LinearLayout.LayoutParams(0, -2, 1));
        mini.addView(miniMetric("RPM", "rpmMini"), new LinearLayout.LayoutParams(0, -2, 1));
        mini.addView(miniMetric("Marcha", "gearMini"), new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(mini, mlp);
    }

    private void buildTabs(){
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(12), 0, dp(8));
        content.addView(tabs, lp);
        Button main = actionButton("Telemetria", BLUE);
        Button all = actionButton("Todos os Dados", CARD_2);
        tabs.addView(main, new LinearLayout.LayoutParams(0, dp(42), 1));
        tabs.addView(all, new LinearLayout.LayoutParams(0, dp(42), 1));
        main.setOnClickListener(v -> { mainDashboard.setVisibility(View.VISIBLE); allDataDashboard.setVisibility(View.GONE); });
        all.setOnClickListener(v -> { mainDashboard.setVisibility(View.GONE); allDataDashboard.setVisibility(View.VISIBLE); });
    }

    private void buildMainDashboard(){
        mainDashboard.addView(section("Telemetria ao Vivo"));
        LinearLayout live1 = grid();
        live1.addView(metricCard("Velocidade", "velocidade", "km/h"), new LinearLayout.LayoutParams(0, -2, 1));
        live1.addView(metricCard("Vel. Máxima", "velocidadeMaxima", "km/h"), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(live1);
        LinearLayout live2 = grid();
        live2.addView(metricCard("RPM", "rpm", ""), new LinearLayout.LayoutParams(0, -2, 1));
        live2.addView(metricCard("Marcha", "marcha", ""), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(live2);
        LinearLayout live3 = grid();
        live3.addView(metricCard("Acelerador", "acelerador", "%"), new LinearLayout.LayoutParams(0, -2, 1));
        live3.addView(metricCard("Freio", "freio", "%"), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(live3);
        LinearLayout live4 = grid();
        live4.addView(metricCard("Combustível", "combustivel", "L"), new LinearLayout.LayoutParams(0, -2, 1));
        live4.addView(metricCard("Combustível", "combustivelPorcentagem", "%"), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(live4);

        mainDashboard.addView(section("Voltas e Prova"));
        LinearLayout race1 = grid();
        race1.addView(metricCard("Melhor Volta", "melhorVolta", ""), new LinearLayout.LayoutParams(0, -2, 1));
        race1.addView(metricCard("Última Volta", "ultimaVolta", ""), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(race1);
        LinearLayout race2 = grid();
        race2.addView(metricCard("Volta Atual", "voltaAtual", ""), new LinearLayout.LayoutParams(0, -2, 1));
        race2.addView(metricCard("Tempo Total", "tempoTotalCorrida", ""), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(race2);
        LinearLayout race3 = grid();
        race3.addView(metricCard("Voltas Brutas", "voltasCompletadas", ""), new LinearLayout.LayoutParams(0, -2, 1));
        race3.addView(metricCard("Voltas Corrigidas", "voltasCorrigidas", ""), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(race3);

        mainDashboard.addView(section("Mapa e Posição"));
        LinearLayout pos = grid();
        pos.addView(metricCard("X", "posX", ""), new LinearLayout.LayoutParams(0, -2, 1));
        pos.addView(metricCard("Y", "posY", ""), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(pos);
        LinearLayout pos2 = grid();
        pos2.addView(metricCard("Z", "posZ", ""), new LinearLayout.LayoutParams(0, -2, 1));
        pos2.addView(metricCard("Superfície", "surfaceType", ""), new LinearLayout.LayoutParams(0, -2, 1));
        mainDashboard.addView(pos2);

        LinearLayout detailsCard = card();
        detailToggle = text("Mostrar detalhes do Bridge", 15, TXT, true);
        detailToggle.setPadding(0, 0, 0, dp(8));
        detailsCard.addView(detailToggle);
        detailsBox = new LinearLayout(this);
        detailsBox.setOrientation(LinearLayout.VERTICAL);
        detailsBox.setVisibility(View.GONE);
        detailsCard.addView(detailsBox);
        String[] keys = {"status", "packetVersion", "lastPacketSize", "warning", "updatedAt"};
        String[] labels = {"Status", "Versão do pacote", "Tamanho do pacote", "Aviso", "Última atualização"};
        for(int i=0;i<keys.length;i++) detailsBox.addView(rowValue(labels[i], keys[i]));
        detailToggle.setOnClickListener(v -> { detailsOpen = !detailsOpen; detailsBox.setVisibility(detailsOpen ? View.VISIBLE : View.GONE); detailToggle.setText(detailsOpen ? "Recolher detalhes do Bridge" : "Mostrar detalhes do Bridge"); });
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2); dlp.setMargins(0, dp(10), 0, 0);
        mainDashboard.addView(detailsCard, dlp);
    }

    private void buildAllDataDashboard(){
        allDataDashboard.addView(section("Todos os Dados da Telemetria"));
        String[][] groups = {
            {"Status do Bridge", "connected,decodeOk,status,updatedAt,packetVersion,lastPacketSize,warning"},
            {"Carro ao Vivo", "velocidade,velocidadeMaxima,rpm,marcha,marchaNumero,acelerador,freio,combustivel,combustivelPorcentagem,fuelCapacity"},
            {"Voltas e Tempo", "melhorVolta,ultimaVolta,voltaAtual,tempoTotalCorrida,voltasCompletadas,voltasCorrigidas"},
            {"Posição e Mapa", "posX,posY,posZ,surfaceType,mapPoints"},
            {"Sessão e Registro", "velocidade_final,telemetria_completa"}
        };
        for(String[] g: groups){
            LinearLayout box = card();
            TextView head = text(g[0], 16, TXT, true);
            box.addView(head);
            LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(0, dp(8), 0, 0);
            box.addView(body);
            for(String key: g[1].split(",")) body.addView(rowValue(labelFor(key), key));
            head.setOnClickListener(v -> body.setVisibility(body.getVisibility()==View.VISIBLE ? View.GONE : View.VISIBLE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, 0);
            allDataDashboard.addView(box, lp);
        }
    }

    private LinearLayout metricCard(String label, String key, String unit){
        LinearLayout c = card();
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView l = text(label, 11, MUTED, true); c.addView(l);
        TextView v = text("--", 22, TXT, true); v.setPadding(0, dp(6), 0, 0); c.addView(v);
        TextView u = text(unit, 10, MUTED, false); c.addView(u);
        valueViews.put(key, v);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, dp(112)); mlp.setMargins(dp(4), dp(4), dp(4), dp(4)); c.setLayoutParams(mlp);
        return c;
    }

    private LinearLayout miniMetric(String label, String key){
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER); c.setPadding(dp(6), dp(8), dp(6), dp(8)); c.setBackground(round(CARD_2, dp(14), STROKE));
        TextView l = text(label, 10, MUTED, true); l.setGravity(Gravity.CENTER); c.addView(l);
        TextView v = text("--", 18, TXT, true); v.setGravity(Gravity.CENTER); c.addView(v); valueViews.put(key, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(dp(3), 0, dp(3), 0); c.setLayoutParams(lp); return c;
    }

    private LinearLayout rowValue(String label, String key){
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setPadding(0, dp(6), 0, dp(6));
        TextView l = text(label, 12, MUTED, false); r.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text("--", 12, TXT, true); v.setGravity(Gravity.RIGHT); r.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        valueViews.put(key, v); return r;
    }

    private void refreshUi(){
        Telemetry snap;
        synchronized(t){ snap = t.copy(); }
        statusBadge.setText(snap.valid ? "DADOS VÁLIDOS" : (running.get() ? "AGUARDANDO" : "PARADO"));
        statusBadge.setTextColor(snap.valid ? GREEN : (running.get() ? AMBER : MUTED));
        packetBadge.setText("Pacote " + snap.packetVersion + " / " + snap.lastPacketSize);
        updatedBadge.setText(snap.updatedAt > 0 ? "Atualizado" : "Sem dados");
        set("speedMini", Math.round(snap.speed) + "");
        set("rpmMini", Math.round(snap.rpm) + "");
        set("gearMini", snap.gearLabel);
        set("velocidade", Math.round(snap.speed) + "");
        set("velocidadeMaxima", Math.round(snap.maxSpeed) + "");
        set("rpm", Math.round(snap.rpm) + "");
        set("marcha", snap.gearLabel);
        set("marchaNumero", String.valueOf(snap.gear));
        set("acelerador", snap.throttle + "");
        set("freio", snap.brake + "");
        set("combustivel", snap.fuel >= 0 ? snap.r(snap.fuel) : "--");
        set("combustivelPorcentagem", snap.fuelPct >= 0 ? snap.r(snap.fuelPct) : "--");
        set("fuelCapacity", snap.fuelCap > 0 ? snap.r(snap.fuelCap) : "--");
        set("melhorVolta", snap.fmt(snap.best));
        set("ultimaVolta", snap.fmt(snap.last));
        set("voltaAtual", snap.fmt(snap.current));
        set("tempoTotalCorrida", snap.fmt((int)snap.raceMs));
        set("voltasCompletadas", String.valueOf(snap.laps));
        set("voltasCorrigidas", String.valueOf(Math.max(0, snap.laps - 1)));
        set("posX", snap.r(snap.x)); set("posY", snap.r(snap.y)); set("posZ", snap.r(snap.z));
        set("surfaceType", snap.surface.length()>0 ? snap.surface : "--");
        set("connected", String.valueOf(snap.connected)); set("decodeOk", String.valueOf(snap.valid));
        set("status", snap.valid ? "ok" : "aguardando_dados_validos");
        set("updatedAt", snap.updatedAt > 0 ? new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(snap.updatedAt)) : "--");
        set("packetVersion", snap.packetVersion); set("lastPacketSize", String.valueOf(snap.lastPacketSize)); set("warning", snap.warning.length()>0 ? snap.warning : "--");
        set("mapPoints", String.valueOf(snap.map.size())); set("velocidade_final", Math.round(snap.speed) + " km/h"); set("telemetria_completa", "snapshot ativo");
        pushTelemetry();
    }

    private void set(String k, String v){ TextView tv = valueViews.get(k); if(tv != null) tv.setText(v == null || v.length()==0 ? "--" : v); }

    private void startBridge(){
        if(running.get()) { Toast.makeText(this, "Bridge já está ativo", Toast.LENGTH_SHORT).show(); return; }
        synchronized(t){ t.resetSession(); }
        running.set(true);
        new Thread(this::udpReceiver).start();
        new Thread(this::heartbeat).start();
    }
    private void stopBridge(){ running.set(false); }
    private void openSite(){ webView.setVisibility(View.VISIBLE); webView.getLayoutParams().height = dp(420); webView.requestLayout(); webView.loadUrl("https://gt7.online"); }
    private void saveSession(String reason){ synchronized(t){ t.saveReason = reason; } getSharedPreferences("gt7_bridge", MODE_PRIVATE).edit().putString("last_session", t.entryJson()).apply(); Toast.makeText(this, "Sessão salva", Toast.LENGTH_SHORT).show(); }

    private void heartbeat(){ while(running.get()){ try{ String ip=ps5Ip.getText().toString().trim(); if(ip.length()>6){ DatagramSocket s=new DatagramSocket(); byte[] hb="C".getBytes("UTF-8"); s.send(new DatagramPacket(hb,hb.length,InetAddress.getByName(ip),33739)); s.close(); } Thread.sleep(2000); }catch(Exception ignored){} } }
    private void udpReceiver(){ try(DatagramSocket socket=new DatagramSocket(33740)){ socket.setSoTimeout(1000); byte[] buf=new byte[4096]; while(running.get()){ try{ DatagramPacket p=new DatagramPacket(buf,buf.length); socket.receive(p); byte[] raw=Arrays.copyOf(p.getData(),p.getLength()); byte[] data=decode(raw); synchronized(t){ parse(data,raw.length,t); } }catch(SocketTimeoutException ignored){} } }catch(Exception e){ runOnUiThread(() -> Toast.makeText(this, "Erro UDP: "+e.getMessage(), Toast.LENGTH_LONG).show()); } }

    private byte[] decode(byte[] raw) throws Exception { if(raw.length<0x44)return raw; ByteBuffer bb=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN); int iv1=bb.getInt(0x40); int keyXor=raw.length==296?0xDEADBEAF:raw.length==344?0x55FABB4F:0xDEADBEEF; int iv2=iv1^keyXor; byte[] iv=new byte[8]; putLE(iv,0,iv2); putLE(iv,4,iv1); byte[] key=new byte[32]; byte[] k="Simulator Interface Packet GT7 ver 0.0".getBytes("US-ASCII"); System.arraycopy(k,0,key,0,Math.min(32,k.length)); Salsa20Engine engine=new Salsa20Engine(); engine.init(false,new ParametersWithIV(new KeyParameter(key),iv)); byte[] out=new byte[raw.length]; engine.processBytes(raw,0,raw.length,out,0); return out; }
    private void parse(byte[] d,int packetSize,Telemetry x){ x.connected=true; x.updatedAt=System.currentTimeMillis(); x.lastPacketSize=packetSize; x.packetVersion=packetName(packetSize); if(d.length<296){x.valid=false;return;} ByteBuffer b=ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN); float rpm=f(b,0x3C),fuel=f(b,0x44),cap=f(b,0x48),speed=f(b,0x4C)*3.6f; int lap=b.getShort(0x74); int best=b.getInt(0x78); int last=b.getInt(0x7C); int gearByte=d[0x90]&255,thr=d[0x91]&255,br=d[0x92]&255; boolean ok=finite(speed)&&speed>=0&&speed<800&&finite(rpm)&&rpm>=0&&rpm<30000&&lap>-20&&lap<1000; x.valid=ok; if(!ok){x.warning="Pacote recebido, mas valores ignorados por validação.";return;} if(x.startMs==0)x.startMs=System.currentTimeMillis(); x.warning=""; x.speed=speed; x.maxSpeed=Math.max(x.maxSpeed,speed); x.rpm=rpm; x.fuel=Math.max(0,fuel); x.fuelCap=cap; x.fuelPct=cap>0.1f?clamp((fuel/cap)*100f,0,100):-1; x.gear=gearByte&15; x.gearLabel=x.gear==0?"N":x.gear==15?"R":String.valueOf(x.gear); x.throttle=Math.max(0,Math.min(100,Math.round(thr/255f*100f))); x.brake=Math.max(0,Math.min(100,Math.round(br/255f*100f))); if(lap>=0)x.laps=lap; if(best>0)x.best=best; if(last>0)x.last=last; x.raceMs=System.currentTimeMillis()-x.startMs; x.x=f(b,0x04); x.y=f(b,0x08); x.z=f(b,0x0C); x.map.add(new float[]{x.x,x.y,x.z}); if(x.map.size()>1500)x.map.remove(0); if(d.length>=368){int cur=b.getInt(0x15C); if(cur>0)x.current=cur; x.surface=ascii(d,0x158,4);} }

    private WebResourceResponse localBridgeResponse(String url){ String body; synchronized(t){ body=url.contains("/api/status")?t.statusJson():url.contains("/api/map")?t.mapJson():url.contains("/api/entry")?t.entryJson():t.fieldsJson(); } Map<String,String> h=new HashMap<>(); h.put("Access-Control-Allow-Origin","*"); h.put("Access-Control-Allow-Methods","GET, OPTIONS"); h.put("Access-Control-Allow-Headers","*"); h.put("Access-Control-Allow-Private-Network","true"); return new WebResourceResponse("application/json","UTF-8",200,"OK",h,new ByteArrayInputStream(body.getBytes())); }
    private void httpServer(){ try(ServerSocket server=new ServerSocket(8787)){ while(true){ Socket c=server.accept(); BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream())); String line=br.readLine(); String path="/api/fields"; if(line!=null){String[] p=line.split(" "); if(p.length>1)path=p[1];} String body; synchronized(t){body=path.startsWith("/api/status")?t.statusJson():path.startsWith("/api/map")?t.mapJson():path.startsWith("/api/entry")?t.entryJson():t.fieldsJson();} byte[] bytes=body.getBytes("UTF-8"); OutputStream out=c.getOutputStream(); String hdr="HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Allow-Private-Network: true\r\nConnection: close\r\nContent-Length: "+bytes.length+"\r\n\r\n"; out.write(hdr.getBytes("UTF-8")); out.write(bytes); out.flush(); c.close(); } }catch(Exception ignored){} }
    public class BridgeJs { @JavascriptInterface public String getFields(){ synchronized(t){ return t.fieldsJson(); } } @JavascriptInterface public String getStatus(){ synchronized(t){ return t.statusJson(); } } @JavascriptInterface public String getMap(){ synchronized(t){ return t.mapJson(); } } @JavascriptInterface public String getEntry(){ synchronized(t){ return t.entryJson(); } } }
    private void injectBridgeHints(){ String js="try{"+"localStorage.setItem('gt7TelemetryMode','mobile-apk-push');"+"localStorage.setItem('gt7BridgeHttpUrl','http://127.0.0.1:8787/api/fields');"+"localStorage.setItem('gt7BridgeEntryUrl','http://127.0.0.1:8787/api/entry');"+"window.GT7_BRIDGE_MODE='mobile-apk-push';"+"window.GT7_BRIDGE_URL='http://127.0.0.1:8787/api/fields';"+"window.__gt7MobileBridgeActive=true;"+"const originalFetch=window.fetch.bind(window);"+"window.fetch=function(input,init){const url=(typeof input==='string'?input:(input&&input.url)||'');if(url.includes('127.0.0.1:8787')||url.includes('localhost:8787')){let data=url.includes('/api/status')?GT7AndroidBridge.getStatus():(url.includes('/api/map')?GT7AndroidBridge.getMap():(url.includes('/api/entry')?GT7AndroidBridge.getEntry():GT7AndroidBridge.getFields()));return Promise.resolve(new Response(data,{status:200,headers:{'Content-Type':'application/json'}}));}return originalFetch(input,init);};"+"window.dispatchEvent(new CustomEvent('gt7-bridge-mobile-ready',{detail:{mode:'mobile-apk-push'}}));"+"}catch(e){console.log('GT7 Bridge inject error',e)}"; webView.evaluateJavascript(js,null); pushTelemetry(); }
    private void pushTelemetry(){ if(webView==null||webView.getVisibility()!=View.VISIBLE)return; String payload,entry; synchronized(t){payload=t.fieldsJson(); entry=t.entryJson();} String js="try{var d="+payload+";var e="+entry+";window.__gt7MobileTelemetry=d;window.__gt7MobileEntry=e;localStorage.setItem('gt7LastMobileTelemetry',JSON.stringify(d));localStorage.setItem('gt7LastMobileEntry',JSON.stringify(e));window.dispatchEvent(new CustomEvent('gt7-mobile-telemetry',{detail:d}));window.dispatchEvent(new CustomEvent('gt7-mobile-entry',{detail:e}));window.dispatchEvent(new CustomEvent('gt7-telemetry-update',{detail:d}));}catch(e){}"; webView.evaluateJavascript(js,null); }

    private LinearLayout card(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(round(CARD,dp(18),STROKE)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(8),0,dp(8)); l.setLayoutParams(lp); l.setPadding(dp(12),dp(12),dp(12),dp(12)); return l; }
    private LinearLayout grid(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private TextView section(String s){ TextView t=text(s,16,TXT,true); t.setPadding(0,dp(16),0,dp(4)); return t; }
    private TextView text(String s,int sp,int color,boolean bold){ TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private TextView badge(String s,int color){ TextView t=text(s,11,color,true); t.setPadding(dp(9),dp(5),dp(9),dp(5)); t.setBackground(round(Color.argb(45,Color.red(color),Color.green(color),Color.blue(color)),dp(99),color)); return t; }
    private Button actionButton(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setTextColor(TXT); b.setAllCaps(false); b.setBackground(round(color,dp(14),color)); return b; }
    private GradientDrawable round(int color,int radius,int stroke){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); g.setStroke(dp(1),stroke); return g; }
    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    private String labelFor(String k){ return k.replace("position.","").replace("velocidadeMaxima","Velocidade máxima").replace("combustivelPorcentagem","Combustível %").replace("tempoTotalCorrida","Tempo total").replace("voltasCorrigidas","Voltas corrigidas").replace("voltasCompletadas","Voltas brutas"); }
    private void putLE(byte[] a,int off,int v){a[off]=(byte)v;a[off+1]=(byte)(v>>8);a[off+2]=(byte)(v>>16);a[off+3]=(byte)(v>>24);} private String packetName(int s){return s==296?"A":s==316?"B":s==344?"~":s==368?"C":"?";} private float f(ByteBuffer b,int o){try{return b.getFloat(o);}catch(Exception e){return 0;}} private boolean finite(float v){return !Float.isNaN(v)&&!Float.isInfinite(v);} private float clamp(float v,float a,float z){return Math.max(a,Math.min(z,v));} private String ascii(byte[] d,int o,int l){StringBuilder s=new StringBuilder(); for(int i=0;i<l&&o+i<d.length;i++){int c=d[o+i]&255;if(c>=32&&c<=126)s.append((char)c);} return s.toString().trim();}

    static class Telemetry { boolean connected=false,valid=false; long updatedAt=0,startMs=0,raceMs=0; int lastPacketSize=0,gear=0,throttle=0,brake=0,laps=0,best=-1,last=-1,current=-1; float speed=0,maxSpeed=0,rpm=0,fuel=0,fuelCap=0,fuelPct=-1,x=0,y=0,z=0; String gearLabel="N",packetVersion="?",warning="",surface="",saveReason=""; ArrayList<float[]> map=new ArrayList<>(); Telemetry copy(){Telemetry n=new Telemetry(); n.connected=connected;n.valid=valid;n.updatedAt=updatedAt;n.startMs=startMs;n.raceMs=raceMs;n.lastPacketSize=lastPacketSize;n.gear=gear;n.throttle=throttle;n.brake=brake;n.laps=laps;n.best=best;n.last=last;n.current=current;n.speed=speed;n.maxSpeed=maxSpeed;n.rpm=rpm;n.fuel=fuel;n.fuelCap=fuelCap;n.fuelPct=fuelPct;n.x=x;n.y=y;n.z=z;n.gearLabel=gearLabel;n.packetVersion=packetVersion;n.warning=warning;n.surface=surface;n.map=new ArrayList<>(map);return n;} void resetSession(){connected=false;valid=false;updatedAt=0;startMs=0;raceMs=0;lastPacketSize=0;gear=0;throttle=0;brake=0;laps=0;best=-1;last=-1;current=-1;speed=0;maxSpeed=0;rpm=0;fuel=0;fuelCap=0;fuelPct=-1;x=0;y=0;z=0;gearLabel="N";packetVersion="?";warning="";surface="";map.clear();} String fieldsJson(){return "{\"connected\":"+connected+",\"decodeOk\":"+valid+",\"status\":\""+(valid?"ok":"aguardando_dados_validos")+"\",\"updatedAt\":"+updatedAt+",\"velocidade\":"+r(speed)+",\"velocidadeMaxima\":"+r(maxSpeed)+",\"rpm\":"+Math.round(rpm)+",\"marcha\":\""+gearLabel+"\",\"marchaNumero\":"+gear+",\"acelerador\":"+throttle+",\"freio\":"+brake+",\"combustivel\":"+r(fuel)+",\"combustivelPorcentagem\":"+r(fuelPct)+",\"melhorVolta\":\""+fmt(best)+"\",\"ultimaVolta\":\""+fmt(last)+"\",\"voltaAtual\":\""+fmt(current)+"\",\"tempoTotalCorrida\":\""+fmt((int)raceMs)+"\",\"voltasCompletadas\":"+laps+",\"voltasCorrigidas\":"+Math.max(0,laps-1)+",\"packetVersion\":\""+packetVersion+"\",\"lastPacketSize\":"+lastPacketSize+",\"surfaceType\":\""+surface+"\",\"position\":{\"x\":"+r(x)+",\"y\":"+r(y)+",\"z\":"+r(z)+"},\"warning\":\""+esc(warning)+"\"}";} String entryJson(){String date=new SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(new Date());return "{\"schema\":\"gt7_entry_v1_4_7\",\"source\":\"GT7 Bridge Mobile v1.4.7\",\"data\":\""+date+"\",\"melhor_volta\":\""+fmt(best)+"\",\"ultima_volta\":\""+fmt(last)+"\",\"tempo_total\":\""+fmt((int)raceMs)+"\",\"voltas\":"+Math.max(0,laps-1)+",\"velocidade_maxima\":\""+Math.round(maxSpeed)+" km/h\",\"velocidade_final\":\""+Math.round(speed)+" km/h\",\"telemetria_completa\":"+fieldsJson()+"}";} String statusJson(){return "{\"connected\":"+connected+",\"decodeOk\":"+valid+",\"packetVersion\":\""+packetVersion+"\",\"lastPacketSize\":"+lastPacketSize+",\"warning\":\""+esc(warning)+"\"}";} String mapJson(){StringBuilder s=new StringBuilder("{\"points\":[");for(int i=0;i<map.size();i++){float[] p=map.get(i);if(i>0)s.append(',');s.append("{\"x\":").append(r(p[0])).append(",\"y\":").append(r(p[1])).append(",\"z\":").append(r(p[2])).append('}');}return s.append("]}").toString();} String r(float v){return v<0?"null":String.format(Locale.US,"%.2f",v);} String fmt(int ms){if(ms<=0)return "--";return String.format(Locale.US,"%d:%02d.%03d",ms/60000,(ms%60000)/1000,ms%1000);} String esc(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"");} }
}
