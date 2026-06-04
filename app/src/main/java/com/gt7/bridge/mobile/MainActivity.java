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
    private static final String VERSION = "1.6.3";
    private static final String PREF = "gt7_bridge_mobile_dark_final";
    private static final String KEY_BRIDGE_URL = "bridge_url";
    private static final String KEY_CARDS = "card_checks";
    private static final String KEY_SESSIONS = "saved_sessions";
    private static final String DEFAULT_BRIDGE_URL = "http://192.168.1.70:8787";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private DashboardView dashboard;
    private Telemetry telemetry = new Telemetry();
    private String bridgeUrl;
    private boolean polling = false;

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

    private void startPolling() {
        if (polling) return;
        polling = true;
        handler.post(poller);
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(poller);
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }

    private void fetchTelemetry() {
        new Thread(() -> {
            try {
                String body = get(bridgeUrl + "/api/fields", 900);
                if (body == null || body.trim().isEmpty()) body = get(bridgeUrl + "/api/telemetry", 900);
                if (body == null || body.trim().isEmpty()) throw new Exception("empty");
                Telemetry next = Telemetry.fromJson(body, telemetry);
                next.connected = true;
                next.status = "ONLINE";
                telemetry = next;
            } catch (Exception e) {
                telemetry.connected = false;
                telemetry.status = "OFFLINE";
            }
            runOnUiThread(() -> dashboard.invalidate());
        }).start();
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
        br.close();
        c.disconnect();
        return sb.toString();
    }

    private void showBridgeDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(bridgeUrl);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("Bridge GT7")
                .setMessage("URL do bridge local")
                .setView(input)
                .setPositiveButton("Salvar", (d, w) -> {
                    bridgeUrl = input.getText().toString().trim();
                    if (!bridgeUrl.startsWith("http")) bridgeUrl = "http://" + bridgeUrl;
                    prefs().edit().putString(KEY_BRIDGE_URL, bridgeUrl).apply();
                    Toast.makeText(this, "Bridge salvo", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveSession() {
        try {
            JSONArray arr = new JSONArray(prefs().getString(KEY_SESSIONS, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("data", new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(new Date()));
            obj.put("pista", safe(telemetry.track, "Pista não identificada"));
            obj.put("melhorVolta", safe(telemetry.bestLap, "--"));
            obj.put("ultimaVolta", safe(telemetry.lastLap, "--"));
            obj.put("tempoTotal", safe(telemetry.totalTime, "--"));
            obj.put("velocidadeMaxima", telemetry.maxSpeed);
            obj.put("voltasCorrigidas", telemetry.laps);
            obj.put("combustivel", telemetry.fuelPercent >= 0 ? telemetry.fuelPercent : JSONObject.NULL);
            JSONArray next = new JSONArray();
            next.put(obj);
            for (int i = 0; i < arr.length(); i++) next.put(arr.get(i));
            prefs().edit().putString(KEY_SESSIONS, next.toString()).apply();
            dashboard.panel = DashboardView.PANEL_SESSIONS;
            dashboard.invalidate();
            Toast.makeText(this, "Sessão salva", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar sessão", Toast.LENGTH_SHORT).show();
        }
    }

    private static String safe(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }

    static class Telemetry {
        boolean connected = false;
        String status = "OFFLINE";
        int speed = 0;
        int rpm = 0;
        String gear = "N";
        int throttle = 0;
        int brake = 0;
        float fuelPercent = -1;
        float fuelLiters = -1;
        float fuelCapacity = -1;
        String bestLap = "--";
        String lastLap = "--";
        String currentLap = "--";
        String totalTime = "--";
        int laps = 0;
        int maxSpeed = 0;
        String track = "TRACK";
        float intake = Float.NaN, coolant = Float.NaN, oil = Float.NaN;
        float tireFL = Float.NaN, tireFR = Float.NaN, tireRL = Float.NaN, tireRR = Float.NaN;
        float tirePFL = Float.NaN, tirePFR = Float.NaN, tirePRL = Float.NaN, tirePRR = Float.NaN;
        float gLat = Float.NaN, gLong = Float.NaN, acc = Float.NaN, turbo = Float.NaN, posX = Float.NaN, posY = Float.NaN, posZ = Float.NaN;

        static Telemetry fromJson(String body, Telemetry last) throws Exception {
            JSONObject obj;
            String s = body.trim();
            if (s.startsWith("[")) {
                JSONArray arr = new JSONArray(s);
                obj = arr.length() > 0 && arr.get(0) instanceof JSONObject ? arr.getJSONObject(0) : new JSONObject();
            } else {
                int a = s.indexOf('{'), b = s.lastIndexOf('}');
                obj = new JSONObject(a >= 0 && b > a ? s.substring(a, b + 1) : s);
            }
            if (obj.has("fields") && obj.get("fields") instanceof JSONObject) obj = obj.getJSONObject("fields");
            if (obj.has("telemetry") && obj.get("telemetry") instanceof JSONObject) obj = obj.getJSONObject("telemetry");
            Telemetry t = new Telemetry();
            t.speed = validInt(obj, last.speed, "speed_kmh", "speed", "velocityKmh", "velocidade");
            t.rpm = validInt(obj, last.rpm, "rpm", "engine_rpm", "currentRpm");
            t.gear = validString(obj, last.gear, "gear", "marcha", "currentGear");
            if (t.gear.equals("0")) t.gear = "N";
            t.throttle = validInt(obj, last.throttle, "throttle_percent", "throttle", "acelerador");
            t.brake = validInt(obj, last.brake, "brake_percent", "brake", "freio");
            t.fuelPercent = validFloat(obj, last.fuelPercent, "fuel_percent", "fuelPercent", "combustivelPorcentagem", "fuel_percentage");
            t.fuelLiters = validFloat(obj, last.fuelLiters, "fuel", "fuelLiters", "fuel_liters", "combustivel");
            t.fuelCapacity = validFloat(obj, last.fuelCapacity, "fuelCapacity", "fuel_capacity");
            t.bestLap = validString(obj, last.bestLap, "best_lap_text", "bestLap", "melhorVolta", "best_lap");
            t.lastLap = validString(obj, last.lastLap, "last_lap_text", "lastLap", "ultimaVolta", "last_lap");
            t.currentLap = validString(obj, last.currentLap, "voltaAtual", "currentLap", "lapCurrent");
            t.totalTime = validString(obj, last.totalTime, "total_race_time_text", "tempoTotal", "totalRaceTime");
            t.laps = validInt(obj, last.laps, "voltasCorrigidas", "correctedLaps", "completed_laps", "lap", "currentLap");
            t.maxSpeed = Math.max(Math.max(last.maxSpeed, t.speed), validInt(obj, last.maxSpeed, "velocidadeMaxima", "maxSpeed"));
            t.track = validString(obj, last.track, "trackName", "pista", "track", "circuit");
            t.intake = validFloat(obj, last.intake, "intakeTemp", "intake_temp", "temperaturaAdmissao");
            t.coolant = validFloat(obj, last.coolant, "coolantTemp", "water_temp", "engine_water_temp", "temperaturaAgua");
            t.oil = validFloat(obj, last.oil, "oilTemp", "oil_temp", "engine_oil_temp");
            t.tireFL = validFloat(obj, last.tireFL, "tireTempFL", "tyreTempFL", "tire_fl");
            t.tireFR = validFloat(obj, last.tireFR, "tireTempFR", "tyreTempFR", "tire_fr");
            t.tireRL = validFloat(obj, last.tireRL, "tireTempRL", "tyreTempRL", "tire_rl");
            t.tireRR = validFloat(obj, last.tireRR, "tireTempRR", "tyreTempRR", "tire_rr");
            t.tirePFL = validFloat(obj, last.tirePFL, "tirePressureFL", "tyrePressureFL");
            t.tirePFR = validFloat(obj, last.tirePFR, "tirePressureFR", "tyrePressureFR");
            t.tirePRL = validFloat(obj, last.tirePRL, "tirePressureRL", "tyrePressureRL");
            t.tirePRR = validFloat(obj, last.tirePRR, "tirePressureRR", "tyrePressureRR");
            t.gLat = validFloat(obj, last.gLat, "gForceLat", "g_lat", "lateralG");
            t.gLong = validFloat(obj, last.gLong, "gForceLong", "g_long", "longitudinalG");
            t.acc = validFloat(obj, last.acc, "acceleration", "acc");
            t.turbo = validFloat(obj, last.turbo, "turbo", "boostPressure", "turbo_pressure");
            if (obj.has("position") && obj.get("position") instanceof JSONObject) {
                JSONObject p = obj.getJSONObject("position");
                t.posX = validFloat(p, last.posX, "x");
                t.posY = validFloat(p, last.posY, "y");
                t.posZ = validFloat(p, last.posZ, "z");
            } else {
                t.posX = validFloat(obj, last.posX, "position.x", "x");
                t.posY = validFloat(obj, last.posY, "position.y", "y");
                t.posZ = validFloat(obj, last.posZ, "position.z", "z");
            }
            return t;
        }

        private static int validInt(JSONObject o, int fallback, String... keys) {
            float f = validFloat(o, fallback, keys);
            return Float.isNaN(f) ? fallback : Math.round(f);
        }

        private static float validFloat(JSONObject o, float fallback, String... keys) {
            for (String k : keys) try {
                if (!o.has(k) || o.isNull(k)) continue;
                String v = String.valueOf(o.get(k)).replace(",", ".");
                if (v.trim().isEmpty() || v.equals("--") || v.equalsIgnoreCase("nan")) continue;
                float f = Float.parseFloat(v);
                if (!Float.isNaN(f) && !Float.isInfinite(f)) return f;
            } catch (Exception ignored) {}
            return fallback;
        }

        private static String validString(JSONObject o, String fallback, String... keys) {
            for (String k : keys) try {
                if (!o.has(k) || o.isNull(k)) continue;
                String v = String.valueOf(o.get(k));
                if (!v.trim().isEmpty() && !v.equals("--") && !v.equalsIgnoreCase("nan")) return v;
            } catch (Exception ignored) {}
            return fallback;
        }
    }

    class DashboardView extends View {
        static final int PANEL_DASH = 0, PANEL_PICKER = 1, PANEL_SESSIONS = 2;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final ArrayList<Card> cards = new ArrayList<>();
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        boolean[] enabled = {true, true, true, true, true, true, true, true};
        int panel = PANEL_DASH;
        int expanded = -1;
        RectF leftTop = new RectF(), rightTop = new RectF();
        RectF bottom1 = new RectF(), bottom2 = new RectF(), bottom3 = new RectF(), bottom4 = new RectF();

        DashboardView(Context c) {
            super(c);
            setFocusable(true);
            setBackgroundColor(Color.BLACK);
            stroke.setStyle(Paint.Style.STROKE);
            loadChecks();
        }

        void loadChecks() {
            String s = prefs().getString(KEY_CARDS, "11111111");
            for (int i = 0; i < enabled.length; i++) enabled[i] = i >= s.length() || s.charAt(i) != '0';
        }

        void saveChecks() {
            StringBuilder sb = new StringBuilder();
            for (boolean b : enabled) sb.append(b ? '1' : '0');
            prefs().edit().putString(KEY_CARDS, sb.toString()).apply();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float scale = getWidth() / 430f;
            c.save();
            c.scale(scale, scale);
            draw(c, 430, getHeight() / scale);
            c.restore();
        }

        private void draw(Canvas c, float w, float h) {
            drawBackground(c, w, h);
            drawTop(c, w);
            drawGauge(c, w / 2, 194, 142);
            drawMeta(c);
            drawCards(c, h);
            drawBottom(c, w, h);
            if (panel == PANEL_PICKER) drawPicker(c, w, h);
            if (panel == PANEL_SESSIONS) drawSessions(c, w, h);
            if (expanded >= 0) drawExpanded(c, w, h);
        }

        private void drawBackground(Canvas c, float w, float h) {
            p.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(1, 8, 15), Color.BLACK, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        private void drawTop(Canvas c, float w) {
            leftTop.set(14, 12, 56, 54);
            rightTop.set(w - 56, 12, w - 14, 54);
            iconButton(c, leftTop, "☰");
            iconButton(c, rightTop, "▦");
            text(c, "GT7 BRIDGE MOBILE", w / 2, 31, 17, Color.WHITE, true, Paint.Align.CENTER);
            int active = 0; for (boolean b : enabled) if (b) active++;
            text(c, telemetry.status + " · " + active + "/8 CARDS", w / 2, 47, 8, Color.rgb(22, 177, 255), true, Paint.Align.CENTER);
        }

        private void iconButton(Canvas c, RectF rect, String label) {
            round(c, rect, 12, Color.rgb(3, 12, 22), Color.argb(110, 72, 184, 255), 1);
            text(c, label, rect.centerX(), rect.centerY() + 6, 19, Color.rgb(226, 245, 252), true, Paint.Align.CENTER);
        }

        private void drawGauge(Canvas c, float cx, float cy, float radius) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(2, 13, 24));
            c.drawCircle(cx, cy, radius, p);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.4f);
            stroke.setColor(Color.argb(120, 23, 144, 218));
            c.drawCircle(cx, cy, radius, stroke);
            float start = -225, sweep = 270;
            int marks = 65;
            int active = Math.round(Math.max(0, Math.min(1, telemetry.rpm / 10000f)) * (marks - 1));
            for (int i = 0; i < marks; i++) {
                float pct = i / (float) (marks - 1);
                float a = (float) Math.toRadians(start + sweep * pct);
                int col = scaleColor(pct, i <= active);
                stroke.setColor(col);
                stroke.setStrokeWidth(i <= active ? 4.6f : 3.2f);
                stroke.setStrokeCap(Paint.Cap.ROUND);
                float inner = radius - 22, outer = radius - 7;
                c.drawLine(cx + (float)Math.cos(a) * inner, cy + (float)Math.sin(a) * inner,
                        cx + (float)Math.cos(a) * outer, cy + (float)Math.sin(a) * outer, stroke);
            }
            for (int i = 0; i <= 10; i++) {
                float a = (float)Math.toRadians(start + sweep * i / 10f);
                text(c, String.valueOf(i), cx + (float)Math.cos(a) * 92, cy + 4 + (float)Math.sin(a) * 92, 10, Color.argb(210, 225, 241, 248), true, Paint.Align.CENTER);
            }
            p.setColor(Color.rgb(2, 12, 22));
            c.drawCircle(cx, cy, 75, p);
            stroke.setColor(Color.argb(80, 47, 172, 240));
            stroke.setStrokeWidth(1.2f);
            c.drawCircle(cx, cy, 75, stroke);
            text(c, String.valueOf(telemetry.speed), cx, cy - 2, 62, Color.WHITE, true, Paint.Align.CENTER);
            text(c, "KM/H", cx, cy + 29, 10, Color.rgb(76, 216, 255), true, Paint.Align.CENTER);
            r.set(cx - 27, cy + 45, cx + 27, cy + 86);
            round(c, r, 14, Color.rgb(0, 188, 246), Color.TRANSPARENT, 0);
            text(c, telemetry.gear, cx, cy + 75, 24, Color.rgb(2, 14, 24), true, Paint.Align.CENTER);
        }

        private int scaleColor(float pct, boolean lit) {
            int col;
            if (pct < .45f) col = Color.rgb(80, 225, 60);
            else if (pct < .66f) col = Color.rgb(235, 232, 42);
            else if (pct < .84f) col = Color.rgb(255, 144, 34);
            else col = Color.rgb(255, 40, 48);
            if (lit) return col;
            return Color.argb(60, Color.red(col), Color.green(col), Color.blue(col));
        }

        private void drawMeta(Canvas c) {
            float x = 15, y = 338, gap = 7, cw = 129;
            chip(c, x, y, cw, "TRACK", telemetry.track);
            chip(c, x + cw + gap, y, cw, "TIME", timeFormat.format(new Date()));
            chip(c, x + (cw + gap) * 2, y, cw, "FUEL", telemetry.fuelPercent >= 0 ? Math.round(telemetry.fuelPercent) + "%" : "--");
        }

        private void chip(Canvas c, float x, float y, float w, String label, String value) {
            r.set(x, y, x + w, y + 43);
            round(c, r, 12, Color.rgb(2, 18, 32), Color.argb(85, 72, 184, 255), 1);
            text(c, label, x + w / 2, y + 15, 7, Color.rgb(93, 163, 204), true, Paint.Align.CENTER);
            text(c, ellipsize(value, 13), x + w / 2, y + 32, 12, Color.WHITE, true, Paint.Align.CENTER);
        }

        private void drawCards(Canvas c, float h) {
            cards.clear();
            String[][] d = cardData();
            float x0 = 15, y0 = 391, gap = 7, cw = 196;
            float bottomNavTop = Math.max(840, h - 76);
            float available = bottomNavTop - y0 - 9;
            float ch = Math.max(108, Math.min(125, (available - gap * 3) / 4));
            for (int i = 0; i < 8; i++) {
                float x = x0 + (i % 2) * (cw + gap);
                float y = y0 + (i / 2) * (ch + gap);
                RectF rect = new RectF(x, y, x + cw, y + ch);
                cards.add(new Card(rect, i));
                card(c, rect, i, d[i]);
            }
        }

        private String[][] cardData() {
            return new String[][]{
                    {"TELEMETRY", "Live Data", "–", "VIEW", "SPD", telemetry.speed + " km/h", "RPM", String.valueOf(telemetry.rpm), "GEAR", telemetry.gear, "THR", telemetry.throttle + "%", "BRK", telemetry.brake + "%"},
                    {"LAP TIMER", "Best Lap", "◷", "START", "BEST", telemetry.bestLap, "LAST", telemetry.lastLap, "CUR", telemetry.currentLap, "TOTAL", telemetry.totalTime, "LAPS", String.valueOf(telemetry.laps)},
                    {"TIRE STATUS", "All Good", "⊙", "DETAILS", "FL", tire(telemetry.tireFL, telemetry.tirePFL), "FR", tire(telemetry.tireFR, telemetry.tirePFR), "RL", tire(telemetry.tireRL, telemetry.tirePRL), "RR", tire(telemetry.tireRR, telemetry.tirePRR), "", ""},
                    {"ENGINE TEMP", "Normal", "◇", "DETAILS", "COOLANT", degC(telemetry.coolant), "OIL", degC(telemetry.oil), "INTAKE", degC(telemetry.intake), "", "", "", ""},
                    {"FUEL LEVEL", "combustivelPorcentagem", "●", "DETAILS", "LEVEL", telemetry.fuelPercent >= 0 ? one(telemetry.fuelPercent) + "%" : "--", "LITERS", telemetry.fuelLiters >= 0 ? one(telemetry.fuelLiters) + " L" : "--", "CAPACITY", telemetry.fuelCapacity >= 0 ? one(telemetry.fuelCapacity) + " L" : "--", "", ""},
                    {"G-FORCE", "lateral/longitudinal", "✣", "RESET", "LAT", g(telemetry.gLat), "LONG", g(telemetry.gLong), "ACC", g(telemetry.acc), "THR", telemetry.throttle + "%", "BRK", telemetry.brake + "%"},
                    {"BOOST PRESSURE", "turbo", "◎", "DETAILS", "BOOST", boost(telemetry.turbo), "TURBO", telemetry.turbo >= 0 ? one(telemetry.turbo * 100f) + "%" : "--", "RPM", String.valueOf(telemetry.rpm), "", "", "", ""},
                    {"TRACK MAP", "pista", "⌁", "VIEW", "POSITION", "X: " + two(telemetry.posX), "", "Y: " + two(telemetry.posY), "", "Z: " + two(telemetry.posZ), "", "", "", ""}
            };
        }

        private void card(Canvas c, RectF rect, int index, String[] d) {
            round(c, rect, 12, enabled[index] ? Color.rgb(1, 16, 29) : Color.rgb(4, 12, 20), Color.argb(enabled[index] ? 88 : 42, 45, 150, 220), 1);
            RectF check = new RectF(rect.right - 36, rect.top + 10, rect.right - 12, rect.top + 34);
            round(c, check, 6, Color.rgb(3, 18, 32), Color.argb(180, 72, 184, 255), 1);
            if (enabled[index]) text(c, "✓", check.centerX(), check.centerY() + 6, 15, Color.rgb(216, 248, 255), true, Paint.Align.CENTER);
            RectF icon = new RectF(rect.left + 11, rect.top + 12, rect.left + 43, rect.top + 44);
            round(c, icon, 14, Color.rgb(0, 134, 214), Color.TRANSPARENT, 0);
            text(c, d[2], icon.centerX(), icon.centerY() + 6, 16, Color.WHITE, true, Paint.Align.CENTER);
            text(c, d[0], rect.left + 52, rect.top + 23, 11, Color.WHITE, true, Paint.Align.LEFT);
            text(c, enabled[index] ? d[1] : "OFF", rect.left + 52, rect.top + 37, 7.5f, Color.rgb(132, 177, 204), false, Paint.Align.LEFT);
            float y = rect.top + 58;
            for (int i = 4; i < d.length - 1 && y < rect.bottom - 28; i += 2) {
                if (d[i].length() == 0 && d[i + 1].length() == 0) continue;
                text(c, d[i], rect.left + 11, y, 8, Color.rgb(119, 170, 200), true, Paint.Align.LEFT);
                text(c, enabled[index] ? ellipsize(d[i + 1], 15) : "--", rect.right - 12, y, 8, Color.rgb(232, 247, 255), true, Paint.Align.RIGHT);
                stroke.setColor(Color.argb(28, 75, 154, 212));
                stroke.setStrokeWidth(1);
                c.drawLine(rect.left + 11, y + 5, rect.right - 12, y + 5, stroke);
                y += 13;
            }
            RectF btn = new RectF(rect.left + 11, rect.bottom - 25, rect.right - 11, rect.bottom - 6);
            round(c, btn, 8, Color.rgb(3, 54, 92), Color.argb(75, 72, 184, 255), 1);
            text(c, d[3], btn.centerX(), btn.centerY() + 4, 8, Color.rgb(165, 222, 255), true, Paint.Align.CENTER);
        }

        private void drawBottom(Canvas c, float w, float h) {
            float y = h - 62, gap = 6, bw = (w - 30 - gap * 3) / 4f;
            bottom1.set(15, y, 15 + bw, y + 45);
            bottom2.set(bottom1.right + gap, y, bottom1.right + gap + bw, y + 45);
            bottom3.set(bottom2.right + gap, y, bottom2.right + gap + bw, y + 45);
            bottom4.set(bottom3.right + gap, y, bottom3.right + gap + bw, y + 45);
            bottom(c, bottom1, "◴", "DASHBOARD", panel == PANEL_DASH);
            bottom(c, bottom2, "▣", "SESSIONS", panel == PANEL_SESSIONS);
            bottom(c, bottom3, "⚑", "MY TRACKS", false);
            bottom(c, bottom4, "⚙", "SETTINGS", panel == PANEL_PICKER);
        }

        private void bottom(Canvas c, RectF rect, String icon, String label, boolean active) {
            round(c, rect, 9, active ? Color.rgb(3, 38, 68) : Color.rgb(3, 12, 20), active ? Color.rgb(0, 155, 255) : Color.argb(70, 92, 150, 185), 1);
            text(c, icon, rect.centerX(), rect.top + 18, 13, Color.WHITE, true, Paint.Align.CENTER);
            text(c, label, rect.centerX(), rect.top + 34, 7.2f, active ? Color.rgb(72, 203, 255) : Color.rgb(219, 234, 241), true, Paint.Align.CENTER);
        }

        private void drawPicker(Canvas c, float w, float h) {
            RectF panelRect = new RectF(24, 74, w - 24, Math.min(h - 74, 620));
            round(c, panelRect, 18, Color.rgb(2, 10, 18), Color.argb(140, 65, 175, 235), 1);
            text(c, "ESCOLHER FUNÇÃO DA TELEMETRIA", panelRect.left + 15, panelRect.top + 26, 12, Color.WHITE, true, Paint.Align.LEFT);
            String[][] d = cardData();
            float y = panelRect.top + 58;
            for (int i = 0; i < 8; i++) {
                text(c, d[i][0], panelRect.left + 15, y, 11, Color.WHITE, true, Paint.Align.LEFT);
                text(c, enabled[i] ? "ON" : "OFF", panelRect.right - 15, y, 11, enabled[i] ? Color.rgb(60, 220, 255) : Color.rgb(120, 132, 142), true, Paint.Align.RIGHT);
                y += 34;
            }
        }

        private void drawSessions(Canvas c, float w, float h) {
            RectF panelRect = new RectF(24, 74, w - 24, Math.min(h - 74, 620));
            round(c, panelRect, 18, Color.rgb(2, 10, 18), Color.argb(140, 65, 175, 235), 1);
            text(c, "MINHAS PISTAS", panelRect.left + 15, panelRect.top + 26, 12, Color.WHITE, true, Paint.Align.LEFT);
            try {
                JSONArray arr = new JSONArray(prefs().getString(KEY_SESSIONS, "[]"));
                if (arr.length() == 0) text(c, "Nenhuma sessão salva ainda.", panelRect.left + 15, panelRect.top + 60, 11, Color.rgb(200, 222, 238), false, Paint.Align.LEFT);
                float y = panelRect.top + 56;
                for (int i = 0; i < Math.min(8, arr.length()); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    RectF item = new RectF(panelRect.left + 12, y, panelRect.right - 12, y + 44);
                    round(c, item, 12, Color.rgb(4, 24, 42), Color.argb(65, 72, 184, 255), 1);
                    text(c, ellipsize(o.optString("pista", "Pista não identificada"), 28), item.left + 10, item.top + 17, 10, Color.WHITE, true, Paint.Align.LEFT);
                    text(c, "Melhor " + o.optString("melhorVolta", "--") + " · Última " + o.optString("ultimaVolta", "--"), item.left + 10, item.top + 34, 8.5f, Color.rgb(170, 210, 232), false, Paint.Align.LEFT);
                    y += 51;
                }
            } catch (Exception ignored) {}
        }

        private void drawExpanded(Canvas c, float w, float h) {
            String[][] d = cardData();
            RectF overlay = new RectF(20, 105, w - 20, h - 92);
            round(c, overlay, 18, Color.rgb(1, 8, 15), Color.argb(170, 72, 184, 255), 1.4f);
            text(c, d[expanded][0], overlay.left + 18, overlay.top + 34, 18, Color.WHITE, true, Paint.Align.LEFT);
            text(c, "TOQUE PARA FECHAR", overlay.right - 18, overlay.top + 31, 8, Color.rgb(132, 194, 230), true, Paint.Align.RIGHT);
            float y = overlay.top + 72;
            for (int i = 4; i < d[expanded].length - 1; i += 2) {
                if (d[expanded][i].length() == 0 && d[expanded][i + 1].length() == 0) continue;
                text(c, d[expanded][i], overlay.left + 18, y, 13, Color.rgb(115, 186, 230), true, Paint.Align.LEFT);
                text(c, d[expanded][i + 1], overlay.right - 18, y, 15, Color.WHITE, true, Paint.Align.RIGHT);
                stroke.setColor(Color.argb(45, 72, 184, 255));
                c.drawLine(overlay.left + 18, y + 9, overlay.right - 18, y + 9, stroke);
                y += 38;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float scale = getWidth() / 430f;
            float x = e.getX() / scale, y = e.getY() / scale;
            if (expanded >= 0) { expanded = -1; invalidate(); return true; }
            if (leftTop.contains(x, y)) { panel = panel == PANEL_SESSIONS ? PANEL_DASH : PANEL_SESSIONS; invalidate(); return true; }
            if (rightTop.contains(x, y)) { panel = panel == PANEL_PICKER ? PANEL_DASH : PANEL_PICKER; invalidate(); return true; }
            if (bottom1.contains(x, y)) { panel = PANEL_DASH; invalidate(); return true; }
            if (bottom2.contains(x, y)) { panel = PANEL_SESSIONS; invalidate(); return true; }
            if (bottom3.contains(x, y)) { saveSession(); return true; }
            if (bottom4.contains(x, y)) { panel = PANEL_PICKER; invalidate(); return true; }
            if (panel == PANEL_PICKER) {
                float rowY = 132;
                for (int i = 0; i < enabled.length; i++) if (y >= rowY + i * 34 - 17 && y <= rowY + i * 34 + 17) {
                    enabled[i] = !enabled[i]; saveChecks(); invalidate(); return true;
                }
            }
            for (Card card : cards) {
                RectF check = new RectF(card.rect.right - 42, card.rect.top + 4, card.rect.right - 4, card.rect.top + 42);
                RectF button = new RectF(card.rect.left + 7, card.rect.bottom - 32, card.rect.right - 7, card.rect.bottom);
                if (check.contains(x, y)) { enabled[card.index] = !enabled[card.index]; saveChecks(); invalidate(); return true; }
                if (button.contains(x, y)) { expanded = card.index; invalidate(); return true; }
            }
            if (y < 60 && x > 120 && x < 310) showBridgeDialog();
            return true;
        }

        private void round(Canvas c, RectF rect, float rad, int fill, int strokeColor, float strokeWidth) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(fill); c.drawRoundRect(rect, rad, rad, p);
            if (strokeColor != Color.TRANSPARENT && strokeWidth > 0) { stroke.setColor(strokeColor); stroke.setStrokeWidth(strokeWidth); stroke.setStyle(Paint.Style.STROKE); c.drawRoundRect(rect, rad, rad, stroke); }
        }

        private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size); p.setTextAlign(align);
            p.setTypeface(bold ? Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) : Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            c.drawText(s == null ? "--" : s, x, y, p);
        }

        private String tire(float temp, float press) { return (Float.isNaN(temp) ? "--" : Math.round(temp) + "°C") + (Float.isNaN(press) ? "" : " / " + one(press) + " bar"); }
        private String degC(float v) { return Float.isNaN(v) ? "--" : String.valueOf(Math.round(v)) + "°C"; }
        private String one(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.1f", v); }
        private String two(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.1f m", v); }
        private String g(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.2f G", v); }
        private String boost(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.2f bar", v); }
        private String ellipsize(String s, int max) { return s == null ? "--" : s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…"; }
    }

    static class Card {
        RectF rect;
        int index;
        Card(RectF r, int i) { rect = r; index = i; }
    }
}
