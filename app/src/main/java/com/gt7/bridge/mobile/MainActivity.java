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
    private static final String VERSION = "1.6.2";
    private static final String PREF = "gt7_bridge_mobile_visual_ref";
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

    @Override protected void onResume() {
        super.onResume();
        startPolling();
    }

    @Override protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void startPolling() {
        if (polling) return;
        polling = true;
        handler.post(poller);
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(poller);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREF, MODE_PRIVATE);
    }

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
            JSONArray arr;
            String current = prefs().getString(KEY_SESSIONS, "[]");
            arr = new JSONArray(current);
            JSONObject obj = new JSONObject();
            obj.put("data", new SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(new Date()));
            obj.put("pista", safe(telemetry.track, "Pista não identificada"));
            obj.put("melhorVolta", safe(telemetry.bestLap, "--"));
            obj.put("ultimaVolta", safe(telemetry.lastLap, "--"));
            obj.put("tempoTotal", safe(telemetry.totalTime, "--"));
            obj.put("velocidadeMaxima", telemetry.maxSpeed);
            obj.put("voltasCorrigidas", telemetry.laps);
            obj.put("combustivel", telemetry.fuelPercent >= 0 ? telemetry.fuelPercent : JSONObject.NULL);
            obj.put("telemetriaCompleta", telemetry.raw == null ? "{}" : telemetry.raw.toString());
            JSONArray next = new JSONArray();
            next.put(obj);
            for (int i = 0; i < arr.length(); i++) next.put(arr.get(i));
            prefs().edit().putString(KEY_SESSIONS, next.toString()).apply();
            dashboard.mode = DashboardView.MODE_SESSIONS;
            dashboard.invalidate();
            Toast.makeText(this, "Sessão salva", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar sessão", Toast.LENGTH_SHORT).show();
        }
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

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
        String bestLap = "--";
        String lastLap = "--";
        String currentLap = "--";
        String totalTime = "--";
        int laps = 0;
        int maxSpeed = 0;
        String track = "TRACK";
        float intake = Float.NaN;
        float coolant = Float.NaN;
        float oil = Float.NaN;
        float tireFL = Float.NaN, tireFR = Float.NaN, tireRL = Float.NaN, tireRR = Float.NaN;
        float gLat = Float.NaN, gLong = Float.NaN, turbo = Float.NaN, posX = Float.NaN, posY = Float.NaN;
        JSONObject raw;

        static Telemetry fromJson(String body, Telemetry last) throws Exception {
            JSONObject obj;
            String trimmed = body.trim();
            if (trimmed.startsWith("[")) {
                JSONArray arr = new JSONArray(trimmed);
                obj = arr.length() > 0 && arr.get(0) instanceof JSONObject ? arr.getJSONObject(0) : new JSONObject();
            } else {
                int s = trimmed.indexOf('{');
                int e = trimmed.lastIndexOf('}');
                obj = new JSONObject(s >= 0 && e > s ? trimmed.substring(s, e + 1) : trimmed);
            }
            if (obj.has("fields") && obj.get("fields") instanceof JSONObject) obj = obj.getJSONObject("fields");
            if (obj.has("telemetry") && obj.get("telemetry") instanceof JSONObject) obj = obj.getJSONObject("telemetry");
            Telemetry t = new Telemetry();
            t.raw = obj;
            t.speed = validInt(obj, last.speed, "speed_kmh", "speed", "velocityKmh", "velocidade");
            t.rpm = validInt(obj, last.rpm, "rpm", "engine_rpm", "currentRpm");
            t.gear = validString(obj, last.gear, "gear", "marcha", "currentGear");
            if (t.gear.equals("0")) t.gear = "N";
            t.throttle = validInt(obj, last.throttle, "throttle_percent", "throttle", "acelerador");
            t.brake = validInt(obj, last.brake, "brake_percent", "brake", "freio");
            t.fuelPercent = validFloat(obj, last.fuelPercent, "fuel_percent", "fuelPercent", "combustivelPorcentagem", "fuel_percentage");
            t.fuelLiters = validFloat(obj, last.fuelLiters, "fuel", "fuelLiters", "fuel_liters", "combustivel");
            t.bestLap = validString(obj, last.bestLap, "best_lap_text", "bestLap", "melhorVolta", "best_lap");
            t.lastLap = validString(obj, last.lastLap, "last_lap_text", "lastLap", "ultimaVolta", "last_lap");
            t.currentLap = validString(obj, last.currentLap, "voltaAtual", "currentLap", "lapCurrent");
            t.totalTime = validString(obj, last.totalTime, "total_race_time_text", "tempoTotal", "tempoTotalCorrida", "totalRaceTime");
            t.laps = validInt(obj, last.laps, "voltasCorrigidas", "correctedLaps", "completed_laps", "lap", "currentLap");
            t.maxSpeed = Math.max(Math.max(last.maxSpeed, t.speed), validInt(obj, last.maxSpeed, "velocidadeMaxima", "maxSpeed"));
            t.track = validString(obj, last.track, "trackName", "pista", "track", "circuit");
            if (obj.has("map") && obj.get("map") instanceof JSONObject) {
                JSONObject map = obj.getJSONObject("map");
                t.track = validString(map, t.track, "track_name", "trackName", "pista");
                if (map.has("car_position") && map.get("car_position") instanceof JSONObject) {
                    JSONObject p = map.getJSONObject("car_position");
                    t.posX = validFloat(p, last.posX, "x");
                    t.posY = validFloat(p, last.posY, "y");
                }
            }
            if (obj.has("position") && obj.get("position") instanceof JSONObject) {
                JSONObject p = obj.getJSONObject("position");
                t.posX = validFloat(p, t.posX, "x");
                t.posY = validFloat(p, t.posY, "y");
            }
            t.intake = validFloat(obj, last.intake, "intakeTemp", "intake_temp", "temperaturaAdmissao");
            t.coolant = validFloat(obj, last.coolant, "coolantTemp", "water_temp", "engine_water_temp", "temperaturaAgua");
            t.oil = validFloat(obj, last.oil, "oilTemp", "oil_temp", "engine_oil_temp");
            t.tireFL = validFloat(obj, last.tireFL, "tireTempFL", "tyreTempFL", "tire_fl");
            t.tireFR = validFloat(obj, last.tireFR, "tireTempFR", "tyreTempFR", "tire_fr");
            t.tireRL = validFloat(obj, last.tireRL, "tireTempRL", "tyreTempRL", "tire_rl");
            t.tireRR = validFloat(obj, last.tireRR, "tireTempRR", "tyreTempRR", "tire_rr");
            t.gLat = validFloat(obj, last.gLat, "gForceLat", "g_lat", "lateralG");
            t.gLong = validFloat(obj, last.gLong, "gForceLong", "g_long", "longitudinalG");
            t.turbo = validFloat(obj, last.turbo, "turbo", "boostPressure", "turbo_pressure");
            return t;
        }

        private static int validInt(JSONObject o, int fallback, String... keys) {
            float f = validFloat(o, fallback, keys);
            return Float.isNaN(f) ? fallback : Math.round(f);
        }

        private static float validFloat(JSONObject o, float fallback, String... keys) {
            for (String k : keys) {
                try {
                    if (!o.has(k) || o.isNull(k)) continue;
                    String v = String.valueOf(o.get(k)).replace(",", ".");
                    if (v.trim().isEmpty() || v.equals("--") || v.equalsIgnoreCase("nan")) continue;
                    float f = Float.parseFloat(v);
                    if (Float.isNaN(f) || Float.isInfinite(f)) continue;
                    return f;
                } catch (Exception ignored) {}
            }
            return fallback;
        }

        private static String validString(JSONObject o, String fallback, String... keys) {
            for (String k : keys) {
                try {
                    if (!o.has(k) || o.isNull(k)) continue;
                    String v = String.valueOf(o.get(k));
                    if (v.trim().isEmpty() || v.equals("--") || v.equalsIgnoreCase("nan")) continue;
                    return v;
                } catch (Exception ignored) {}
            }
            return fallback;
        }
    }

    class DashboardView extends View {
        static final int MODE_DASHBOARD = 0;
        static final int MODE_SETTINGS = 1;
        static final int MODE_SESSIONS = 2;

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final ArrayList<Card> cards = new ArrayList<>();
        int mode = MODE_DASHBOARD;
        boolean[] enabled = {true, true, true, true, true, true, true, true};
        RectF menuRect = new RectF();
        RectF functionRect = new RectF();
        RectF saveRect = new RectF();
        RectF finishRect = new RectF();
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        DashboardView(Context c) {
            super(c);
            setFocusable(true);
            setBackgroundColor(Color.rgb(3, 11, 22));
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
            float w = getWidth();
            float scale = w / 430f;
            c.save();
            c.scale(scale, scale);
            drawDashboard(c, scale);
            c.restore();
        }

        private void drawDashboard(Canvas c, float scale) {
            float w = 430;
            float h = getHeight() / scale;
            drawBackground(c, w, h);
            drawTop(c, w);
            drawShell(c, w, h);
            if (mode == MODE_SETTINGS) drawSettings(c, w);
            if (mode == MODE_SESSIONS) drawSessions(c, w);
        }

        private void drawBackground(Canvas c, float w, float h) {
            p.setShader(new LinearGradient(0, 0, 0, h, Color.rgb(10, 45, 82), Color.rgb(2, 7, 16), Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            p.setColor(Color.argb(95, 22, 133, 210));
            c.drawCircle(w / 2, 90, 190, p);
            p.setColor(Color.argb(30, 0, 170, 255));
            for (int i = 0; i < 7; i++) c.drawCircle(w / 2, 120, 90 + i * 38, p);
        }

        private void drawTop(Canvas c, float w) {
            menuRect.set(14, 18, 58, 62);
            functionRect.set(w - 58, 18, w - 14, 62);
            drawIconButton(c, menuRect, "☰");
            drawIconButton(c, functionRect, "☷");
            text(c, "GT7 BRIDGE MOBILE", w / 2, 37, 17, Color.rgb(235, 250, 255), true, Paint.Align.CENTER);
            int active = 0;
            for (boolean b : enabled) if (b) active++;
            text(c, telemetry.status + " · " + active + "/8 CARDS", w / 2, 53, 8, Color.rgb(115, 205, 255), true, Paint.Align.CENTER);
        }

        private void drawIconButton(Canvas c, RectF rect, String label) {
            round(c, rect, 15, Color.rgb(9, 45, 80), Color.argb(180, 88, 201, 255), 1.3f);
            text(c, label, rect.centerX(), rect.centerY() + 7, 20, Color.rgb(225, 249, 255), true, Paint.Align.CENTER);
        }

        private void drawShell(Canvas c, float w, float h) {
            r.set(14, 76, w - 14, Math.max(h - 22, 850));
            round(c, r, 32, Color.rgb(5, 22, 43), Color.argb(110, 76, 187, 255), 1.2f);
            p.setColor(Color.argb(28, 64, 194, 255));
            c.drawCircle(w / 2, 210, 135, p);
            drawGauge(c, w / 2, 235);
            drawMeta(c);
            drawCards(c);
            drawActions(c);
        }

        private void drawGauge(Canvas c, float cx, float cy) {
            float radius = 142;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(170, 5, 20, 38));
            c.drawCircle(cx, cy, radius, p);
            stroke.setStrokeWidth(2);
            stroke.setColor(Color.argb(70, 88, 202, 255));
            c.drawCircle(cx, cy, radius, stroke);
            float start = -225;
            float sweep = 270;
            int marks = 54;
            int active = Math.round(Math.max(0, Math.min(1, telemetry.rpm / 10000f)) * (marks - 1));
            for (int i = 0; i < marks; i++) {
                float a = (float) Math.toRadians(start + sweep * i / (marks - 1));
                float inner = 118;
                float outer = 138;
                float x1 = cx + (float) Math.cos(a) * inner;
                float y1 = cy + (float) Math.sin(a) * inner;
                float x2 = cx + (float) Math.cos(a) * outer;
                float y2 = cy + (float) Math.sin(a) * outer;
                stroke.setStrokeWidth(i <= active ? 5 : 3);
                stroke.setStrokeCap(Paint.Cap.ROUND);
                if (i <= active) {
                    float pct = i / (float) (marks - 1);
                    stroke.setColor(pct > .82f ? Color.rgb(255, 48, 92) : pct > .65f ? Color.rgb(255, 172, 50) : Color.rgb(32, 219, 255));
                } else {
                    stroke.setColor(Color.argb(65, 105, 175, 210));
                }
                c.drawLine(x1, y1, x2, y2, stroke);
            }
            for (int i = 0; i <= 10; i++) {
                float a = (float) Math.toRadians(start + sweep * i / 10f);
                text(c, String.valueOf(i), cx + (float) Math.cos(a) * 96, cy + 4 + (float) Math.sin(a) * 96, 10, Color.argb(190, 218, 244, 255), true, Paint.Align.CENTER);
            }
            p.setColor(Color.argb(215, 6, 29, 54));
            c.drawCircle(cx, cy, 76, p);
            stroke.setColor(Color.argb(110, 81, 202, 255));
            stroke.setStrokeWidth(1.3f);
            c.drawCircle(cx, cy, 76, stroke);
            text(c, String.valueOf(telemetry.speed), cx, cy - 4, 66, Color.WHITE, true, Paint.Align.CENTER);
            text(c, "KM/H", cx, cy + 28, 10, Color.rgb(104, 220, 255), true, Paint.Align.CENTER);
            r.set(cx - 28, cy + 45, cx + 28, cy + 87);
            round(c, r, 15, Color.rgb(45, 192, 255), Color.TRANSPARENT, 0);
            text(c, telemetry.gear, cx, cy + 75, 24, Color.rgb(0, 19, 33), true, Paint.Align.CENTER);
        }

        private void drawMeta(Canvas c) {
            float x = 31, y = 372, gap = 8, cw = 118;
            drawChip(c, x, y, cw, "TRACK", telemetry.track);
            drawChip(c, x + cw + gap, y, cw, "TIME", timeFormat.format(new Date()));
            drawChip(c, x + (cw + gap) * 2, y, cw, "FUEL", telemetry.fuelPercent >= 0 ? Math.round(telemetry.fuelPercent) + "%" : "--");
        }

        private void drawChip(Canvas c, float x, float y, float w, String label, String value) {
            r.set(x, y, x + w, y + 48);
            round(c, r, 16, Color.argb(185, 7, 35, 65), Color.argb(70, 82, 196, 255), 1);
            text(c, label, x + w / 2, y + 17, 7.5f, Color.rgb(114, 188, 230), true, Paint.Align.CENTER);
            text(c, ellipsize(value, 13), x + w / 2, y + 35, 12, Color.rgb(232, 251, 255), true, Paint.Align.CENTER);
        }

        private void drawCards(Canvas c) {
            cards.clear();
            String[][] data = cardData();
            float x0 = 31, y0 = 434, cw = 177, ch = 138, gap = 10;
            for (int i = 0; i < 8; i++) {
                float x = x0 + (i % 2) * (cw + gap);
                float y = y0 + (i / 2) * (ch + gap);
                RectF rect = new RectF(x, y, x + cw, y + ch);
                cards.add(new Card(rect, i));
                drawCard(c, rect, i, data[i]);
            }
        }

        private String[][] cardData() {
            return new String[][]{
                    {"TELEMETRY", "Live Data", "▰", "VIEW", "SPD", telemetry.speed + " km/h", "RPM", String.valueOf(telemetry.rpm), "GEAR", telemetry.gear, "THR", telemetry.throttle + "%", "BRK", telemetry.brake + "%"},
                    {"LAP TIMER", "Best Lap", "◷", "START", "BEST", telemetry.bestLap, "LAST", telemetry.lastLap, "CUR", telemetry.currentLap, "TOTAL", telemetry.totalTime, "LAPS", String.valueOf(telemetry.laps)},
                    {"TIRE STATUS", "All Good", "◎", "DETAILS", "FL", deg(telemetry.tireFL), "FR", deg(telemetry.tireFR), "RL", deg(telemetry.tireRL), "RR", deg(telemetry.tireRR), "", ""},
                    {"ENGINE TEMP", "Normal", "◈", "DETAILS", "COOLANT", degC(telemetry.coolant), "OIL", degC(telemetry.oil), "INTAKE", degC(telemetry.intake), "", "", "", ""},
                    {"FUEL LEVEL", "combustivelPorcentagem", "◍", "DETAILS", "LEVEL", telemetry.fuelPercent >= 0 ? one(telemetry.fuelPercent) + "%" : "--", "LITERS", telemetry.fuelLiters >= 0 ? one(telemetry.fuelLiters) + " L" : "--", "MAX SPD", telemetry.maxSpeed + " km/h", "", "", "", ""},
                    {"G-FORCE", "lateral/longitudinal", "✣", "RESET", "LAT", g(telemetry.gLat), "LONG", g(telemetry.gLong), "THR", telemetry.throttle + "%", "BRK", telemetry.brake + "%", "", ""},
                    {"BOOST PRESSURE", "turbo", "◆", "DETAILS", "BOOST", boost(telemetry.turbo), "RPM", String.valueOf(telemetry.rpm), "GEAR", telemetry.gear, "", "", "", ""},
                    {"TRACK MAP", "pista", "⌖", "VIEW", "TRACK", telemetry.track, "X", two(telemetry.posX), "Y", two(telemetry.posY), "LAP", String.valueOf(telemetry.laps), "", ""}
            };
        }

        private void drawCard(Canvas c, RectF rect, int index, String[] d) {
            int fill = enabled[index] ? Color.rgb(7, 31, 59) : Color.rgb(8, 20, 35);
            round(c, rect, 21, fill, Color.argb(enabled[index] ? 86 : 40, 72, 190, 255), 1);
            p.setColor(Color.argb(enabled[index] ? 32 : 15, 77, 199, 255));
            c.drawCircle(rect.right - 18, rect.top + 4, 44, p);
            RectF check = new RectF(rect.right - 29, rect.top + 9, rect.right - 10, rect.top + 28);
            round(c, check, 6, Color.rgb(10, 48, 84), Color.argb(190, 111, 219, 255), 1);
            if (enabled[index]) text(c, "✓", check.centerX(), check.centerY() + 5, 12, Color.rgb(142, 242, 255), true, Paint.Align.CENTER);
            RectF icon = new RectF(rect.left + 10, rect.top + 12, rect.left + 41, rect.top + 43);
            round(c, icon, 12, enabled[index] ? Color.rgb(21, 134, 213) : Color.rgb(30, 70, 96), Color.TRANSPARENT, 0);
            text(c, d[2], icon.centerX(), icon.centerY() + 5, 15, Color.rgb(240, 252, 255), true, Paint.Align.CENTER);
            text(c, d[0], rect.left + 50, rect.top + 25, 10, Color.rgb(243, 251, 255), true, Paint.Align.LEFT);
            text(c, enabled[index] ? d[1] : "OFF", rect.left + 50, rect.top + 38, 7.5f, Color.rgb(117, 187, 226), false, Paint.Align.LEFT);
            float y = rect.top + 60;
            for (int i = 4; i < d.length - 1; i += 2) {
                if (d[i].length() == 0) continue;
                text(c, d[i], rect.left + 11, y, 8.5f, Color.rgb(108, 174, 214), true, Paint.Align.LEFT);
                text(c, enabled[index] ? ellipsize(d[i + 1], 12) : "--", rect.right - 12, y, 8.5f, Color.rgb(232, 251, 255), true, Paint.Align.RIGHT);
                stroke.setColor(Color.argb(24, 96, 189, 255));
                stroke.setStrokeWidth(1);
                c.drawLine(rect.left + 11, y + 5, rect.right - 11, y + 5, stroke);
                y += 15;
            }
            RectF btn = new RectF(rect.left + 10, rect.bottom - 34, rect.right - 10, rect.bottom - 9);
            round(c, btn, 11, Color.rgb(13, 72, 120), Color.argb(92, 93, 207, 255), 1);
            text(c, d[3], btn.centerX(), btn.centerY() + 4, 8, Color.rgb(201, 245, 255), true, Paint.Align.CENTER);
        }

        private void drawActions(Canvas c) {
            saveRect.set(31, 1031, 208, 1074);
            finishRect.set(218, 1031, 395, 1074);
            drawAction(c, saveRect, "SALVAR SESSÃO");
            drawAction(c, finishRect, "FINALIZAR");
        }

        private void drawAction(Canvas c, RectF rect, String label) {
            round(c, rect, 15, Color.rgb(18, 78, 132), Color.argb(95, 84, 193, 255), 1.2f);
            text(c, label, rect.centerX(), rect.centerY() + 4, 10, Color.rgb(239, 252, 255), true, Paint.Align.CENTER);
        }

        private void drawSettings(Canvas c, float w) {
            RectF panel = new RectF(31, 1088, w - 31, 1450);
            round(c, panel, 22, Color.rgb(6, 25, 46), Color.argb(90, 84, 193, 255), 1);
            text(c, "ESCOLHER FUNÇÃO", panel.left + 14, panel.top + 25, 12, Color.rgb(234, 250, 255), true, Paint.Align.LEFT);
            String[][] d = cardData();
            float y = panel.top + 54;
            for (int i = 0; i < 8; i++) {
                text(c, d[i][0], panel.left + 14, y, 11, Color.rgb(223, 248, 255), true, Paint.Align.LEFT);
                text(c, enabled[i] ? "ON" : "OFF", panel.right - 14, y, 11, enabled[i] ? Color.rgb(94, 236, 255) : Color.rgb(145, 155, 165), true, Paint.Align.RIGHT);
                stroke.setColor(Color.argb(35, 255, 255, 255));
                c.drawLine(panel.left + 12, y + 11, panel.right - 12, y + 11, stroke);
                y += 34;
            }
        }

        private void drawSessions(Canvas c, float w) {
            RectF panel = new RectF(31, 1088, w - 31, 1540);
            round(c, panel, 22, Color.rgb(6, 25, 46), Color.argb(90, 84, 193, 255), 1);
            text(c, "MINHAS PISTAS", panel.left + 14, panel.top + 25, 12, Color.rgb(234, 250, 255), true, Paint.Align.LEFT);
            try {
                JSONArray arr = new JSONArray(prefs().getString(KEY_SESSIONS, "[]"));
                if (arr.length() == 0) text(c, "Nenhuma sessão salva ainda.", panel.left + 14, panel.top + 58, 11, Color.rgb(219, 239, 255), false, Paint.Align.LEFT);
                float y = panel.top + 58;
                for (int i = 0; i < Math.min(8, arr.length()); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    RectF item = new RectF(panel.left + 12, y, panel.right - 12, y + 48);
                    round(c, item, 14, Color.rgb(10, 40, 70), Color.argb(50, 84, 193, 255), 1);
                    text(c, ellipsize(o.optString("pista", "Pista não identificada"), 28), item.left + 10, item.top + 18, 10.5f, Color.WHITE, true, Paint.Align.LEFT);
                    text(c, "Melhor " + o.optString("melhorVolta", "--") + " · Última " + o.optString("ultimaVolta", "--"), item.left + 10, item.top + 36, 9, Color.rgb(170, 218, 245), false, Paint.Align.LEFT);
                    y += 56;
                }
            } catch (Exception ignored) {}
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() != MotionEvent.ACTION_UP) return true;
            float scale = getWidth() / 430f;
            float x = e.getX() / scale;
            float y = e.getY() / scale;
            if (menuRect.contains(x, y)) {
                mode = mode == MODE_SESSIONS ? MODE_DASHBOARD : MODE_SESSIONS;
                invalidate();
                return true;
            }
            if (functionRect.contains(x, y)) {
                mode = mode == MODE_SETTINGS ? MODE_DASHBOARD : MODE_SETTINGS;
                invalidate();
                return true;
            }
            if (saveRect.contains(x, y)) {
                saveSession();
                return true;
            }
            if (finishRect.contains(x, y)) {
                telemetry.totalTime = telemetry.totalTime.equals("--") ? new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()) : telemetry.totalTime;
                Toast.makeText(MainActivity.this, "Corrida finalizada", Toast.LENGTH_SHORT).show();
                invalidate();
                return true;
            }
            if (mode == MODE_SETTINGS) {
                float yStart = 1142;
                for (int i = 0; i < enabled.length; i++) {
                    if (y >= yStart + i * 34 - 16 && y <= yStart + i * 34 + 16) {
                        enabled[i] = !enabled[i];
                        saveChecks();
                        invalidate();
                        return true;
                    }
                }
            }
            for (Card card : cards) {
                RectF check = new RectF(card.rect.right - 33, card.rect.top + 4, card.rect.right, card.rect.top + 35);
                if (check.contains(x, y)) {
                    enabled[card.index] = !enabled[card.index];
                    saveChecks();
                    invalidate();
                    return true;
                }
            }
            if (y < 76 && x > 130 && x < 300) showBridgeDialog();
            return true;
        }

        private void round(Canvas c, RectF rect, float rad, int fill, int strokeColor, float strokeWidth) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(fill);
            c.drawRoundRect(rect, rad, rad, p);
            if (strokeColor != Color.TRANSPARENT && strokeWidth > 0) {
                stroke.setColor(strokeColor);
                stroke.setStrokeWidth(strokeWidth);
                stroke.setStyle(Paint.Style.STROKE);
                c.drawRoundRect(rect, rad, rad, stroke);
            }
        }

        private void text(Canvas c, String s, float x, float y, float size, int color, boolean bold, Paint.Align align) {
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setTextSize(size);
            p.setTextAlign(align);
            p.setTypeface(bold ? Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) : Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            c.drawText(s == null ? "--" : s, x, y, p);
        }

        private String deg(float v) { return Float.isNaN(v) ? "--" : String.valueOf(Math.round(v)) + "°"; }
        private String degC(float v) { return Float.isNaN(v) ? "--" : String.valueOf(Math.round(v)) + "°C"; }
        private String one(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.1f", v); }
        private String two(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.2f", v); }
        private String g(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.2fg", v); }
        private String boost(float v) { return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.2f bar", v); }
        private String ellipsize(String s, int max) { return s == null ? "--" : s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…"; }
    }

    static class Card {
        RectF rect;
        int index;
        Card(RectF r, int i) { rect = r; index = i; }
    }
}
