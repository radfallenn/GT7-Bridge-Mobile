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
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String VERSION = "1.6.1";
    private static final String BRIDGE_URL = "http://192.168.1.70:8787";
    private static final String DEFAULT_PS5_IP = "192.168.1.54";
    private static final String PREF = "gt7_bridge_mobile";
    private static final String KEY_PS5_IP = "ps5_ip";
    private static final String KEY_SESSIONS = "gt7_saved_sessions";

    private static final int BG = Color.parseColor("#02070D");
    private static final int PANEL = Color.parseColor("#06111D");
    private static final int PANEL_2 = Color.parseColor("#071827");
    private static final int STROKE = Color.parseColor("#1B4D78");
    private static final int TXT = Color.parseColor("#F6FAFF");
    private static final int MUTED = Color.parseColor("#9CADBE");
    private static final int BLUE = Color.parseColor("#159BFF");
    private static final int CYAN = Color.parseColor("#16E6FF");
    private static final int GREEN = Color.parseColor("#37F06B");
    private static final int RED = Color.parseColor("#FF3358");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Telemetry t = new Telemetry();
    private Health h = new Health();

    private TextView bridgeTxt, ps5Txt, fieldsTxt, statusTxt;
    private SpeedGaugeView speedGauge;
    private AccelChartView accelChart;
    private TextView gearTxt, fuelTxt, totalTxt, autonomyTxt, speedTxt, lastLapTxt, lapsTxt, bestLapTxt;
    private TextView card9Txt, card10Txt;

    private long lastLapChange = 0, lastActive = 0;
    private int lastLaps = 0;
    private boolean sessionActive = false, sessionSaved = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            fetchFields();
            fetchHealth(false);
            handler.postDelayed(this, 800);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        buildUi();
        handler.post(tick);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vBox();
        root.setPadding(dp(10), dp(8), dp(10), dp(16));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root.addView(topStatus());
        root.addView(mainPanelOption4());
        root.addView(bottomButtons());
        setContentView(screen);
    }

    private View topStatus() {
        LinearLayout row = hRow();
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(round(PANEL, 16, STROKE, 1));
        row.setLayoutParams(lp(-1, dp(82), 0, 0, 0, dp(8)));
        bridgeTxt = block(row, "BRIDGE", "192.168.1.70:8787");
        divider(row);
        ps5Txt = block(row, "PS5", getPs5Ip());
        divider(row);
        fieldsTxt = block(row, "", "campos  ›");
        row.setOnClickListener(v -> fetchDialog("Campos", BRIDGE_URL + "/api/fields"));
        return row;
    }

    private TextView block(LinearLayout row, String label, String value) {
        LinearLayout b = vBox();
        b.setGravity(Gravity.CENTER_VERTICAL);
        if (label.length() > 0) b.addView(text(label, 12, TXT, true));
        TextView val = text(value, 15, CYAN, false);
        b.addView(val, lp(-1, -2, 0, label.length() > 0 ? dp(6) : 0, 0, 0));
        row.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
        return val;
    }

    private void divider(LinearLayout row) {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#24425E"));
        row.addView(d, lp(dp(1), -1, dp(12), 0, dp(12), 0));
    }

    private View mainPanelOption4() {
        LinearLayout panel = vBox();
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(round(PANEL, 16, STROKE, 1));

        LinearLayout first = hRow();
        first.setGravity(Gravity.TOP);
        panel.addView(first, lp(-1, dp(410), 0, 0, 0, dp(8)));

        FrameLayout gaugeWrap = new FrameLayout(this);
        speedGauge = new SpeedGaugeView(this);
        gaugeWrap.addView(speedGauge, new FrameLayout.LayoutParams(-1, -1));
        first.addView(gaugeWrap, new LinearLayout.LayoutParams(0, -1, 1.35f));

        LinearLayout right = vBox();
        first.addView(right, lp(0, -1, dp(8), 0, 0, 0, 1f));
        gearTxt = iconCard(right, "⚙", "--", "---", true);
        fuelTxt = iconCard(right, "⛽", "-- L", "---", true);
        totalTxt = iconCard(right, "⏱", "--", "SOMA VOLTAS", true);

        LinearLayout row2 = hRow();
        panel.addView(row2, lp(-1, dp(90), 0, 0, 0, dp(8)));
        autonomyTxt = iconMini(row2, "⛽", "--", "VOLTAS");
        speedTxt = iconMini(row2, "◔", "0 km/h", "---");
        lastLapTxt = iconMini(row2, "⏱", "--", "---");

        LinearLayout row3 = hRow();
        panel.addView(row3, lp(-1, dp(112), 0, 0, 0, 0));
        lapsTxt = iconMini(row3, "⏱", "0", "---");
        LinearLayout chartCard = card();
        chartCard.setPadding(dp(10), dp(8), dp(10), dp(8));
        accelChart = new AccelChartView(this);
        chartCard.addView(accelChart, new LinearLayout.LayoutParams(-1, -1));
        row3.addView(chartCard, lp(0, -1, dp(8), 0, 0, 0, 2f));

        LinearLayout row4 = hRow();
        panel.addView(row4, lp(-1, dp(82), 0, dp(8), 0, 0));
        bestLapTxt = iconMini(row4, "★", "--", "---");
        card9Txt = iconMini(row4, "◉", "--", "---");
        card10Txt = iconMini(row4, "▣", "--", "---");
        return panel;
    }

    private TextView iconCard(LinearLayout parent, String icon, String value, String sub, boolean tall) {
        LinearLayout c = card();
        c.setPadding(dp(12), dp(10), dp(12), dp(8));
        LinearLayout row = hRow();
        TextView ic = text(icon, 27, TXT, true);
        ic.setGravity(Gravity.CENTER);
        row.addView(ic, new LinearLayout.LayoutParams(dp(48), -1));
        LinearLayout values = vBox();
        values.setGravity(Gravity.CENTER);
        TextView v = text(value, 30, TXT, true);
        v.setGravity(Gravity.CENTER);
        values.addView(v);
        TextView s = text(sub, 14, MUTED, true);
        s.setGravity(Gravity.CENTER);
        values.addView(s);
        row.addView(values, new LinearLayout.LayoutParams(0, -1, 1));
        c.addView(row, new LinearLayout.LayoutParams(-1, -1));
        parent.addView(c, lp(-1, 0, 0, 0, 0, dp(8), 1f));
        return v;
    }

    private TextView iconMini(LinearLayout parent, String icon, String value, String sub) {
        LinearLayout c = card();
        c.setPadding(dp(10), dp(8), dp(10), dp(6));
        LinearLayout row = hRow();
        TextView ic = text(icon, 24, TXT, true);
        ic.setGravity(Gravity.CENTER);
        row.addView(ic, new LinearLayout.LayoutParams(dp(44), -1));
        LinearLayout values = vBox();
        values.setGravity(Gravity.CENTER);
        TextView v = text(value, 24, TXT, true);
        v.setGravity(Gravity.CENTER);
        values.addView(v);
        TextView s = text(sub, 12, MUTED, true);
        s.setGravity(Gravity.CENTER);
        values.addView(s);
        row.addView(values, new LinearLayout.LayoutParams(0, -1, 1));
        c.addView(row, new LinearLayout.LayoutParams(-1, -1));
        parent.addView(c, lp(0, -1, 0, 0, dp(8), 0, 1f));
        return v;
    }

    private View bottomButtons() {
        LinearLayout row = hRow();
        row.setGravity(Gravity.CENTER);
        row.setLayoutParams(lp(-1, dp(56), 0, dp(10), 0, 0));
        addButton(row, "SALVAR", v -> { saveSession("manual"); Toast.makeText(this, "Sessão salva", Toast.LENGTH_SHORT).show(); });
        addButton(row, "HISTÓRICO", v -> showHistory());
        addButton(row, "DEBUG", v -> fetchDialog("Debug", BRIDGE_URL + "/api/debug"));
        addButton(row, "CANDIDATOS", v -> fetchDialog("Candidates", BRIDGE_URL + "/api/candidates"));
        addButton(row, "PS5", v -> editPs5Ip());
        return row;
    }

    private void addButton(LinearLayout row, String label, View.OnClickListener l) {
        TextView b = text(label, 11, TXT, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(round(PANEL_2, 10, STROKE, 1));
        b.setOnClickListener(l);
        row.addView(b, lp(0, dp(48), dp(3), 0, dp(3), 0, 1f));
    }

    private LinearLayout card() {
        LinearLayout c = vBox();
        c.setBackground(round(Color.parseColor("#061421"), 15, STROKE, 1));
        return c;
    }

    private void editPs5Ip() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setText(getPs5Ip());
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Editar IP do PS5")
                .setMessage("Bridge: " + BRIDGE_URL)
                .setView(input)
                .setPositiveButton("Salvar", (d, w) -> {
                    String ip = input.getText().toString().trim();
                    if (ip.length() == 0) ip = DEFAULT_PS5_IP;
                    getPrefs().edit().putString(KEY_PS5_IP, ip).apply();
                    ps5Txt.setText(ip);
                    Toast.makeText(this, "IP salvo", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void fetchFields() { fetchJson(BRIDGE_URL + "/api/fields", obj -> { t.fromJson(obj, getPs5Ip()); applyTelemetry(); }); }
    private void fetchHealth(boolean toast) { fetchJson(BRIDGE_URL + "/api/health", obj -> { h.fromJson(obj); if (toast) Toast.makeText(this, "Health atualizado", Toast.LENGTH_SHORT).show(); }); }

    private interface JsonCb { void ok(JSONObject obj) throws Exception; }
    private interface TextCb { void ok(String text); }

    private void fetchJson(String url, JsonCb cb) {
        new Thread(() -> {
            try {
                JSONObject obj = new JSONObject(httpGet(url));
                handler.post(() -> { try { cb.ok(obj); } catch (Exception ignored) {} });
            } catch (Exception e) {
                handler.post(() -> { if (url.contains("/api/fields")) { t.offline(); applyTelemetry(); } });
            }
        }).start();
    }

    private void fetchDialog(String title, String url) {
        fetchText(url, text -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text.length() > 3500 ? text.substring(0, 3500) + "..." : text)
                .setPositiveButton("OK", null)
                .show());
    }

    private void fetchText(String url, TextCb cb) {
        new Thread(() -> {
            try { String s = httpGet(url); handler.post(() -> cb.ok(s)); }
            catch (Exception e) { handler.post(() -> cb.ok("Erro ao acessar: " + url + "\n" + e.getMessage())); }
        }).start();
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(1500);
        conn.setReadTimeout(1500);
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void applyTelemetry() {
        fieldsTxt.setText((t.packetSize > 0 ? String.valueOf(t.packetSize) : "campos") + "  ›");
        ps5Txt.setText(getPs5Ip());
        speedGauge.setValues(num(t.velocidade), t.marcha, t.combustivel, t.autonomia);
        gearTxt.setText(t.marcha);
        fuelTxt.setText(t.combustivel + " L");
        totalTxt.setText(t.tempoTotal);
        autonomyTxt.setText(t.autonomia);
        speedTxt.setText(t.velocidade + " km/h");
        lastLapTxt.setText(t.ultimaVolta);
        lapsTxt.setText(t.voltasCorrigidas);
        bestLapTxt.setText(t.melhorVolta);
        card9Txt.setText(t.codigoCarro);
        card10Txt.setText(t.estadoCorrida);
        accelChart.push((t.acelerador - t.freio) / 100f);
        updateSessionState();
    }

    private void updateSessionState() {
        long now = System.currentTimeMillis();
        boolean active = t.connected && (num(t.velocidade) > 5 || num(t.voltasCorrigidas) > 0);
        if (active) {
            lastActive = now;
            if (!sessionActive) { sessionActive = true; sessionSaved = false; lastLaps = num(t.voltasCorrigidas); lastLapChange = now; }
        }
        int laps = num(t.voltasCorrigidas);
        if (laps != lastLaps) { lastLaps = laps; lastLapChange = now; }
        boolean finished = sessionActive && !sessionSaved && laps > 0 && !t.ultimaVolta.equals("--") && num(t.velocidade) <= 3 && now - lastLapChange > 6000 && now - lastActive > 5000;
        if (finished) saveSession("automatico");
    }

    private void saveSession(String type) {
        try {
            JSONObject o = new JSONObject();
            o.put("tipoSalvamento", type);
            o.put("dataFim", new Date().toString());
            o.put("ps5Ip", getPs5Ip());
            o.put("codigoCarro", t.codigoCarro);
            o.put("melhorVolta", t.melhorVolta);
            o.put("ultimaVolta", t.ultimaVolta);
            o.put("tempoTotal", t.tempoTotal);
            o.put("voltasCorrigidas", t.voltasCorrigidas);
            o.put("velocidadeMaxima", t.velocidadeMaxima);
            JSONArray old = new JSONArray(getPrefs().getString(KEY_SESSIONS, "[]"));
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < old.length(); i++) out.put(old.getJSONObject(i));
            getPrefs().edit().putString(KEY_SESSIONS, out.toString()).apply();
            sessionSaved = true;
            sessionActive = false;
        } catch (Exception ignored) {}
    }

    private void showHistory() {
        try {
            JSONArray arr = new JSONArray(getPrefs().getString(KEY_SESSIONS, "[]"));
            if (arr.length() == 0) { Toast.makeText(this, "Sem sessões salvas", Toast.LENGTH_SHORT).show(); return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(10, arr.length()); i++) {
                JSONObject o = arr.getJSONObject(i);
                sb.append(i + 1).append(". ").append(o.optString("dataFim", "--")).append("\n");
                sb.append("Voltas: ").append(o.optString("voltasCorrigidas", "0")).append(" | Total: ").append(o.optString("tempoTotal", "--")).append("\n\n");
            }
            new AlertDialog.Builder(this).setTitle("Histórico").setMessage(sb.toString()).setPositiveButton("OK", null).show();
        } catch (Exception e) { Toast.makeText(this, "Erro ao abrir histórico", Toast.LENGTH_SHORT).show(); }
    }

    private SharedPreferences getPrefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }
    private String getPs5Ip() { return getPrefs().getString(KEY_PS5_IP, DEFAULT_PS5_IP); }
    private int num(String s) { try { return Integer.parseInt(String.valueOf(s).replaceAll("[^0-9-]", "")); } catch (Exception e) { return 0; } }
    private LinearLayout vBox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout hRow() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String s, int size, int color, boolean bold) { TextView v = new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color); if (bold) v.setTypeface(Typeface.DEFAULT_BOLD); v.setIncludeFontPadding(true); return v; }
    private GradientDrawable round(int color, int radius, int stroke, int sw) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if (sw > 0) d.setStroke(dp(sw), stroke); return d; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(l, t, r, b); return p; }
    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b, float weight) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight); p.setMargins(l, t, r, b); return p; }

    static class Health { boolean ok; String status="--"; boolean connected; void fromJson(JSONObject o){ ok=o.optBoolean("ok", false); status=o.optString("status", "--"); connected=o.optBoolean("connected", false); } }

    static class Telemetry {
        boolean connected=false, decodeOk=false;
        int packetSize=0, acelerador=0, freio=0;
        String velocidade="0", velocidadeMaxima="0", marcha="D", combustivel="--", melhorVolta="--", ultimaVolta="--", tempoTotal="--", voltasBrutas="0", voltasCorrigidas="0", estadoCorrida="AGUARDANDO", paradasBoxes="--", codigoCarro="--", autonomia="--";
        void fromJson(JSONObject j, String ps5){
            connected=j.optBoolean("connected", false); decodeOk=j.optBoolean("decodeOk", false); packetSize=j.optInt("packetSize", 0);
            velocidade=clean(first(j,"velocidade","speed","speed_kmh"), "0"); velocidadeMaxima=clean(first(j,"velocidadeMaxima","maxSpeed","max_speed_kmh"), velocidade);
            marcha=first(j,"marcha","gear","current_gear"); if(marcha.equals("--")) marcha="D";
            acelerador=percent(j,"acelerador","throttle","throttle_percent","accelerator"); freio=percent(j,"freio","brake","brake_percent");
            combustivel=clean(first(j,"combustivel","fuel_liters","fuelLiters","fuel"), "--");
            melhorVolta=first(j,"melhorVolta","bestLap","best_lap","best_lap_time"); ultimaVolta=first(j,"ultimaVolta","lastLap","last_lap","last_lap_time");
            voltasBrutas=clean(first(j,"voltasCompletadas","voltasBrutas","rawLaps","raw_laps"), "0"); voltasCorrigidas=clean(first(j,"voltasCorrigidas","completed_laps","completedLaps","lap_count"), "0");
            paradasBoxes=clean(first(j,"paradasBoxes","pitStops","pit_stops"), "--"); codigoCarro=first(j,"codigoCarro","carCode","carId","car_id","vehicleCode","car_code");
            tempoTotal=sumRealLaps(j); if(tempoTotal.equals("--")) tempoTotal=first(j,"tempoTotalCorrida","tempoTotal","totalTime","total_time");
            autonomia=calcAutonomy(j, combustivel, voltasCorrigidas);
            estadoCorrida=(num(velocidade)>3 || num(voltasCorrigidas)>0) ? "CORRIDA" : "PARADO";
        }
        void offline(){ connected=false; decodeOk=false; estadoCorrida="OFFLINE"; }
        static String sumRealLaps(JSONObject j){ JSONArray arr=firstArray(j,"voltasReais","voltasCertas","correctLaps","valid_laps","lapTimes","lap_times","laps"); if(arr==null||arr.length()==0)return "--"; long sum=0; int ok=0; for(int i=0;i<arr.length();i++){ Object raw=arr.opt(i); String s=raw instanceof JSONObject?first((JSONObject)raw,"tempo","time","lapTime","lap_time"):String.valueOf(raw); long ms=parseLapMs(s); if(ms>0){sum+=ms;ok++;}} return ok>0?formatMs(sum):"--"; }
        static String calcAutonomy(JSONObject j,String fuel,String laps){ String direct=first(j,"autonomia","fuelAutonomy","fuel_autonomy","estimatedLapsFuel"); if(!direct.equals("--")) return direct; double f=dbl(fuel), used=dbl(first(j,"combustivelGasto","fuelUsed","fuel_used")), l=dbl(laps); if(f>0&&used>0&&l>0)return String.format(Locale.US,"%.1f",f/(used/l)); return "--"; }
        static int percent(JSONObject j,String...keys){ String raw=first(j,keys); try{ double v=Double.parseDouble(raw.replace("%","").replace(",",".").replaceAll("[^0-9.\\-]","")); if(v>0&&v<=1)v*=100; if(v>100&&v<=255)v=v/255.0*100.0; return Math.max(0,Math.min(100,(int)Math.round(v))); }catch(Exception e){return 0;} }
        static int num(String s){ try{return Integer.parseInt(String.valueOf(s).replaceAll("[^0-9-]",""));}catch(Exception e){return 0;} }
        static double dbl(String s){ try{return Double.parseDouble(String.valueOf(s).replace(",",".").replaceAll("[^0-9.\\-]",""));}catch(Exception e){return 0;} }
        static String clean(String v,String f){ if(v==null||v.equals("--")||v.length()==0)return f; if(v.endsWith(".0"))return v.substring(0,v.length()-2); return v; }
        static String first(JSONObject j,String...keys){ for(String k:keys){ if(j.has(k)&&!j.isNull(k)) return String.valueOf(j.opt(k)); } return "--"; }
        static JSONArray firstArray(JSONObject j,String...keys){ for(String k:keys){ JSONArray a=j.optJSONArray(k); if(a!=null)return a; } return null; }
        static long parseLapMs(String s){ try{ s=String.valueOf(s).trim().replace(",","."); String[] p=s.split(":"); if(p.length==1)return Math.round(Double.parseDouble(p[0])*1000.0); if(p.length==2)return Math.round((Double.parseDouble(p[0])*60.0+Double.parseDouble(p[1]))*1000.0); return Math.round((Double.parseDouble(p[0])*3600.0+Double.parseDouble(p[1])*60.0+Double.parseDouble(p[2]))*1000.0); }catch(Exception e){return 0;} }
        static String formatMs(long ms){ long total=ms/1000, milli=ms%1000, sec=total%60, min=(total/60)%60, hr=total/3600; if(hr>0)return String.format(Locale.US,"%d:%02d:%02d.%03d",hr,min,sec,milli); return String.format(Locale.US,"%d:%02d.%03d",min,sec,milli); }
    }

    static class SpeedGaugeView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint txt=new Paint(Paint.ANTI_ALIAS_FLAG); private int speed=0; private String gear="D", fuel="--", auto="--";
        SpeedGaugeView(Context c){ super(c); txt.setTypeface(Typeface.DEFAULT_BOLD); }
        void setValues(int s,String g,String f,String a){ speed=s; gear=g; fuel=f; auto=a; invalidate(); }
        @Override protected void onDraw(Canvas c){ super.onDraw(c); int w=getWidth(), h=getHeight(); float cx=w*.48f, cy=h*.58f; float r=Math.min(w,h)*.45f; p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#050A11")); c.drawCircle(cx,cy,r*1.02f,p); RectF arc=new RectF(cx-r,cy-r,cx+r,cy+r); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dpLocal(10)); p.setStrokeCap(Paint.Cap.BUTT); p.setShader(new LinearGradient(cx-r,cy,cx+r,cy,new int[]{Color.parseColor("#18E081"),Color.parseColor("#DDE8FF"),Color.parseColor("#FF3333")},null, Shader.TileMode.CLAMP)); c.drawArc(arc,145,250,false,p); p.setShader(null); p.setStrokeWidth(dpLocal(3)); for(int i=0;i<=55;i++){ float a=(float)Math.toRadians(145+250*i/55f); float inner=r-(i%5==0?dpLocal(26):dpLocal(14)); p.setColor(Color.argb(i%5==0?240:110,245,250,255)); c.drawLine(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner,cx+(float)Math.cos(a)*(r-dpLocal(2)),cy+(float)Math.sin(a)*(r-dpLocal(2)),p);} txt.setTextAlign(Paint.Align.CENTER); txt.setColor(Color.WHITE); txt.setTextSize(dpLocal(17)); int[] nums={0,40,80,120,160,200}; for(int i=0;i<nums.length;i++){ float a=(float)Math.toRadians(145+250*nums[i]/220f); c.drawText(String.valueOf(nums[i]),cx+(float)Math.cos(a)*(r-dpLocal(50)),cy+(float)Math.sin(a)*(r-dpLocal(50))+dpLocal(6),txt);} txt.setTextSize(dpLocal(13)); txt.setColor(Color.parseColor("#97A8BA")); c.drawText("km/h",cx,cy-r*.36f,txt); float pct=Math.max(0,Math.min(1,speed/220f)); float na=(float)Math.toRadians(145+250*pct); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dpLocal(5)); p.setColor(Color.parseColor("#EAF2FF")); c.drawLine(cx,cy,cx+(float)Math.cos(na)*(r-dpLocal(42)),cy+(float)Math.sin(na)*(r-dpLocal(42)),p); p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#DDE8FF")); c.drawCircle(cx,cy,dpLocal(9),p); txt.setColor(Color.WHITE); txt.setTextSize(dpLocal(34)); c.drawText(gear,cx,cy+r*.40f,txt); txt.setTextSize(dpLocal(13)); txt.setColor(Color.parseColor("#CBD5E0")); c.drawText("⛽  "+auto+" km",cx,cy+r*.56f,txt); txt.setTextSize(dpLocal(12)); c.drawText("1/2",cx,cy+r*.70f,txt); }
        private int dpLocal(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    }

    static class AccelChartView extends View { private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final float[] vals=new float[54]; AccelChartView(Context c){ super(c); } void push(float v){ System.arraycopy(vals,1,vals,0,vals.length-1); vals[vals.length-1]=Math.max(-1,Math.min(1,v)); invalidate(); } @Override protected void onDraw(Canvas c){ super.onDraw(c); int w=getWidth(),h=getHeight(); float mid=h*.82f; p.setStyle(Paint.Style.FILL); float bw=Math.max(3,w/(float)vals.length*.72f); for(int i=0;i<vals.length;i++){ float v=vals[i]; float x=i*w/(float)vals.length; float bh=Math.abs(v)*(h*.68f)+2; p.setColor(i>vals.length*.62f?Color.parseColor("#FF3358"):Color.parseColor("#159BFF")); c.drawRoundRect(x,mid-bh,x+bw,mid,bw/2,bw/2,p);} p.setColor(Color.WHITE); p.setTextSize(16); c.drawText("0",0,mid,p); } }
}
