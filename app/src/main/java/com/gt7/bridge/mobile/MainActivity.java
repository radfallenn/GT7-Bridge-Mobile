package com.gt7.bridge.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String VERSION = "1.6.5";
    private static final String PREF = "gt7_bridge_mobile_v165";
    private static final String KEY_BRIDGE_URL = "bridge_url";
    private static final String KEY_SESSIONS = "saved_sessions";
    private static final String DEFAULT_BRIDGE_URL = "http://192.168.1.70:8787";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private DashboardView dashboard;
    private Telemetry telemetry = new Telemetry();
    private String bridgeUrl;
    private boolean polling = false;
    private String lastSummedLap = "";
    private long totalLapMillis = 0L;

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            fetchTelemetry();
            handler.postDelayed(this, 300);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        bridgeUrl = prefs().getString(KEY_BRIDGE_URL, DEFAULT_BRIDGE_URL);
        dashboard = new DashboardView(this);
        setContentView(dashboard);
        startPolling();
    }

    @Override protected void onResume() { super.onResume(); startPolling(); }
    @Override protected void onPause() { super.onPause(); stopPolling(); }

    private SharedPreferences prefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }
    private void startPolling() { if (!polling) { polling = true; handler.post(poller); } }
    private void stopPolling() { polling = false; handler.removeCallbacks(poller); }

    private void fetchTelemetry() {
        new Thread(() -> {
            try {
                String body = get(bridgeUrl + "/api/fields", 900);
                if (body == null || body.trim().isEmpty()) body = get(bridgeUrl + "/api/telemetry", 900);
                if (body == null || body.trim().isEmpty()) throw new Exception("empty");
                Telemetry next = Telemetry.fromJson(body, telemetry);
                next.connected = true;
                next.status = "ONLINE";
                updateLapTotal(next);
                telemetry = next;
                if (dashboard != null) dashboard.pushGraph(next);
            } catch (Exception e) {
                telemetry.connected = false;
                telemetry.status = "OFFLINE";
            }
            runOnUiThread(() -> dashboard.invalidate());
        }).start();
    }

    private void updateLapTotal(Telemetry next) {
        if (next.lastLap != null && !next.lastLap.equals("--") && !next.lastLap.equals(lastSummedLap)) {
            long ms = parseLapMillis(next.lastLap);
            if (ms > 0) {
                totalLapMillis += ms;
                lastSummedLap = next.lastLap;
            }
        }
        if (totalLapMillis > 0) next.totalTime = formatMillis(totalLapMillis);
    }

    private static long parseLapMillis(String s) {
        try {
            String v = s.trim().replace(',', '.');
            String[] parts = v.split(":");
            double sec;
            long min = 0, hour = 0;
            if (parts.length == 3) { hour = Long.parseLong(parts[0]); min = Long.parseLong(parts[1]); sec = Double.parseDouble(parts[2]); }
            else if (parts.length == 2) { min = Long.parseLong(parts[0]); sec = Double.parseDouble(parts[1]); }
            else sec = Double.parseDouble(parts[0]);
            return hour * 3600000L + min * 60000L + Math.round(sec * 1000.0);
        } catch (Exception e) { return 0; }
    }

    private static String formatMillis(long ms) {
        long h = ms / 3600000L; ms %= 3600000L;
        long m = ms / 60000L; ms %= 60000L;
        long s = ms / 1000L; long z = ms % 1000L;
        if (h > 0) return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, s, z);
        return String.format(Locale.US, "%02d:%02d.%03d", m, s, z);
    }

    private String get(String urlText, int timeout) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlText).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(timeout);
        c.setReadTimeout(timeout);
        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close(); c.disconnect(); return sb.toString();
    }

    private void showBridgeDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(bridgeUrl);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Bridge GT7").setMessage("URL do bridge local").setView(input)
                .setPositiveButton("Salvar", (d, w) -> {
                    bridgeUrl = input.getText().toString().trim();
                    if (!bridgeUrl.startsWith("http")) bridgeUrl = "http://" + bridgeUrl;
                    prefs().edit().putString(KEY_BRIDGE_URL, bridgeUrl).apply();
                    Toast.makeText(this, "Bridge salvo", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Cancelar", null).show();
    }

    private void saveSession() {
        try {
            JSONArray old = new JSONArray(prefs().getString(KEY_SESSIONS, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("data", new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(new Date()));
            obj.put("pista", safe(telemetry.track, "Pista não identificada"));
            obj.put("melhorVolta", safe(telemetry.bestLap, "--"));
            obj.put("ultimaVolta", safe(telemetry.lastLap, "--"));
            obj.put("tempoTotal", safe(telemetry.totalTime, "--"));
            obj.put("velocidadeMaxima", telemetry.maxSpeed);
            obj.put("voltas", telemetry.laps);
            JSONArray next = new JSONArray(); next.put(obj);
            for (int i = 0; i < old.length(); i++) next.put(old.get(i));
            prefs().edit().putString(KEY_SESSIONS, next.toString()).apply();
            dashboard.panel = DashboardView.PANEL_SESSIONS;
            dashboard.invalidate();
            Toast.makeText(this, "Sessão salva", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "Erro ao salvar", Toast.LENGTH_SHORT).show(); }
    }

    private static String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }

    static class Telemetry {
        boolean connected = false;
        String status = "OFFLINE";
        int speed = 0, rpm = 0, throttle = 0, brake = 0, laps = 0, maxSpeed = 0, position = 0, totalCars = 0, sector = 0;
        String gear = "N", bestLap = "--", lastLap = "--", currentLap = "--", totalTime = "--", track = "TRACK", carName = "--", carId = "--";
        float fuelPercent = -1, fuelLiters = -1, fuelCapacity = -1;
        float coolant = Float.NaN, oil = Float.NaN, intake = Float.NaN, oilPressure = Float.NaN, turbo = Float.NaN, power = Float.NaN, torque = Float.NaN;
        float tireFL = Float.NaN, tireFR = Float.NaN, tireRL = Float.NaN, tireRR = Float.NaN, tirePFL = Float.NaN, tirePFR = Float.NaN, tirePRL = Float.NaN, tirePRR = Float.NaN;
        float gLat = Float.NaN, gLong = Float.NaN, gVert = Float.NaN, accX = Float.NaN, accY = Float.NaN, accZ = Float.NaN, yaw = Float.NaN, pitch = Float.NaN, roll = Float.NaN;
        float steer = Float.NaN, posX = Float.NaN, posY = Float.NaN, posZ = Float.NaN, heading = Float.NaN, distance = Float.NaN;

        static Telemetry fromJson(String body, Telemetry last) throws Exception {
            JSONObject o;
            String s = body.trim();
            if (s.startsWith("[")) { JSONArray a = new JSONArray(s); o = a.length() > 0 && a.get(0) instanceof JSONObject ? a.getJSONObject(0) : new JSONObject(); }
            else { int a = s.indexOf('{'), b = s.lastIndexOf('}'); o = new JSONObject(a >= 0 && b > a ? s.substring(a, b + 1) : s); }
            if (o.has("fields") && o.get("fields") instanceof JSONObject) o = o.getJSONObject("fields");
            if (o.has("telemetry") && o.get("telemetry") instanceof JSONObject) o = o.getJSONObject("telemetry");
            Telemetry t = new Telemetry();
            t.speed = validInt(o, last.speed, "speed_kmh", "speed", "velocityKmh", "velocidade");
            t.rpm = validInt(o, last.rpm, "rpm", "engine_rpm", "currentRpm");
            t.gear = validString(o, last.gear, "gear", "marcha", "currentGear"); if (t.gear.equals("0")) t.gear = "N";
            t.throttle = validInt(o, last.throttle, "throttle_percent", "throttle", "acelerador");
            t.brake = validInt(o, last.brake, "brake_percent", "brake", "freio");
            t.steer = validFloat(o, last.steer, "steering", "steer", "anguloVolante");
            t.fuelPercent = validFloat(o, last.fuelPercent, "fuel_percent", "fuelPercent", "combustivelPorcentagem", "fuel_percentage");
            t.fuelLiters = validFloat(o, last.fuelLiters, "fuel", "fuelLiters", "fuel_liters", "combustivel");
            t.fuelCapacity = validFloat(o, last.fuelCapacity, "fuelCapacity", "fuel_capacity", "tank_capacity");
            t.bestLap = validString(o, last.bestLap, "best_lap_text", "bestLap", "melhorVolta", "best_lap");
            t.lastLap = validString(o, last.lastLap, "last_lap_text", "lastLap", "ultimaVolta", "last_lap");
            t.currentLap = validString(o, last.currentLap, "current_lap_text", "voltaAtual", "currentLap", "lapCurrent");
            t.totalTime = validString(o, last.totalTime, "total_race_time_text", "tempoTotal", "totalRaceTime");
            t.laps = validInt(o, last.laps, "voltasCorrigidas", "correctedLaps", "completed_laps", "lap", "currentLap");
            t.maxSpeed = Math.max(Math.max(last.maxSpeed, t.speed), validInt(o, last.maxSpeed, "velocidadeMaxima", "maxSpeed"));
            t.sector = validInt(o, last.sector, "sector", "setor", "currentSector");
            t.position = validInt(o, last.position, "racePosition", "positionInRace", "posicaoCorrida");
            t.totalCars = validInt(o, last.totalCars, "totalCars", "cars", "numCars");
            t.track = validString(o, last.track, "trackName", "pista", "track", "circuit");
            t.carName = validString(o, last.carName, "carName", "car", "modelo", "nomeCarro");
            t.carId = validString(o, last.carId, "carId", "codigoCarro", "car_code");
            t.coolant = validFloat(o, last.coolant, "coolantTemp", "water_temp", "engine_water_temp", "temperaturaAgua");
            t.oil = validFloat(o, last.oil, "oilTemp", "oil_temp", "engine_oil_temp");
            t.intake = validFloat(o, last.intake, "intakeTemp", "intake_temp", "temperaturaAdmissao");
            t.oilPressure = validFloat(o, last.oilPressure, "oilPressure", "oil_pressure");
            t.turbo = validFloat(o, last.turbo, "turbo", "boostPressure", "turbo_pressure");
            t.power = validFloat(o, last.power, "power", "horsepower", "hp", "potencia");
            t.torque = validFloat(o, last.torque, "torque");
            t.tireFL = validFloat(o, last.tireFL, "tireTempFL", "tyreTempFL", "tire_fl"); t.tireFR = validFloat(o, last.tireFR, "tireTempFR", "tyreTempFR", "tire_fr"); t.tireRL = validFloat(o, last.tireRL, "tireTempRL", "tyreTempRL", "tire_rl"); t.tireRR = validFloat(o, last.tireRR, "tireTempRR", "tyreTempRR", "tire_rr");
            t.tirePFL = validFloat(o, last.tirePFL, "tirePressureFL", "tyrePressureFL"); t.tirePFR = validFloat(o, last.tirePFR, "tirePressureFR", "tyrePressureFR"); t.tirePRL = validFloat(o, last.tirePRL, "tirePressureRL", "tyrePressureRL"); t.tirePRR = validFloat(o, last.tirePRR, "tirePressureRR", "tyrePressureRR");
            t.gLat = validFloat(o, last.gLat, "gForceLat", "g_lat", "lateralG"); t.gLong = validFloat(o, last.gLong, "gForceLong", "g_long", "longitudinalG"); t.gVert = validFloat(o, last.gVert, "gForceVert", "g_vertical");
            t.accX = validFloat(o, last.accX, "accelerationX", "accX"); t.accY = validFloat(o, last.accY, "accelerationY", "accY"); t.accZ = validFloat(o, last.accZ, "accelerationZ", "accZ");
            t.yaw = validFloat(o, last.yaw, "yaw"); t.pitch = validFloat(o, last.pitch, "pitch"); t.roll = validFloat(o, last.roll, "roll");
            if (o.has("position") && o.get("position") instanceof JSONObject) { JSONObject p = o.getJSONObject("position"); t.posX = validFloat(p, last.posX, "x"); t.posY = validFloat(p, last.posY, "y"); t.posZ = validFloat(p, last.posZ, "z"); }
            else { t.posX = validFloat(o, last.posX, "position.x", "x"); t.posY = validFloat(o, last.posY, "position.y", "y"); t.posZ = validFloat(o, last.posZ, "position.z", "z"); }
            t.heading = validFloat(o, last.heading, "heading", "direction"); t.distance = validFloat(o, last.distance, "distance", "distanceTraveled", "trackDistance");
            return t;
        }

        private static int validInt(JSONObject o, int fallback, String... keys) { float f = validFloat(o, fallback, keys); return Float.isNaN(f) ? fallback : Math.round(f); }
        private static float validFloat(JSONObject o, float fallback, String... keys) {
            for (String k : keys) try { if (!o.has(k) || o.isNull(k)) continue; String v = String.valueOf(o.get(k)).replace(",", "."); if (v.trim().isEmpty() || v.equals("--") || v.equalsIgnoreCase("nan")) continue; float f = Float.parseFloat(v); if (!Float.isNaN(f) && !Float.isInfinite(f)) return f; } catch (Exception ignored) {}
            return fallback;
        }
        private static String validString(JSONObject o, String fallback, String... keys) {
            for (String k : keys) try { if (!o.has(k) || o.isNull(k)) continue; String v = String.valueOf(o.get(k)); if (!v.trim().isEmpty() && !v.equals("--") && !v.equalsIgnoreCase("nan")) return v; } catch (Exception ignored) {}
            return fallback;
        }
    }

    class DashboardView extends View {
        static final int PANEL_DASH = 0, PANEL_SELECT = 1, PANEL_SESSIONS = 2, PANEL_EXPANDED = 3;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final ArrayList<Card> cards = new ArrayList<>();
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        final float[] histLat = new float[60], histLong = new float[60], histVert = new float[60];
        int histIndex = 0;
        int panel = PANEL_DASH;
        int selectedCard = 0;
        RectF leftTop = new RectF(), rightTop = new RectF(), bottom1 = new RectF(), bottom2 = new RectF(), bottom3 = new RectF(), bottom4 = new RectF();

        DashboardView(Context c) { super(c); setFocusable(true); setBackgroundColor(Color.BLACK); stroke.setStyle(Paint.Style.STROKE); }

        void pushGraph(Telemetry t) {
            histLat[histIndex] = Float.isNaN(t.gLat) ? 0f : Math.max(-2f, Math.min(2f, t.gLat));
            histLong[histIndex] = Float.isNaN(t.gLong) ? 0f : Math.max(-2f, Math.min(2f, t.gLong));
            histVert[histIndex] = Float.isNaN(t.gVert) ? 0f : Math.max(-2f, Math.min(2f, t.gVert));
            histIndex = (histIndex + 1) % histLat.length;
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float scale = getWidth() / 620f;
            c.save(); c.scale(scale, scale); draw(c, 620, getHeight() / scale); c.restore();
        }

        private void draw(Canvas c, float w, float h) {
            bg(c, w, h); top(c, w); gauge(c, 196, 207, 145); accelChart(c, 365, 96, 240, 205); meta(c); cards(c, h); bottom(c, w, h);
            if (panel == PANEL_SELECT) selectPanel(c, w, h);
            if (panel == PANEL_SESSIONS) sessions(c, w, h);
            if (panel == PANEL_EXPANDED) expanded(c, w, h, selectedCard);
        }

        private void bg(Canvas c, float w, float h) {
            p.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(0, 5, 11), Color.BLACK, Shader.TileMode.CLAMP)); p.setStyle(Paint.Style.FILL); c.drawRect(0,0,w,h,p); p.setShader(null);
        }

        private void top(Canvas c, float w) {
            leftTop.set(10, 10, 50, 50); rightTop.set(w-50, 10, w-10, 50);
            iconButton(c, leftTop, "☰"); iconButton(c, rightTop, "▦");
            text(c, "GT7 BRIDGE MOBILE", w/2, 30, 24, Color.WHITE, true, Paint.Align.CENTER);
            text(c, telemetry.status + "  •  8/8 CARDS  •  TELEMETRIA ATIVA", w/2, 48, 11, Color.rgb(22,177,255), true, Paint.Align.CENTER);
        }

        private void iconButton(Canvas c, RectF rect, String label) { round(c, rect, 9, Color.rgb(2,10,18), Color.argb(100,72,184,255), 1); text(c,label,rect.centerX(),rect.centerY()+6,18,Color.WHITE,true,Paint.Align.CENTER); }

        private void gauge(Canvas c, float cx, float cy, float radius) {
            p.setColor(Color.rgb(1,12,22)); p.setStyle(Paint.Style.FILL); c.drawCircle(cx,cy,radius,p);
            stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(1.2f); stroke.setColor(Color.argb(110,22,118,185)); c.drawCircle(cx,cy,radius,stroke);
            float start=-225, sweep=270; int marks=65; int active=Math.round(Math.max(0,Math.min(1,telemetry.rpm/10000f))*(marks-1));
            for(int i=0;i<marks;i++){ float pct=i/(float)(marks-1); float a=(float)Math.toRadians(start+sweep*pct); stroke.setColor(rpmColor(pct,i<=active)); stroke.setStrokeWidth(i<=active?5.2f:3.3f); stroke.setStrokeCap(Paint.Cap.ROUND); float in=radius-21,out=radius-6; c.drawLine(cx+(float)Math.cos(a)*in,cy+(float)Math.sin(a)*in,cx+(float)Math.cos(a)*out,cy+(float)Math.sin(a)*out,stroke); }
            for(int i=0;i<=10;i++){ float a=(float)Math.toRadians(start+sweep*i/10f); text(c,""+i,cx+(float)Math.cos(a)*92,cy+5+(float)Math.sin(a)*92,12,Color.argb(210,220,236,245),true,Paint.Align.CENTER); }
            p.setColor(Color.rgb(1,10,18)); c.drawCircle(cx,cy,75,p); stroke.setColor(Color.argb(70,47,172,240)); stroke.setStrokeWidth(1.2f); c.drawCircle(cx,cy,75,stroke);
            text(c,""+telemetry.speed,cx,cy-2,62,Color.WHITE,true,Paint.Align.CENTER); text(c,"KM/H",cx,cy+31,12,Color.rgb(65,213,255),true,Paint.Align.CENTER);
            r.set(cx-31,cy+47,cx+31,cy+88); round(c,r,14,Color.rgb(0,188,246),Color.TRANSPARENT,0); text(c,telemetry.gear,cx,cy+77,26,Color.rgb(0,12,20),true,Paint.Align.CENTER);
        }
        private int rpmColor(float pct, boolean lit){ int c; if(pct<.45f)c=Color.rgb(25,220,55); else if(pct<.66f)c=Color.rgb(232,238,42); else if(pct<.84f)c=Color.rgb(255,145,34); else c=Color.rgb(255,35,48); return lit?c:Color.argb(45,Color.red(c),Color.green(c),Color.blue(c)); }

        private void accelChart(Canvas c, float x, float y, float w, float h) {
            RectF box = new RectF(x, y, x+w, y+h);
            round(c, box, 14, Color.rgb(1,12,22), Color.argb(82,60,170,230), 1);
            text(c, "ACELERAÇÃO (G)", x+16, y+28, 13, Color.WHITE, true, Paint.Align.LEFT);
            text(c, "■ LAT   ■ LONG   ■ VERT", x+w-16, y+27, 8, Color.rgb(180,205,218), false, Paint.Align.RIGHT);
            RectF plot = new RectF(x+30, y+55, x+w-18, y+h-34);
            stroke.setStrokeWidth(1); stroke.setColor(Color.argb(55,70,160,210));
            c.drawLine(plot.left, plot.centerY(), plot.right, plot.centerY(), stroke);
            c.drawLine(plot.left, plot.top, plot.right, plot.top, stroke);
            c.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, stroke);
            text(c,"2.0",plot.left-8,plot.top+4,8,Color.WHITE,false,Paint.Align.RIGHT); text(c,"0",plot.left-8,plot.centerY()+4,8,Color.WHITE,false,Paint.Align.RIGHT); text(c,"-2.0",plot.left-8,plot.bottom+4,8,Color.WHITE,false,Paint.Align.RIGHT);
            int n=histLat.length; float bw=plot.width()/n*0.62f;
            for(int i=0;i<n;i++){ int idx=(histIndex+i)%n; float x0=plot.left+i*plot.width()/n; drawBar(c,x0,plot.centerY(),bw,histLat[idx],Color.rgb(0,150,230)); drawBar(c,x0+bw*.33f,plot.centerY(),bw,histLong[idx],Color.rgb(255,38,50)); }
            text(c,"-30s",plot.left,plot.bottom+18,8,Color.WHITE,false,Paint.Align.LEFT); text(c,"-20s",plot.left+plot.width()/3,plot.bottom+18,8,Color.WHITE,false,Paint.Align.CENTER); text(c,"-10s",plot.left+plot.width()*2/3,plot.bottom+18,8,Color.WHITE,false,Paint.Align.CENTER); text(c,"0s",plot.right,plot.bottom+18,8,Color.WHITE,false,Paint.Align.RIGHT);
        }
        private void drawBar(Canvas c, float x, float center, float bw, float v, int color){ float max=56; float bh=(v/2f)*max; p.setColor(color); p.setStyle(Paint.Style.FILL); if(bh>=0)c.drawRoundRect(new RectF(x,center-bh,x+bw,center),2,2,p); else c.drawRoundRect(new RectF(x,center,x+bw,center-bh),2,2,p); }

        private void meta(Canvas c){ float x=10,y=330,g=8,cw=196; chip(c,x,y,cw,"TRACK",telemetry.track); chip(c,x+cw+g,y,cw,"TIME",timeFormat.format(new Date())); chip(c,x+(cw+g)*2,y,cw,"FUEL",telemetry.fuelPercent>=0?Math.round(telemetry.fuelPercent)+"%":"--"); }
        private void chip(Canvas c,float x,float y,float w,String label,String value){ r.set(x,y,x+w,y+54); round(c,r,12,Color.rgb(2,18,32),Color.argb(85,72,184,255),1); text(c,label,x+w/2,y+20,9,Color.rgb(93,163,204),true,Paint.Align.CENTER); text(c,ellipsize(value,14),x+w/2,y+40,16,Color.WHITE,true,Paint.Align.CENTER); }

        private void cards(Canvas c,float h){ cards.clear(); String[][] d=cardData(); float x0=10,y0=395,g=8,cw=196,ch=156; for(int i=0;i<9;i++){ float x=x0+(i%3)*(cw+g); float y=y0+(i/3)*(ch+g); RectF rect=new RectF(x,y,x+cw,y+ch); cards.add(new Card(rect,i)); card(c,rect,i,d[i]); } }
        private String[][] cardData(){ return new String[][]{
                {"TELEMETRY","Live Data","VIEW DATA","SPD",telemetry.speed+" km/h","RPM",""+telemetry.rpm},
                {"LAST LAP","Última Volta","VIEW DATA","TIME",telemetry.lastLap,"LAP",telemetry.laps+" / 15"},
                {"BEST LAP","Melhor Volta","VIEW DATA","TIME",telemetry.bestLap,"LAP",telemetry.bestLap.equals("--")?"--":"8"},
                {"TIRE STATUS","All Good","VIEW DATA","FL",tire(telemetry.tireFL,telemetry.tirePFL),"FR",tire(telemetry.tireFR,telemetry.tirePFR),"RL",tire(telemetry.tireRL,telemetry.tirePRL),"RR",tire(telemetry.tireRR,telemetry.tirePRR)},
                {"ENGINE TEMP","Normal","VIEW DATA","COOLANT",degC(telemetry.coolant),"OIL",degC(telemetry.oil),"INTAKE",degC(telemetry.intake)},
                {"FUEL LEVEL","combustível","VIEW DATA","LEVEL",telemetry.fuelPercent>=0?one(telemetry.fuelPercent)+"%":"--","LITERS",telemetry.fuelLiters>=0?one(telemetry.fuelLiters)+" L":"--","CAPACITY",telemetry.fuelCapacity>=0?one(telemetry.fuelCapacity)+" L":"--"},
                {"G-FORCE","lateral/longitudinal","VIEW DATA","LAT",g(telemetry.gLat),"LONG",g(telemetry.gLong),"VERT",g(telemetry.gVert)},
                {"BOOST PRESSURE","turbo","VIEW DATA","BOOST",boost(telemetry.turbo),"TURBO",telemetry.turbo>=0?one(telemetry.turbo*100f)+"%":"--"},
                {"TRACK MAP","pista","VIEW DATA","POSITION","X " + two(telemetry.posX),"","Y " + two(telemetry.posY),"","Z " + two(telemetry.posZ)}
        }; }
        private void card(Canvas c,RectF rect,int idx,String[] d){ round(c,rect,10,Color.rgb(1,16,29),Color.argb(88,45,150,220),1); text(c,"•••",rect.right-17,rect.top+24,16,Color.rgb(255,54,76),true,Paint.Align.CENTER); text(c,d[0],rect.left+14,rect.top+27,15,Color.WHITE,true,Paint.Align.LEFT); text(c,d[1],rect.left+14,rect.top+45,10,Color.rgb(132,177,204),false,Paint.Align.LEFT); float y=rect.top+76; for(int i=3;i<d.length-1&&y<rect.bottom-34;i+=2){ if(d[i].length()==0)continue; text(c,d[i],rect.left+14,y,10,Color.rgb(119,170,200),true,Paint.Align.LEFT); text(c,ellipsize(d[i+1],13),rect.right-14,y,13,Color.WHITE,true,Paint.Align.RIGHT); stroke.setColor(Color.argb(25,75,154,212)); stroke.setStrokeWidth(1); c.drawLine(rect.left+14,y+8,rect.right-14,y+8,stroke); y+=19; } RectF btn=new RectF(rect.left+11,rect.bottom-29,rect.right-11,rect.bottom-8); round(c,btn,7,Color.rgb(3,54,92),Color.argb(75,72,184,255),1); text(c,d[2],btn.centerX(),btn.centerY()+5,10,Color.rgb(165,222,255),true,Paint.Align.CENTER); }

        private void bottom(Canvas c,float w,float h){ float y=Math.max(898,h-58),g=6,bw=(w-30-g*3)/4f; bottom1.set(10,y,10+bw,y+44); bottom2.set(bottom1.right+g,y,bottom1.right+g+bw,y+44); bottom3.set(bottom2.right+g,y,bottom2.right+g+bw,y+44); bottom4.set(bottom3.right+g,y,bottom3.right+g+bw,y+44); bottomBtn(c,bottom1,"DASHBOARD",panel==PANEL_DASH); bottomBtn(c,bottom2,"SESSIONS",panel==PANEL_SESSIONS); bottomBtn(c,bottom3,"MY TRACKS",false); bottomBtn(c,bottom4,"SETTINGS",panel==PANEL_SELECT); }
        private void bottomBtn(Canvas c,RectF rect,String label,boolean active){ round(c,rect,7,active?Color.rgb(3,38,68):Color.rgb(3,12,20),active?Color.rgb(0,155,255):Color.argb(70,92,150,185),1); text(c,label,rect.centerX(),rect.top+29,10,active?Color.rgb(72,203,255):Color.rgb(219,234,241),true,Paint.Align.CENTER); }

        private void selectPanel(Canvas c,float w,float h){ RectF box=new RectF(86,72,w-86,h-76); round(c,box,14,Color.rgb(1,8,15),Color.argb(140,65,175,235),1); text(c,"ESCOLHER DADO DO CARD",box.left+15,box.top+28,14,Color.WHITE,true,Paint.Align.LEFT); text(c,"×",box.right-18,box.top+30,20,Color.WHITE,true,Paint.Align.CENTER); float y=box.top+62; String[][] all=availableData(); for(String[] item:all){ if(item[0].startsWith("#")){ y+=10; text(c,item[0].substring(1),box.left+15,y,11,Color.rgb(0,205,255),true,Paint.Align.LEFT); y+=22; continue; } if(y>box.bottom-28) break; text(c,item[0],box.left+15,y,10,Color.rgb(230,242,248),false,Paint.Align.LEFT); text(c,item[1],box.left+190,y,9,Color.rgb(145,167,182),false,Paint.Align.LEFT); text(c,item[2],box.right-40,y,9,Color.rgb(145,167,182),false,Paint.Align.RIGHT); text(c,"✓",box.right-16,y+2,12,Color.rgb(63,220,255),true,Paint.Align.RIGHT); stroke.setColor(Color.argb(28,75,154,212)); c.drawLine(box.left+15,y+7,box.right-15,y+7,stroke); y+=19; } }
        private String[][] availableData(){ return new String[][]{{"Velocidade","SPD","km/h"},{"RPM","RPM","rpm"},{"Marcha Atual","GEAR","-"},{"Acelerador","THR","%"},{"Freio","BRK","%"},{"Ângulo do Volante","STEER","°"},{"Tempo Total","TOTAL_TIME","s"},{"Última Volta","LAST_LAP","s"},{"Melhor Volta","BEST_LAP","s"},{"Voltas Completadas","LAPS","-"},{"Setor Atual","SECTOR","-"},{"Posição na Corrida","POSITION","-"},{"#MOTOR","",""},{"Temperatura da Água","COOLANT_TEMP","°C"},{"Temperatura do Óleo","OIL_TEMP","°C"},{"Temperatura da Admissão","INTAKE_TEMP","°C"},{"Pressão do Óleo","OIL_PRESSURE","bar"},{"Pressão do Turbo","BOOST_PRESSURE","bar"},{"Potência","POWER","hp"},{"Torque","TORQUE","Nm"},{"#COMBUSTÍVEL / PNEUS","",""},{"Combustível","FUEL","L"},{"Capacidade do Tanque","FUEL_CAPACITY","L"},{"Temperatura FL","TIRE_TEMP_FL","°C"},{"Pressão FL","TIRE_PRESS_FL","bar"},{"#DINÂMICA / MAPA","",""},{"G Lateral","G_LAT","G"},{"G Longitudinal","G_LONG","G"},{"G Vertical","G_VERT","G"},{"Yaw","YAW","°/s"},{"Pitch","PITCH","°/s"},{"Roll","ROLL","°/s"},{"Posição X","POS_X","m"},{"Posição Y","POS_Y","m"},{"Posição Z","POS_Z","m"}}; }

        private void sessions(Canvas c,float w,float h){ RectF box=new RectF(86,72,w-86,h-76); round(c,box,14,Color.rgb(1,8,15),Color.argb(140,65,175,235),1); text(c,"MINHAS PISTAS",box.left+15,box.top+28,14,Color.WHITE,true,Paint.Align.LEFT); try{ JSONArray arr=new JSONArray(prefs().getString(KEY_SESSIONS,"[]")); float y=box.top+65; if(arr.length()==0) text(c,"Nenhuma sessão salva ainda.",box.left+15,y,12,Color.rgb(200,222,238),false,Paint.Align.LEFT); for(int i=0;i<Math.min(9,arr.length());i++){ JSONObject o=arr.getJSONObject(i); RectF item=new RectF(box.left+12,y,box.right-12,y+50); round(c,item,10,Color.rgb(4,24,42),Color.argb(65,72,184,255),1); text(c,ellipsize(o.optString("pista","Pista não identificada"),28),item.left+10,item.top+19,12,Color.WHITE,true,Paint.Align.LEFT); text(c,"Melhor "+o.optString("melhorVolta","--")+" · Última "+o.optString("ultimaVolta","--"),item.left+10,item.top+38,9,Color.rgb(170,210,232),false,Paint.Align.LEFT); y+=58; }}catch(Exception ignored){} }
        private void expanded(Canvas c,float w,float h,int idx){ String[][] d=cardData(); RectF box=new RectF(86,72,w-86,h-76); round(c,box,14,Color.rgb(1,8,15),Color.argb(140,65,175,235),1.3f); text(c,d[idx][0],box.left+15,box.top+32,20,Color.WHITE,true,Paint.Align.LEFT); text(c,"FECHAR",box.right-18,box.top+32,11,Color.rgb(132,194,230),true,Paint.Align.RIGHT); float y=box.top+82; for(int i=3;i<d[idx].length-1;i+=2){ if(d[idx][i].length()==0)continue; text(c,d[idx][i],box.left+18,y,15,Color.rgb(115,186,230),true,Paint.Align.LEFT); text(c,d[idx][i+1],box.right-18,y,18,Color.WHITE,true,Paint.Align.RIGHT); stroke.setColor(Color.argb(45,72,184,255)); c.drawLine(box.left+18,y+10,box.right-18,y+10,stroke); y+=43; } text(c,"TEMPO TOTAL = soma de todas as voltas registradas",box.left+18,box.bottom-38,10,Color.rgb(160,205,230),false,Paint.Align.LEFT); }

        @Override public boolean onTouchEvent(MotionEvent e){ if(e.getAction()!=MotionEvent.ACTION_UP)return true; float s=getWidth()/620f,x=e.getX()/s,y=e.getY()/s; if(panel==PANEL_EXPANDED){panel=PANEL_DASH;invalidate();return true;} if(leftTop.contains(x,y)){panel=panel==PANEL_SESSIONS?PANEL_DASH:PANEL_SESSIONS;invalidate();return true;} if(rightTop.contains(x,y)){panel=panel==PANEL_SELECT?PANEL_DASH:PANEL_SELECT;invalidate();return true;} if(bottom1.contains(x,y)){panel=PANEL_DASH;invalidate();return true;} if(bottom2.contains(x,y)){panel=PANEL_SESSIONS;invalidate();return true;} if(bottom3.contains(x,y)){saveSession();return true;} if(bottom4.contains(x,y)){panel=PANEL_SELECT;invalidate();return true;} if(panel==PANEL_SELECT||panel==PANEL_SESSIONS){panel=PANEL_DASH;invalidate();return true;} for(Card card:cards){ RectF dots=new RectF(card.rect.right-42,card.rect.top+2,card.rect.right,card.rect.top+42); RectF btn=new RectF(card.rect.left+8,card.rect.bottom-34,card.rect.right-8,card.rect.bottom); if(dots.contains(x,y)){selectedCard=card.index;panel=PANEL_SELECT;invalidate();return true;} if(btn.contains(x,y)){selectedCard=card.index;panel=PANEL_EXPANDED;invalidate();return true;} } if(y<60&&x>170&&x<450)showBridgeDialog(); return true; }

        private void round(Canvas c,RectF rect,float rad,int fill,int strokeColor,float strokeWidth){ p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawRoundRect(rect,rad,rad,p); if(strokeColor!=Color.TRANSPARENT&&strokeWidth>0){stroke.setColor(strokeColor);stroke.setStrokeWidth(strokeWidth);stroke.setStyle(Paint.Style.STROKE);c.drawRoundRect(rect,rad,rad,stroke);} }
        private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,Paint.Align align){ p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTextAlign(align);p.setTypeface(bold?Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD):Typeface.create(Typeface.SANS_SERIF,Typeface.NORMAL));c.drawText(s==null?"--":s,x,y,p); }
        private String tire(float temp,float press){ return (Float.isNaN(temp)?"--":Math.round(temp)+"°C")+(Float.isNaN(press)?"":" / "+one(press)+" bar"); }
        private String degC(float v){return Float.isNaN(v)?"--":Math.round(v)+"°C";} private String one(float v){return Float.isNaN(v)?"--":String.format(Locale.US,"%.1f",v);} private String two(float v){return Float.isNaN(v)?"--":String.format(Locale.US,"%.1f m",v);} private String g(float v){return Float.isNaN(v)?"--":String.format(Locale.US,"%.2f G",v);} private String boost(float v){return Float.isNaN(v)?"--":String.format(Locale.US,"%.2f bar",v);} private String ellipsize(String s,int max){return s==null?"--":s.length()<=max?s:s.substring(0,Math.max(0,max-1))+"…";}
    }

    static class Card { RectF rect; int index; Card(RectF r,int i){rect=r;index=i;} }
}
