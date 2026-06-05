package com.gt7.bridge.mobile;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import android.content.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String PREF="gt7_v169_layout";
    static final String KEY_URL="bridge_url";
    static final String DEF_URL="http://192.168.1.70:8787";
    Handler handler=new Handler(Looper.getMainLooper());
    Dash dash;
    Tel tel=new Tel();
    String url;
    boolean running=false;
    Runnable poll=new Runnable(){ public void run(){ readTel(); handler.postDelayed(this,350); }};

    public void onCreate(Bundle b){
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        url=getSharedPreferences(PREF,0).getString(KEY_URL,DEF_URL);
        dash=new Dash(this);
        setContentView(dash);
        start();
    }
    protected void onResume(){super.onResume();start();}
    protected void onPause(){super.onPause();stop();}
    void start(){if(!running){running=true;handler.post(poll);}}
    void stop(){running=false;handler.removeCallbacks(poll);}    
    void readTel(){new Thread(()->{try{String s=http(url+"/api/fields"); if(s==null||s.trim().length()==0)s=http(url+"/api/telemetry"); Tel n=Tel.parse(s,tel); n.status="ONLINE"; tel=n; dash.push(n);}catch(Exception e){tel.status="OFFLINE";} runOnUiThread(()->dash.invalidate());}).start();}
    String http(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(800);c.setReadTimeout(800);BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder sb=new StringBuilder();String l;while((l=br.readLine())!=null)sb.append(l);br.close();c.disconnect();return sb.toString();}
    void bridgeDialog(){EditText in=new EditText(this);in.setText(url);new AlertDialog.Builder(this).setTitle("Bridge GT7").setView(in).setPositiveButton("Salvar",(d,w)->{url=in.getText().toString().trim();if(!url.startsWith("http"))url="http://"+url;getSharedPreferences(PREF,0).edit().putString(KEY_URL,url).apply();}).setNegativeButton("Cancelar",null).show();}

    static class Tel{
        String status="OFFLINE",gear="N",track="TRACK",last="--",best="--",total="--";
        int speed=0,rpm=0,throttle=0,brake=0,laps=0;
        float fuel=-1,fuelL=-1,water=Float.NaN,oil=Float.NaN,intake=Float.NaN,turbo=Float.NaN,gLat=Float.NaN,gLong=Float.NaN,gVert=Float.NaN,x=Float.NaN,y=Float.NaN,z=Float.NaN;
        static Tel parse(String body,Tel old)throws Exception{String s=body.trim();int a=s.indexOf('{'),b=s.lastIndexOf('}');JSONObject o=new JSONObject(a>=0&&b>a?s.substring(a,b+1):s);if(o.has("fields")&&o.get("fields") instanceof JSONObject)o=o.getJSONObject("fields");if(o.has("telemetry")&&o.get("telemetry") instanceof JSONObject)o=o.getJSONObject("telemetry");Tel t=new Tel();t.speed=i(o,old.speed,"speed_kmh","speed","velocidade");t.rpm=i(o,old.rpm,"rpm","engine_rpm");t.gear=str(o,old.gear,"gear","marcha");if(t.gear.equals("0"))t.gear="N";t.throttle=i(o,old.throttle,"throttle","throttle_percent");t.brake=i(o,old.brake,"brake","brake_percent");t.track=str(o,old.track,"trackName","track","pista");t.last=str(o,old.last,"lastLap","last_lap_text","ultimaVolta","last_lap");t.best=str(o,old.best,"bestLap","best_lap_text","melhorVolta","best_lap");t.total=str(o,old.total,"totalRaceTime","total_race_time_text","tempoTotal");t.laps=i(o,old.laps,"lap","currentLap","completed_laps","voltasCorrigidas");t.fuel=f(o,old.fuel,"fuelPercent","fuel_percent","combustivelPorcentagem");t.fuelL=f(o,old.fuelL,"fuel","fuelLiters","fuel_liters");t.water=f(o,old.water,"coolantTemp","water_temp","temperaturaAgua");t.oil=f(o,old.oil,"oilTemp","oil_temp");t.intake=f(o,old.intake,"intakeTemp","intake_temp");t.turbo=f(o,old.turbo,"turbo","boostPressure","turbo_pressure");t.gLat=f(o,old.gLat,"gForceLat","g_lat","lateralG");t.gLong=f(o,old.gLong,"gForceLong","g_long","longitudinalG");t.gVert=f(o,old.gVert,"gForceVert","g_vertical");if(o.has("position")&&o.get("position") instanceof JSONObject){JSONObject p=o.getJSONObject("position");t.x=f(p,old.x,"x");t.y=f(p,old.y,"y");t.z=f(p,old.z,"z");}else{t.x=f(o,old.x,"x","posX");t.y=f(o,old.y,"y","posY");t.z=f(o,old.z,"z","posZ");}return t;}
        static int i(JSONObject o,int d,String...k){return Math.round(f(o,d,k));}
        static float f(JSONObject o,float d,String...k){for(String s:k)try{if(o.has(s)&&!o.isNull(s)){float v=Float.parseFloat(String.valueOf(o.get(s)).replace(",","."));if(!Float.isNaN(v)&&!Float.isInfinite(v))return v;}}catch(Exception e){}return d;}
        static String str(JSONObject o,String d,String...k){for(String s:k)try{if(o.has(s)&&!o.isNull(s)){String v=String.valueOf(o.get(s));if(v.trim().length()>0&&!v.equals("--"))return v;}}catch(Exception e){}return d;}
    }

    class Dash extends View{
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); RectF r=new RectF(); SharedPreferences sp=getSharedPreferences(PREF,0);
        String[] titles={"TELEMETRY","LAST LAP","BEST LAP","TIRE STATUS","ENGINE TEMP","FUEL LEVEL","G-FORCE","BOOST PRESSURE","TRACK MAP"};
        boolean[] closed=new boolean[9]; float[] xs=new float[9],ys=new float[9],ws=new float[9],hs=new float[9];
        float scroll=0,lastX,lastY,downX,downY; int selected=-1,mode=0; boolean edit=false; float[] gl=new float[64],gg=new float[64]; int gi=0;
        Dash(Activity a){super(a);setBackgroundColor(Color.BLACK);for(int i=0;i<9;i++){closed[i]=sp.getBoolean("c"+i,false);xs[i]=sp.getFloat("x"+i,15);ys[i]=sp.getFloat("y"+i,405+i*132);ws[i]=sp.getFloat("w"+i,400);hs[i]=sp.getFloat("h"+i,122);}}
        void push(Tel t){gl[gi]=Float.isNaN(t.gLat)?0:Math.max(-2,Math.min(2,t.gLat));gg[gi]=Float.isNaN(t.gLong)?0:Math.max(-2,Math.min(2,t.gLong));gi=(gi+1)%gl.length;}
        protected void onDraw(Canvas c){float s=getWidth()/430f;c.save();c.scale(s,s);draw(c,430,getHeight()/s);c.restore();}
        void draw(Canvas c,float w,float h){p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(0,5,11));c.drawRect(0,0,w,h,p);top(c,w);gauge(c,w/2,178);chips(c);chart(c,15,312,400,78);c.save();c.translate(0,-scroll);for(int i=0;i<9;i++)card(c,i);c.restore();if(edit)bar(c,w,h);}        
        void top(Canvas c,float w){button(c,12,12,42,42,"R");button(c,w-62,12,50,42,edit?"OK":"EDIT");text(c,"GT7 BRIDGE MOBILE",w/2,32,19,Color.WHITE,true,Paint.Align.CENTER);text(c,tel.status+" - FREE LAYOUT",w/2,51,9,Color.rgb(25,180,255),true,Paint.Align.CENTER);}        
        void gauge(Canvas c,float cx,float cy){p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(1,12,22));c.drawCircle(cx,cy,138,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);int active=Math.round(Math.max(0,Math.min(1,tel.rpm/10000f))*64);for(int i=0;i<65;i++){float q=i/64f;p.setColor(rpmColor(q,i<=active));float a=(float)Math.toRadians(-225+270*q);c.drawLine(cx+(float)Math.cos(a)*112,cy+(float)Math.sin(a)*112,cx+(float)Math.cos(a)*130,cy+(float)Math.sin(a)*130,p);}p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(1,10,18));c.drawCircle(cx,cy,72,p);text(c,""+tel.speed,cx,cy-3,62,Color.WHITE,true,Paint.Align.CENTER);text(c,"KM/H",cx,cy+31,10,Color.rgb(65,213,255),true,Paint.Align.CENTER);round(c,cx-29,cy+46,58,40,14,Color.rgb(0,188,246),Color.TRANSPARENT);text(c,tel.gear,cx,cy+76,24,Color.rgb(0,12,20),true,Paint.Align.CENTER);}        
        int rpmColor(float q,boolean on){int c=q<.45?Color.rgb(25,220,55):q<.66?Color.rgb(235,238,42):q<.84?Color.rgb(255,145,34):Color.rgb(255,35,48);return on?c:Color.argb(45,Color.red(c),Color.green(c),Color.blue(c));}
        void chips(Canvas c){chip(c,15,304,126,"TRACK",tel.track);chip(c,152,304,126,"TIME",new java.text.SimpleDateFormat("HH:mm",Locale.US).format(new Date()));chip(c,289,304,126,"FUEL",tel.fuel>=0?Math.round(tel.fuel)+"%":"--%");}
        void chip(Canvas c,float x,float y,float w,String a,String b){round(c,x,y,w,47,12,Color.rgb(2,18,32),Color.argb(85,72,184,255));text(c,a,x+w/2,y+17,7,Color.rgb(93,163,204),true,Paint.Align.CENTER);text(c,cut(b,13),x+w/2,y+35,12,Color.WHITE,true,Paint.Align.CENTER);}        
        void chart(Canvas c,float x,float y,float w,float h){round(c,x,y,w,h,12,Color.rgb(1,12,22),Color.argb(72,60,170,230));text(c,"GRAFICO DE ACELERACAO",x+13,y+22,11,Color.WHITE,true,Paint.Align.LEFT);float base=y+h/2+12;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(Color.argb(60,70,160,210));c.drawLine(x+20,base,x+w-15,base,p);p.setStyle(Paint.Style.FILL);for(int i=0;i<gl.length;i++){int k=(gi+i)%gl.length;float bx=x+25+i*(w-50)/gl.length;bar(c,bx,base,3,gl[k],Color.rgb(0,150,230));bar(c,bx+3,base,3,gg[k],Color.rgb(255,38,50));}}
        void bar(Canvas c,float x,float b,float ww,float v,int col){float bh=v/2f*25;p.setColor(col);if(bh>=0)c.drawRect(x,b-bh,x+ww,b,p);else c.drawRect(x,b,x+ww,b-bh,p);}        
        String[][] data(){return new String[][]{{"SPD",tel.speed+" km/h","RPM",""+tel.rpm},{"TIME",tel.last,"LAP",""+tel.laps},{"TIME",tel.best,"TOTAL",tel.total},{"FL","--","FR","--"},{"COOLANT",deg(tel.water),"OIL",deg(tel.oil)},{"LEVEL",tel.fuel>=0?one(tel.fuel)+"%":"--","LITERS",tel.fuelL>=0?one(tel.fuelL)+" L":"--"},{"LAT",g(tel.gLat),"LONG",g(tel.gLong)},{"BOOST",boost(tel.turbo),"RPM",""+tel.rpm},{"X",one(tel.x),"Y",one(tel.y)}};}
        void card(Canvas c,int i){float h=closed[i]?58:hs[i];float x=xs[i],y=ys[i],w=ws[i];round(c,x,y,w,h,12,Color.rgb(1,16,29),edit&&i==selected?Color.rgb(0,155,255):Color.argb(88,45,150,220));text(c,closed[i]?"+":"-",x+20,y+28,18,Color.rgb(112,210,255),true,Paint.Align.CENTER);text(c,titles[i],x+46,y+27,15,Color.WHITE,true,Paint.Align.LEFT);text(c,"...",x+w-22,y+24,16,Color.rgb(255,54,76),true,Paint.Align.CENTER);if(!closed[i]){String[][] d=data();text(c,d[i][0],x+16,y+76,10,Color.rgb(119,170,200),true,Paint.Align.LEFT);text(c,d[i][1],x+w-16,y+76,13,Color.WHITE,true,Paint.Align.RIGHT);text(c,d[i][2],x+16,y+99,10,Color.rgb(119,170,200),true,Paint.Align.LEFT);text(c,d[i][3],x+w-16,y+99,13,Color.WHITE,true,Paint.Align.RIGHT);if(edit){p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(0,155,255));c.drawRect(x+w-16,y+h-16,x+w-4,y+h-4,p);}}}
        void bar(Canvas c,float w,float h){round(c,15,h-64,w-30,48,15,Color.rgb(4,18,30),Color.argb(110,72,184,255));text(c,"EDIT: arraste cards; puxe o canto para redimensionar; +/- recolhe",w/2,h-35,9,Color.WHITE,true,Paint.Align.CENTER);}        
        public boolean onTouchEvent(MotionEvent e){float s=getWidth()/430f,x=e.getX()/s,y=e.getY()/s;if(e.getAction()==MotionEvent.ACTION_DOWN){downX=lastX=x;downY=lastY=y;selected=find(x,y+scroll);mode=0;if(edit&&selected>=0){float yy=y+scroll;if(x>xs[selected]+ws[selected]-35&&yy>ys[selected]+(closed[selected]?58:hs[selected])-35)mode=2;else mode=1;}return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=x-lastX,dy=y-lastY;if(edit&&selected>=0&&mode>0){if(mode==1){xs[selected]=clamp(xs[selected]+dx,8,430-ws[selected]-8);ys[selected]=Math.max(395,ys[selected]+dy);}else{ws[selected]=clamp(ws[selected]+dx,180,400);hs[selected]=clamp(hs[selected]+dy,80,220);}invalidate();}else{scroll=Math.max(0,scroll-dy);invalidate();}lastX=x;lastY=y;return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(x>368&&y<62){edit=!edit;save();invalidate();return true;}int id=find(x,y+scroll);if(id>=0&&Math.abs(x-downX)<8&&Math.abs(y-downY)<8){if(x<xs[id]+42){closed[id]=!closed[id];save();invalidate();return true;}if(x>xs[id]+ws[id]-55&&y+scroll<ys[id]+45){pick(id);return true;}}save();return true;}return true;}
        int find(float x,float y){for(int i=8;i>=0;i--){float h=closed[i]?58:hs[i];if(x>=xs[i]&&x<=xs[i]+ws[i]&&y>=ys[i]&&y<=ys[i]+h)return i;}return -1;}
        void pick(int id){String[] ops={"Velocidade/RPM","Ultima volta","Melhor volta","Pneus","Motor","Combustivel","G-Force","Turbo","Mapa"};new AlertDialog.Builder(MainActivity.this).setTitle("Escolher dado do card").setItems(ops,(d,w)->{titles[id]=ops[w].toUpperCase(Locale.US);invalidate();}).setNegativeButton("Fechar",null).show();}
        void save(){SharedPreferences.Editor e=sp.edit();for(int i=0;i<9;i++){e.putBoolean("c"+i,closed[i]);e.putFloat("x"+i,xs[i]);e.putFloat("y"+i,ys[i]);e.putFloat("w"+i,ws[i]);e.putFloat("h"+i,hs[i]);}e.apply();}
        void button(Canvas c,float x,float y,float w,float h,String t){round(c,x,y,w,h,12,Color.rgb(2,10,18),Color.argb(100,72,184,255));text(c,t,x+w/2,y+26,10,Color.WHITE,true,Paint.Align.CENTER);}void round(Canvas c,float x,float y,float w,float h,float rad,int fill,int stroke){r.set(x,y,x+w,y+h);p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawRoundRect(r,rad,rad,p);if(stroke!=Color.TRANSPARENT){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(stroke);c.drawRoundRect(r,rad,rad,p);}}void text(Canvas c,String s,float x,float y,float sz,int col,boolean b,Paint.Align a){p.setStyle(Paint.Style.FILL);p.setColor(col);p.setTextSize(sz);p.setTextAlign(a);p.setTypeface(b?Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD):Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));c.drawText(s==null?"--":s,x,y,p);}float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}String one(float v){return Float.isNaN(v)?"--":String.format(Locale.US,"%.1f",v);}String deg(float v){return Float.isNaN(v)?"--":Math.round(v)+" C";}String g(float v){return Float.isNaN(v)?"--":String.format(Locale.US,"%.2f G",v);}String boost(float v){return Float.isNaN(v)?"--":String.format(Locale.US,"%.2f bar",v);}String cut(String s,int m){return s==null?"--":s.length()<=m?s:s.substring(0,m-1)+".";}
    }
}
