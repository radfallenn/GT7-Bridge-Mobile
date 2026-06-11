package com.gt7.bridge.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    static final String PREF = "gt7_revolution_safe";
    static final String DEF_BRIDGE = "http://192.168.1.70:8787";
    static final String DEF_PS5 = "192.168.1.54";

    final Handler handler = new Handler(Looper.getMainLooper());
    final Map<String, String> fields = new HashMap<>();
    final java.util.ArrayList<Long> laps = new java.util.ArrayList<>();

    LinearLayout root;
    TextView status, rpm, gear, speed, bestLap, lastLap, totalTime, maxSpeedView, fuel, autonomy, weather, wet, car, carId, track, trackId, lapCount;
    String bridgeUrl = DEF_BRIDGE, ps5Ip = DEF_PS5, brand = "--", carName = "--", trackName = "--", config = "--", position = "--", lastLapText = "";
    float maxSpeed = 0f, fuelStart = Float.NaN, fuelLast = Float.NaN, fuelPerLap = Float.NaN;
    long lastPacket = 0;
    boolean sessionActive = false;

    final Runnable poller = new Runnable() {
        @Override public void run() {
            pollBridge();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        load();
        buildUi();
        handler.post(poller);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(poller);
        super.onDestroy();
    }

    void load() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        bridgeUrl = sp.getString("bridge", DEF_BRIDGE);
        ps5Ip = sp.getString("ps5", DEF_PS5);
        brand = sp.getString("brand", "--");
        carName = sp.getString("car", "--");
        trackName = sp.getString("track", "--");
        config = sp.getString("config", "--");
        position = sp.getString("position", "--");
        sessionActive = sp.getBoolean("active", false);
    }

    void saveSetting(String k, String v) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putString(k, v).apply();
        load();
        updateUi();
    }

    int maxLaps() {
        return getSharedPreferences(PREF, MODE_PRIVATE).getInt("maxLaps", 30);
    }

    void setMaxLaps(int n) {
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("maxLaps", n).apply();
        updateUi();
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);
        root.setBackgroundColor(Color.rgb(2, 8, 16));
        scroll.addView(root);
        setContentView(scroll);

        title("GT7 REVOLUTION MODULAR");
        status = card("RASPBERRY", "Conectando...");
        rpm = card("RPM", "--");
        gear = card("MARCHA", "--");
        speed = card("VELOCIDADE", "-- km/h");
        bestLap = card("MELHOR VOLTA", "--");
        lastLap = card("ÚLTIMA VOLTA", "--");
        totalTime = card("TEMPO TOTAL", "--");
        maxSpeedView = card("MAX SESSION", "-- km/h");
        fuel = card("COMBUSTÍVEL", "--");
        autonomy = card("AUTONOMIA", "-- voltas");
        weather = card("CLIMA", "--");
        wet = card("PISTA MOLHADA", "--");
        car = card("CARRO", "--");
        carId = card("ID DO CARRO", "--");
        track = card("PISTA", "--");
        trackId = card("ID DA PISTA", "--");
        lapCount = card("VOLTAS", "0 / 30");

        row(button("INICIAR SEÇÃO", new View.OnClickListener(){ public void onClick(View v){ startSession(); }}),
            button("FINALIZAR SEÇÃO", new View.OnClickListener(){ public void onClick(View v){ finishSession(); }}));

        row(button("SETTINGS", new View.OnClickListener(){ public void onClick(View v){ showSettings(); }}),
            button("SESSÕES", new View.OnClickListener(){ public void onClick(View v){ showSessions(); }}));

        updateUi();
    }

    void title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(22);
        t.setPadding(4, 6, 4, 16);
        t.setTypeface(null, 1);
        root.addView(t);
    }

    TextView card(String label, String value) {
        TextView t = new TextView(this);
        t.setText(label + "\n" + value);
        t.setTextColor(Color.rgb(235, 250, 255));
        t.setTextSize(18);
        t.setPadding(18, 16, 18, 16);
        t.setBackgroundColor(Color.rgb(8, 22, 38));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 12);
        root.addView(t, lp);
        return t;
    }

    Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        return b;
    }

    void row(View a, View b) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(r);
    }

    void pollBridge() {
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                try {
                    String body = http(fixUrl(bridgeUrl) + "/api/fields");
                    if (!valid(body)) body = http(fixUrl(bridgeUrl) + "/api/telemetry");
                    Map<String,String> parsed = parseJson(body);
                    if (!parsed.isEmpty()) {
                        fields.clear();
                        fields.putAll(parsed);
                        lastPacket = System.currentTimeMillis();
                        calculateSession();
                        ok = true;
                    }
                } catch (Exception ignored) { }
                final boolean connected = ok || System.currentTimeMillis() - lastPacket < 3500;
                handler.post(new Runnable() { @Override public void run() { updateUi(connected); } });
            }
        }).start();
    }

    String http(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(900);
        c.setReadTimeout(900);
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        c.disconnect();
        return sb.toString();
    }

    Map<String,String> parseJson(String body) throws Exception {
        Map<String,String> out = new HashMap<>();
        if (!valid(body)) return out;
        Object obj = new JSONTokener(body.trim()).nextValue();
        flatten(out, "", obj);
        return out;
    }

    void flatten(Map<String,String> out, String prefix, Object obj) throws Exception {
        if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject)obj;
            Iterator<String> it = jo.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = jo.get(k);
                String p = prefix.length() == 0 ? k : prefix + "." + k;
                flatten(out, p, v);
                if (v instanceof JSONObject && (k.equals("fields") || k.equals("telemetry") || k.equals("car") || k.equals("session"))) flatten(out, "", v);
            }
        } else {
            out.put(prefix, String.valueOf(obj));
        }
    }

    String val(String... keys) {
        for (String k: keys) if (fields.containsKey(k) && valid(fields.get(k))) return fields.get(k);
        return "--";
    }

    float num(String... keys) {
        try { return Float.parseFloat(val(keys).replace("%", "").replace(',', '.')); } catch(Exception e) { return Float.NaN; }
    }

    boolean valid(String s) { return s != null && s.trim().length() > 0 && !s.equals("--") && !s.equalsIgnoreCase("null"); }
    String fixUrl(String u) { if (!u.startsWith("http")) return "http://" + u; return u.endsWith("/") ? u.substring(0, u.length()-1) : u; }

    void calculateSession() {
        float spd = num("speed_kmh", "speedKmh", "speed", "velocityKmh");
        if (!Float.isNaN(spd) && spd > maxSpeed) maxSpeed = spd;
        String lap = val("lastLap", "lastLaptime", "last_lap", "ultimaVolta");
        if (sessionActive && valid(lap) && !lap.equals(lastLapText) && laps.size() < maxLaps()) {
            long ms = parseTime(lap);
            if (ms > 0) laps.add(ms);
            lastLapText = lap;
        }
        float f = num("fuelPercent", "fuel_percent", "fuel");
        if (sessionActive && !Float.isNaN(f)) {
            if (Float.isNaN(fuelStart)) fuelStart = f;
            fuelLast = f;
            float used = fuelStart - fuelLast;
            if (used > 0.05f && laps.size() > 0) fuelPerLap = used / laps.size();
        }
    }

    void updateUi() { updateUi(System.currentTimeMillis() - lastPacket < 3500); }
    void updateUi(boolean connected) {
        set(status, "RASPBERRY", connected ? "ONLINE · " + fixUrl(bridgeUrl) : "OFFLINE · " + fixUrl(bridgeUrl));
        set(rpm, "RPM", val("rpm", "engine_rpm", "engineRPM"));
        set(gear, "MARCHA", val("gear", "marcha", "currentGear"));
        set(speed, "VELOCIDADE", round(num("speed_kmh", "speedKmh", "speed", "velocityKmh")) + " km/h");
        set(bestLap, "MELHOR VOLTA", val("bestLap", "bestLaptime", "best_lap", "melhorVolta"));
        set(lastLap, "ÚLTIMA VOLTA", val("lastLap", "lastLaptime", "last_lap", "ultimaVolta"));
        set(totalTime, "TEMPO TOTAL", format(totalMs()));
        set(maxSpeedView, "MAX SESSION", round(maxSpeed) + " km/h");
        set(fuel, "COMBUSTÍVEL", val("fuelPercent", "fuel_percent", "fuel") + "%");
        set(autonomy, "AUTONOMIA", autonomyText());
        set(weather, "CLIMA", weatherText());
        set(wet, "PISTA MOLHADA", wetText());
        set(car, "CARRO", carName);
        set(carId, "ID DO CARRO", slug(carName));
        set(track, "PISTA", trackName);
        set(trackId, "ID DA PISTA", slug(trackName));
        set(lapCount, "VOLTAS", laps.size() + " / " + maxLaps());
    }

    void set(TextView t, String label, String value) { t.setText(label + "\n" + (valid(value) ? value : "--")); }
    String round(float f) { return Float.isNaN(f) ? "--" : String.valueOf(Math.round(f)); }
    String weatherText() { String w = val("weather", "clima", "condition", "rainIntensity"); return valid(w) ? w : "--"; }
    String wetText() { float w = num("trackWetness", "track_wetness", "surfaceWater", "waterOnTrack"); if (Float.isNaN(w)) return "--"; if (w <= 1f) w *= 100f; return Math.round(w) + "%"; }
    String autonomyText() { if (Float.isNaN(fuelLast) || Float.isNaN(fuelPerLap) || fuelPerLap <= 0) return "-- voltas"; return String.format(Locale.US, "%.1f voltas", fuelLast / fuelPerLap); }

    void startSession() {
        laps.clear(); maxSpeed = 0; fuelStart = Float.NaN; fuelLast = Float.NaN; fuelPerLap = Float.NaN; lastLapText = ""; sessionActive = true;
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("active", true).apply();
        Toast.makeText(this, "Seção iniciada", Toast.LENGTH_SHORT).show(); updateUi();
    }

    void finishSession() {
        sessionActive = false;
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("active", false).apply();
        saveSession();
        Toast.makeText(this, "Seção salva", Toast.LENGTH_SHORT).show(); updateUi();
    }

    void saveSession() {
        try {
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString("sessions", "[]"));
            JSONObject o = new JSONObject();
            o.put("date", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));
            o.put("brand", brand); o.put("car", carName); o.put("carId", slug(carName));
            o.put("track", trackName); o.put("trackId", slug(trackName)); o.put("config", config);
            JSONArray l = new JSONArray(); for (Long ms: laps) l.put(format(ms)); o.put("lapTimes", l);
            o.put("laps", laps.size()); o.put("best", bestSession()); o.put("total", format(totalMs())); o.put("maxSpeedSession", round(maxSpeed));
            o.put("weather", weatherText()); o.put("trackWetness", wetText()); arr.put(o);
            sp.edit().putString("sessions", arr.toString()).apply();
        } catch(Exception ignored) { }
    }

    void showSessions() {
        try {
            JSONArray arr = new JSONArray(getSharedPreferences(PREF, MODE_PRIVATE).getString("sessions", "[]"));
            StringBuilder sb = new StringBuilder();
            for (int i = arr.length() - 1; i >= 0; i--) sb.append(summary(arr.getJSONObject(i))).append("\n\n────────────\n\n");
            final String text = sb.length() == 0 ? "Nenhuma sessão salva" : sb.toString();
            new AlertDialog.Builder(this).setTitle("Sessões").setMessage(text).setPositiveButton("Copiar", (d,w) -> copy(text)).setNegativeButton("Fechar", null).show();
        } catch(Exception e) { Toast.makeText(this, "Erro ao abrir sessões", Toast.LENGTH_SHORT).show(); }
    }

    String summary(JSONObject o) {
        StringBuilder sb = new StringBuilder();
        sb.append("Resumo da Seção\n\n");
        sb.append("Marca - ").append(o.optString("brand", "--")).append("\n\n");
        sb.append("Carro - ").append(o.optString("car", "--")).append("\n");
        sb.append("ID do Carro - ").append(o.optString("carId", "--")).append("\n\n");
        sb.append("Pista - ").append(o.optString("track", "--")).append("\n");
        sb.append("ID da Pista - ").append(o.optString("trackId", "--")).append("\n\n");
        sb.append("Pneus / Configuração - ").append(o.optString("config", "--")).append("\n\n");
        JSONArray a = o.optJSONArray("lapTimes");
        if (a != null && a.length() > 0) for (int i=0;i<a.length();i++) sb.append("Volta ").append(i+1).append(" - ").append(a.optString(i,"--")).append("\n");
        else sb.append("Volta 1 - --\n");
        sb.append("\nMelhor Volta - ").append(o.optString("best", "--"));
        sb.append("\nTempo Total - ").append(o.optString("total", "--"));
        sb.append("\nMax Session - ").append(o.optString("maxSpeedSession", "--")).append(" km/h");
        sb.append("\nClima - ").append(o.optString("weather", "--"));
        sb.append("\nPista Molhada - ").append(o.optString("trackWetness", "--"));
        return sb.toString();
    }

    void copy(String text) { ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("GT7", text)); }

    void showSettings() {
        final String[] opts = {"Bridge URL", "PS5 IP", "Marca", "Carro", "Pista", "Pneus / Configuração", "Máximo de Voltas"};
        new AlertDialog.Builder(this).setTitle("Settings").setItems(opts, (d, which) -> {
            if (which == 0) edit("bridge", "Bridge URL", bridgeUrl, new String[]{DEF_BRIDGE});
            else if (which == 1) edit("ps5", "PS5 IP", ps5Ip, new String[]{DEF_PS5});
            else if (which == 2) edit("brand", "Marca", brand, new String[]{"Ferrari","Mazda","Porsche","Nissan","Toyota","Honda","BMW","Mercedes-Benz"});
            else if (which == 3) edit("car", "Carro", carName, new String[]{"Ferrari F40 '92","Mazda 787B '91","Porsche 962 C '88","Nissan R92CP '92"});
            else if (which == 4) edit("track", "Pista", trackName, new String[]{"Circuit de la Sarthe","Sardegna - Road Track - A","Circuit de Spa-Francorchamps","Nurburgring Nordschleife"});
            else if (which == 5) edit("config", "Pneus / Configuração", config, new String[]{"Racing Hard","Racing Medium","Racing Soft","800 Race"});
            else chooseMaxLaps();
        }).show();
    }

    void edit(final String key, String title, String current, String[] suggestions) {
        final AutoCompleteTextView input = new AutoCompleteTextView(this);
        input.setSingleLine(true); input.setText(current); input.setThreshold(1);
        input.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, suggestions));
        new AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Salvar", (d,w) -> saveSetting(key, input.getText().toString().trim())).setNegativeButton("Cancelar", null).show();
    }

    void chooseMaxLaps() {
        final String[] opts = {"10", "20", "30", "50", "100"};
        new AlertDialog.Builder(this).setTitle("Máximo de voltas").setItems(opts, (d,w) -> setMaxLaps(Integer.parseInt(opts[w]))).show();
    }

    long parseTime(String s) {
        try {
            String x = s.trim().replace(',', '.');
            String[] p = x.split(":");
            float sec = Float.parseFloat(p[p.length-1]);
            long min = p.length >= 2 ? Long.parseLong(p[p.length-2]) : 0;
            long hour = p.length >= 3 ? Long.parseLong(p[p.length-3]) : 0;
            return hour*3600000L + min*60000L + Math.round(sec*1000f);
        } catch(Exception e) { return 0; }
    }

    long totalMs() { long t = 0; for (Long l: laps) t += l; return t; }
    String bestSession() { long b = 0; for (Long l: laps) if (b == 0 || l < b) b = l; return b == 0 ? val("bestLap", "bestLaptime", "best_lap") : format(b); }
    String format(long ms) { if (ms <= 0) return "--:--.---"; long m = ms/60000; ms%=60000; long s=ms/1000; long z=ms%1000; return String.format(Locale.US, "%02d:%02d.%03d", m, s, z); }
    String slug(String s) { if (!valid(s)) return "--"; return s.toLowerCase(Locale.US).replace("'", "").replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", ""); }
}
