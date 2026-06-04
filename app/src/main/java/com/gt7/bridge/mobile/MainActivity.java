package com.gt7.bridge.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String VERSION = "1.6.0";
    private static final String BRIDGE_URL = "http://192.168.1.70:8787";
    private static final String PREF = "gt7_bridge_mobile";
    private static final String KEY_PS5_IP = "ps5_ip";
    private static final String KEY_SESSIONS = "gt7_saved_sessions";
    private static final String KEY_CARD_PREFIX = "card_metric_";
    private static final String DEFAULT_PS5_IP = "192.168.1.54";

    private static final int BG = Color.parseColor("#02070D");
    private static final int PANEL = Color.parseColor("#06111D");
    private static final int PANEL_2 = Color.parseColor("#081A2A");
    private static final int STROKE = Color.parseColor("#1B4D78");
    private static final int TXT = Color.parseColor("#F6FAFF");
    private static final int MUTED = Color.parseColor("#9CADBE");
    private static final int BLUE = Color.parseColor("#159BFF");
    private static final int CYAN = Color.parseColor("#16E6FF");
    private static final int GREEN = Color.parseColor("#37F06B");
    private static final int RED = Color.parseColor("#FF3358");
    private static final int ORANGE = Color.parseColor("#FF9A2F");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Telemetry t = new Telemetry();
    private Health h = new Health();

    private TextView connection, bridgeIp, ps5Ip, selectedInfo;
    private TextView rpmValue, speedValue, gearValue, totalTimeValue, autonomyValue, fuelValue;
    private RpmGaugeView rpmGauge;
    private AccelChartView accelChart;
    private final ArrayList<MetricCard> cards = new ArrayList<>();

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
        scroll.setFillViewport(true);
        LinearLayout root = vBox();
        root.setPadding(dp(14), dp(12), dp(14), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root.addView(header());
        root.addView(statusStrip());
        root.addView(rpmPanel());
        root.addView(primaryCards());
        root.addView(configurableCards());
        root.addView(accelerationPanel());
        root.addView(buttonBar());

        setContentView(screen);
    }

    private View header() {
        LinearLayout row = hRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, 0);
        row.setLayoutParams(marginLp(-1, dp(54), 0, 0, 0, dp(12)));

        TextView menu = icon("☰", 28);
        menu.setOnClickListener(v -> showMenu());
        row.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView logo = text("GT", 32, TXT, true);
        logo.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(62), dp(42));
        glp.setMargins(dp(10), 0, 0, 0);
        row.addView(logo, glp);

        LinearLayout titleBox = vBox();
        TextView title = text("GT7 BRIDGE", 22, TXT, true);
        title.setLetterSpacing(.04f);
        titleBox.addView(title);
        TextView sub = text("APP PERFORMANCE DASHBOARD", 10, MUTED, true);
        sub.setLetterSpacing(.08f);
        titleBox.addView(sub);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1);
        tlp.setMargins(dp(8), 0, 0, 0);
        row.addView(titleBox, tlp);

        connection = text("●  CONECTANDO", 13, GREEN, true);
        connection.setGravity(Gravity.CENTER);
        connection.setBackground(round(Color.parseColor("#082A25"), 14, Color.parseColor("#124C45"), 1));
        row.addView(connection, new LinearLayout.LayoutParams(dp(132), dp(42)));

        TextView gear = icon("⚙", 24);
        gear.setOnClickListener(v -> editPs5Ip());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.setMargins(dp(8), 0, 0, 0);
        row.addView(gear, lp);
        return row;
    }

    private View statusStrip() {
        LinearLayout box = hRow();
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        box.setBackground(round(PANEL, 16, STROKE, 1));
        box.setLayoutParams(marginLp(-1, dp(82), 0, 0, 0, dp(12)));

        bridgeIp = infoBlock(box, "BRIDGE", BRIDGE_URL.replace("http://", ""));
        addDivider(box);
        ps5Ip = infoBlock(box, "PS5", getPs5Ip());
        addDivider(box);
        selectedInfo = infoBlock(box, "TELEMETRIA", "12/25 selecionados  ›");
        selectedInfo.setOnClickListener(v -> showFieldsInfo());
        box.setOnClickListener(v -> showFieldsInfo());
        return box;
    }

    private TextView infoBlock(LinearLayout parent, String label, String value) {
        LinearLayout b = vBox();
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.addView(text(label, 11, MUTED, false));
        TextView val = text(value, 15, CYAN, false);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.setMargins(0, dp(6), 0, 0);
        b.addView(val, vlp);
        parent.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
        return val;
    }

    private void addDivider(LinearLayout parent) {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#234965"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(1), -1);
        lp.setMargins(dp(10), 0, dp(16), 0);
        parent.addView(d, lp);
    }

    private View rpmPanel() {
        LinearLayout panel = vBox();
        panel.setPadding(dp(16), dp(14), dp(16), dp(10));
        panel.setBackground(round(PANEL, 16, STROKE, 1));
        panel.setLayoutParams(marginLp(-1, dp(270), 0, 0, 0, dp(12)));

        LinearLayout top = hRow();
        top.addView(text("CONTA GIROS", 18, TXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView selector = smallButton("RPM  ˅");
        selector.setOnClickListener(v -> Toast.makeText(this, "Conta giros fixo em RPM", Toast.LENGTH_SHORT).show());
        top.addView(selector, new LinearLayout.LayoutParams(dp(112), dp(38)));
        panel.addView(top);

        FrameLayout gaugeBox = new FrameLayout(this);
        rpmGauge = new RpmGaugeView(this);
        gaugeBox.addView(rpmGauge, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout center = vBox();
        center.setGravity(Gravity.CENTER);
        rpmValue = text("0", 40, TXT, true);
        rpmValue.setGravity(Gravity.CENTER);
        center.addView(rpmValue);
        TextView rpmLbl = text("RPM", 13, MUTED, true);
        rpmLbl.setGravity(Gravity.CENTER);
        center.addView(rpmLbl);
        speedValue = text("0 km/h", 18, CYAN, true);
        speedValue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(0, dp(10), 0, 0);
        center.addView(speedValue, slp);
        gaugeBox.addView(center, new FrameLayout.LayoutParams(-1, -1));

        panel.addView(gaugeBox, new LinearLayout.LayoutParams(-1, 0, 1));
        return panel;
    }

    private View primaryCards() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setLayoutParams(marginLp(-1, -2, 0, 0, 0, dp(4)));

        gearValue = quickCard(grid, "MARCHA", "N", "MARCHA", BLUE);
        fuelValue = quickCard(grid, "COMBUSTÍVEL", "-- L", "LITROS", CYAN);
        totalTimeValue = quickCard(grid, "TEMPO TOTAL", "--", "Soma das voltas certas", TXT);
        autonomyValue = quickCard(grid, "AUTONOMIA", "--", "Estimado pelo consumo atual", GREEN);
        return grid;
    }

    private TextView quickCard(GridLayout grid, String title, String value, String sub, int color) {
        LinearLayout c = cardBase();
        LinearLayout head = hRow();
        head.addView(text(title, 16, TXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(smallButton(sub.length() > 9 ? "INFO" : sub), new LinearLayout.LayoutParams(dp(86), dp(34)));
        c.addView(head);
        TextView val = text(value, 34, color, true);
        val.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, 0, 1);
        vlp.setMargins(0, dp(10), 0, 0);
        c.addView(val, vlp);
        TextView subT = text(sub, 13, MUTED, false);
        subT.setGravity(Gravity.CENTER);
        c.addView(subT);
        grid.addView(c, gridLp(dp(134)));
        return val;
    }

    private View configurableCards() {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        String[] defaults = new String[]{"velocidade", "melhorVolta", "ultimaVolta", "voltasCorrigidas", "freio", "acelerador", "codigoCarro", "paradasBoxes"};
        for (int i = 0; i < defaults.length; i++) {
            String metric = getPrefs().getString(KEY_CARD_PREFIX + i, defaults[i]);
            MetricCard card = new MetricCard(i, metric);
            cards.add(card);
            grid.addView(card.view, gridLp(dp(116)));
        }
        return grid;
    }

    private View accelerationPanel() {
        LinearLayout panel = vBox();
        panel.setPadding(dp(16), dp(12), dp(16), dp(16));
        panel.setBackground(round(PANEL, 16, STROKE, 1));
        panel.setLayoutParams(marginLp(-1, dp(260), 0, dp(8), 0, dp(12)));
        LinearLayout top = hRow();
        top.addView(text("ACELERAÇÃO", 17, TXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView selector = smallButton("G LONGITUDINAL ˅");
        selector.setOnClickListener(v -> Toast.makeText(this, "Gráfico usando acelerador x freio", Toast.LENGTH_SHORT).show());
        top.addView(selector, new LinearLayout.LayoutParams(dp(154), dp(38)));
        panel.addView(top);
        accelChart = new AccelChartView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        lp.setMargins(0, dp(8), 0, 0);
        panel.addView(accelChart, lp);
        return panel;
    }

    private View buttonBar() {
        LinearLayout row = hRow();
        row.setGravity(Gravity.CENTER);
        row.setLayoutParams(marginLp(-1, dp(54), 0, 0, 0, dp(8)));
        String[] labels = {"SALVAR", "HISTÓRICO", "DEBUG", "CANDIDATOS"};
        for (String l : labels) {
            TextView b = smallButton(l);
            b.setTextColor(TXT);
            b.setOnClickListener(v -> {
                String s = ((TextView)v).getText().toString();
                if (s.equals("SALVAR")) { saveSession("manual"); Toast.makeText(this, "Sessão salva", Toast.LENGTH_SHORT).show(); }
                else if (s.equals("HISTÓRICO")) showHistory();
                else if (s.equals("DEBUG")) fetchDialog("Debug", BRIDGE_URL + "/api/debug");
                else fetchDialog("Candidates", BRIDGE_URL + "/api/candidates");
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1);
            lp.setMargins(dp(3), 0, dp(3), 0);
            row.addView(b, lp);
        }
        return row;
    }

    private LinearLayout cardBase() {
        LinearLayout c = vBox();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(round(PANEL, 15, STROKE, 1));
        return c;
    }

    private GridLayout.LayoutParams gridLp(int h) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = (getResources().getDisplayMetrics().widthPixels - dp(36)) / 2;
        lp.height = h;
        lp.setMargins(0, 0, dp(8), dp(10));
        return lp;
    }

    private class MetricCard {
        int index;
        String metric;
        LinearLayout view;
        TextView title, value, selector;

        MetricCard(int index, String metric) {
            this.index = index;
            this.metric = metric;
            view = cardBase();
            LinearLayout head = hRow();
            title = text(labelFor(metric), 14, TXT, true);
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            selector = smallButton("TROCAR ˅");
            selector.setOnClickListener(v -> chooseMetric(this));
            head.addView(selector, new LinearLayout.LayoutParams(dp(92), dp(32)));
            view.addView(head);
            value = text("--", 27, CYAN, true);
            value.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, 0, 1);
            vlp.setMargins(0, dp(8), 0, 0);
            view.addView(value, vlp);
            TextView hint = text("Campo configurável", 11, MUTED, false);
            hint.setGravity(Gravity.CENTER);
            view.addView(hint);
            view.setOnClickListener(v -> chooseMetric(this));
        }

        void setMetric(String m) {
            metric = m;
            title.setText(labelFor(m));
            getPrefs().edit().putString(KEY_CARD_PREFIX + index, m).apply();
            update();
        }

        void update() { value.setText(valueFor(metric)); }
    }

    private void chooseMetric(MetricCard c) {
        final String[] keys = new String[]{"velocidade", "rpm", "marcha", "acelerador", "freio", "combustivel", "combustivelPct", "autonomia", "melhorVolta", "ultimaVolta", "tempoTotal", "voltasCorrigidas", "voltasBrutas", "codigoCarro", "turbo", "oilPressure", "paradasBoxes", "estadoCorrida"};
        String[] labels = new String[keys.length];
        for (int i = 0; i < keys.length; i++) labels[i] = labelFor(keys[i]);
        new AlertDialog.Builder(this)
                .setTitle("Escolher informação do card")
                .setItems(labels, (d, which) -> c.setMetric(keys[which]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String labelFor(String key) {
        switch (key) {
            case "velocidade": return "VELOCIDADE";
            case "rpm": return "RPM";
            case "marcha": return "MARCHA";
            case "acelerador": return "ACELERADOR";
            case "freio": return "FREIO";
            case "combustivel": return "COMBUSTÍVEL";
            case "combustivelPct": return "COMBUSTÍVEL %";
            case "autonomia": return "AUTONOMIA";
            case "melhorVolta": return "MELHOR VOLTA";
            case "ultimaVolta": return "ÚLTIMA VOLTA";
            case "tempoTotal": return "TEMPO TOTAL";
            case "voltasCorrigidas": return "VOLTAS CERTAS";
            case "voltasBrutas": return "VOLTAS BRUTAS";
            case "codigoCarro": return "CÓDIGO DO CARRO";
            case "turbo": return "TURBO";
            case "oilPressure": return "PRESSÃO DO ÓLEO";
            case "paradasBoxes": return "BOXES";
            case "estadoCorrida": return "CORRIDA";
            default: return key.toUpperCase(Locale.ROOT);
        }
    }

    private String valueFor(String key) {
        switch (key) {
            case "velocidade": return t.velocidade + " km/h";
            case "rpm": return t.rpm;
            case "marcha": return t.marcha;
            case "acelerador": return t.acelerador + "%";
            case "freio": return t.freio + "%";
            case "combustivel": return t.combustivel + " L";
            case "combustivelPct": return t.combustivelPct + "%";
            case "autonomia": return t.autonomia;
            case "melhorVolta": return t.melhorVolta;
            case "ultimaVolta": return t.ultimaVolta;
            case "tempoTotal": return t.tempoTotal;
            case "voltasCorrigidas": return t.voltasCorrigidas;
            case "voltasBrutas": return t.voltasBrutas;
            case "codigoCarro": return t.codigoCarro;
            case "turbo": return t.turbo;
            case "oilPressure": return t.oilPressure;
            case "paradasBoxes": return t.paradasBoxes;
            case "estadoCorrida": return t.estadoCorrida;
            default: return "--";
        }
    }

    private void showMenu() {
        String msg = "Bridge: " + BRIDGE_URL + "\nPS5: " + getPs5Ip() + "\nVersão: " + VERSION + "\n\nLayout novo aplicado: conta giros, cards configuráveis, aceleração em gráfico, tempo total por voltas certas e autonomia estimada.";
        new AlertDialog.Builder(this)
                .setTitle("GT7 Bridge Mobile")
                .setMessage(msg)
                .setPositiveButton("Editar IP PS5", (d, w) -> editPs5Ip())
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showFieldsInfo() {
        fetchDialog("Campos disponíveis", BRIDGE_URL + "/api/fields");
    }

    private void editPs5Ip() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setText(getPs5Ip());
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(18), dp(12), dp(18), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("Editar IP do PS5")
                .setMessage("Esse IP fica salvo no app. O bridge continua em " + BRIDGE_URL + ".")
                .setView(input)
                .setPositiveButton("Salvar", (d, w) -> {
                    String ip = input.getText().toString().trim();
                    if (ip.length() == 0) ip = DEFAULT_PS5_IP;
                    getPrefs().edit().putString(KEY_PS5_IP, ip).apply();
                    t.ps5Ip = ip;
                    ps5Ip.setText(ip);
                    Toast.makeText(this, "IP do PS5 salvo", Toast.LENGTH_SHORT).show();
                    fetchFields();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void fetchDialog(String title, String url) {
        fetchText(url, text -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text.length() > 3500 ? text.substring(0, 3500) + "..." : text)
                .setPositiveButton("OK", null)
                .show());
    }

    private void showHistory() {
        try {
            JSONArray arr = new JSONArray(getPrefs().getString(KEY_SESSIONS, "[]"));
            if (arr.length() == 0) { Toast.makeText(this, "Sem sessões salvas", Toast.LENGTH_SHORT).show(); return; }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(10, arr.length()); i++) {
                JSONObject o = arr.getJSONObject(i);
                sb.append(i + 1).append(". ").append(o.optString("dataFim", "--")).append("\n");
                sb.append("Voltas: ").append(o.optString("voltasCorrigidas", "0")).append(" | Melhor: ").append(o.optString("melhorVolta", "--")).append("\n");
                sb.append("Máxima: ").append(o.optString("velocidadeMaxima", "0")).append(" km/h | Carro: ").append(o.optString("codigoCarro", "--")).append("\n\n");
            }
            new AlertDialog.Builder(this).setTitle("Histórico").setMessage(sb.toString()).setPositiveButton("OK", null).show();
        } catch (Exception e) { Toast.makeText(this, "Erro ao abrir histórico", Toast.LENGTH_SHORT).show(); }
    }

    private void fetchFields() { fetchJson(BRIDGE_URL + "/api/fields", obj -> { t.fromJson(obj, getPs5Ip()); applyTelemetry(); }); }
    private void fetchHealth(boolean toast) { fetchJson(BRIDGE_URL + "/api/health", obj -> { h.fromJson(obj); if (toast) Toast.makeText(this, "Health atualizado", Toast.LENGTH_SHORT).show(); }); }

    private interface JsonCb { void ok(JSONObject obj) throws Exception; }
    private interface TextCb { void ok(String text); }

    private void fetchJson(String url, JsonCb cb) {
        new Thread(() -> {
            try {
                String s = httpGet(url);
                JSONObject obj = new JSONObject(s);
                handler.post(() -> { try { cb.ok(obj); } catch (Exception ignored) {} });
            } catch (Exception e) {
                handler.post(() -> { if (url.contains("/api/fields")) { t.offline(); applyTelemetry(); } });
            }
        }).start();
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
        connection.setText(t.connected ? (t.decodeOk ? "●  CONECTADO" : "●  ONLINE") : "●  OFFLINE");
        connection.setTextColor(t.connected ? GREEN : RED);
        connection.setBackground(round(t.connected ? Color.parseColor("#082A25") : Color.parseColor("#2A0B14"), 14, t.connected ? Color.parseColor("#124C45") : Color.parseColor("#6D2438"), 1));
        ps5Ip.setText(getPs5Ip());
        selectedInfo.setText((t.packetSize > 0 ? t.packetSize : 25) + " campos  ›");

        rpmValue.setText(t.rpm);
        speedValue.setText(t.velocidade + " km/h");
        gearValue.setText(t.marcha);
        fuelValue.setText(t.combustivel + " L");
        totalTimeValue.setText(t.tempoTotal);
        autonomyValue.setText(t.autonomia);
        rpmGauge.setValues(num(t.rpm), num(t.velocidade));
        accelChart.push((t.acelerador - t.freio) / 100f);
        for (MetricCard c : cards) c.update();
        updateSessionState();
    }

    private void updateSessionState() {
        long now = System.currentTimeMillis();
        boolean active = t.connected && (num(t.velocidade) > 5 || num(t.rpm) > 1200 || num(t.voltasCorrigidas) > 0);
        if (active) {
            lastActive = now;
            if (!sessionActive) {
                sessionActive = true; sessionSaved = false; lastLaps = num(t.voltasCorrigidas); lastLapChange = now;
            }
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
            o.put("ps5Ip", t.ps5Ip);
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

    private SharedPreferences getPrefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }
    private String getPs5Ip() { return getPrefs().getString(KEY_PS5_IP, DEFAULT_PS5_IP); }
    private int num(String s) { try { return Integer.parseInt(String.valueOf(s).replaceAll("[^0-9-]", "")); } catch (Exception e) { return 0; } }

    private LinearLayout vBox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout hRow() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String label, int size, int color, boolean bold) { TextView tv = new TextView(this); tv.setText(label); tv.setTextSize(size); tv.setTextColor(color); tv.setIncludeFontPadding(true); if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD); return tv; }
    private TextView icon(String label, int size) { TextView tv = text(label, size, TXT, true); tv.setGravity(Gravity.CENTER); tv.setBackground(round(PANEL, 12, STROKE, 1)); return tv; }
    private TextView smallButton(String label) { TextView tv = text(label, 12, MUTED, true); tv.setGravity(Gravity.CENTER); tv.setBackground(round(Color.parseColor("#07131F"), 9, STROKE, 1)); return tv; }
    private GradientDrawable round(int color, int radius, int stroke, int sw) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if (sw > 0) d.setStroke(dp(sw), stroke); return d; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams marginLp(int w, int h, int l, int t, int r, int b) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h); lp.setMargins(l, t, r, b); return lp; }

    static class Health {
        boolean ok; String status="--"; boolean connected;
        void fromJson(JSONObject o){ ok=o.optBoolean("ok", false); status=o.optString("status", "--"); connected=o.optBoolean("connected", false); }
    }

    static class Telemetry {
        boolean connected=false, decodeOk=false;
        long updatedAt=0;
        int packetSize=0, acelerador=0, freio=0;
        String packetVersion="?", ps5Ip="192.168.1.54", velocidade="0", velocidadeMaxima="0", rpm="0", marcha="N", combustivel="--", combustivelPct="--", melhorVolta="--", ultimaVolta="--", tempoTotal="--", voltasBrutas="0", voltasCorrigidas="0", estadoCorrida="AGUARDANDO", paradasBoxes="--", codigoCarro="--", turbo="--", oilPressure="--", autonomia="--";

        void fromJson(JSONObject j, String savedPs5Ip){
            connected=j.optBoolean("connected", false);
            decodeOk=j.optBoolean("decodeOk", false);
            updatedAt=j.optLong("updatedAt", System.currentTimeMillis());
            packetSize=j.optInt("packetSize", 0);
            packetVersion=j.optString("packetVersion", "?");
            ps5Ip=j.optString("ps5Ip", savedPs5Ip);
            velocidade=clean(first(j,"velocidade","speed","speed_kmh"), "0");
            velocidadeMaxima=clean(first(j,"velocidadeMaxima","maxSpeed","max_speed_kmh"), velocidade);
            rpm=clean(first(j,"rpm","engine_rpm"), "0");
            marcha=first(j,"marcha","gear","current_gear"); if (marcha.equals("--")) marcha = "N";
            acelerador=percent(j,"acelerador","throttle","throttle_percent","accelerator");
            freio=percent(j,"freio","brake","brake_percent");
            combustivel=clean(first(j,"combustivel","fuel_liters","fuelLiters","fuel"), "--");
            combustivelPct=clean(first(j,"combustivelPorcentagem","combustivelPct","fuel_percent","fuelPercent"), "--");
            melhorVolta=first(j,"melhorVolta","bestLap","best_lap","best_lap_time");
            ultimaVolta=first(j,"ultimaVolta","lastLap","last_lap","last_lap_time");
            voltasBrutas=clean(first(j,"voltasCompletadas","voltasBrutas","rawLaps","raw_laps"), "0");
            voltasCorrigidas=clean(first(j,"voltasCorrigidas","completed_laps","completedLaps","lap_count"), "0");
            paradasBoxes=clean(first(j,"paradasBoxes","pitStops","pit_stops"), "--");
            codigoCarro=first(j,"codigoCarro","carCode","carId","car_id","vehicleCode","car_code");
            turbo=first(j,"turbo","turbo_pressure");
            oilPressure=first(j,"oilPressure","oil_pressure","oil");
            tempoTotal=sumCorrectLaps(j);
            if (tempoTotal.equals("--")) tempoTotal=first(j,"tempoTotalCorrida","tempoTotal","totalTime","total_time");
            autonomia=calcAutonomy(j, combustivel, voltasCorrigidas);
            estadoCorrida = (num(velocidade) > 3 || num(rpm) > 1000 || num(voltasCorrigidas) > 0) ? "EM ANDAMENTO" : "AGUARDANDO";
        }

        void offline(){ connected=false; decodeOk=false; estadoCorrida="AGUARDANDO"; }

        static String sumCorrectLaps(JSONObject j) {
            JSONArray arr = firstArray(j, "voltasCertas", "correctLaps", "valid_laps", "lapTimes", "lap_times", "laps");
            if (arr == null || arr.length() == 0) return "--";
            long sum = 0; int ok = 0;
            for (int i=0; i<arr.length(); i++) {
                Object raw = arr.opt(i);
                String s;
                if (raw instanceof JSONObject) s = first((JSONObject)raw, "tempo", "time", "lapTime", "lap_time"); else s = String.valueOf(raw);
                long ms = parseLapMs(s);
                if (ms > 0) { sum += ms; ok++; }
            }
            return ok > 0 ? formatMs(sum) : "--";
        }

        static String calcAutonomy(JSONObject j, String fuel, String laps) {
            String direct = first(j, "autonomia", "fuelAutonomy", "fuel_autonomy", "estimatedLapsFuel");
            if (!direct.equals("--")) return direct;
            double fuelL = dbl(fuel);
            double used = dbl(first(j, "combustivelGasto", "fuelUsed", "fuel_used"));
            double completed = dbl(laps);
            if (fuelL > 0 && used > 0 && completed > 0) return String.format(Locale.US, "%.1f voltas", fuelL / (used / completed));
            double fuelPct = dbl(first(j,"combustivelPorcentagem","combustivelPct","fuel_percent","fuelPercent"));
            if (fuelPct > 0 && completed > 0 && fuelPct < 100) return String.format(Locale.US, "%.1f voltas", completed * fuelPct / Math.max(1, 100 - fuelPct));
            return "--";
        }

        static int percent(JSONObject j, String... keys) {
            String raw = first(j, keys);
            try {
                double v = Double.parseDouble(raw.replace("%","").replace(",",".").replaceAll("[^0-9.\\-]",""));
                if (v > 0 && v <= 1) v *= 100;
                if (v > 100 && v <= 255) v = v / 255.0 * 100.0;
                return Math.max(0, Math.min(100, (int)Math.round(v)));
            } catch(Exception e){ return 0; }
        }
        static int num(String s) { try { return Integer.parseInt(String.valueOf(s).replaceAll("[^0-9-]", "")); } catch (Exception e) { return 0; } }
        static double dbl(String s) { try { return Double.parseDouble(String.valueOf(s).replace(",", ".").replaceAll("[^0-9.\\-]", "")); } catch(Exception e){ return 0; } }
        static String clean(String v, String fallback){ if(v==null || v.equals("--") || v.length()==0) return fallback; if(v.endsWith(".0")) return v.substring(0,v.length()-2); return v; }
        static String first(JSONObject j, String... keys){ for(String k: keys){ if(j.has(k) && !j.isNull(k)) return String.valueOf(j.opt(k)); } return "--"; }
        static JSONArray firstArray(JSONObject j, String... keys){ for(String k: keys){ JSONArray a = j.optJSONArray(k); if(a!=null) return a; } return null; }
        static long parseLapMs(String s) {
            try {
                s = String.valueOf(s).trim().replace(",", ".");
                if (s.equals("--") || s.length() == 0) return 0;
                String[] p = s.split(":");
                if (p.length == 1) return Math.round(Double.parseDouble(p[0]) * 1000.0);
                if (p.length == 2) return Math.round((Double.parseDouble(p[0]) * 60.0 + Double.parseDouble(p[1])) * 1000.0);
                return Math.round((Double.parseDouble(p[0]) * 3600.0 + Double.parseDouble(p[1]) * 60.0 + Double.parseDouble(p[2])) * 1000.0);
            } catch(Exception e){ return 0; }
        }
        static String formatMs(long ms) {
            long total = ms / 1000; long milli = ms % 1000; long sec = total % 60; long min = (total / 60) % 60; long hr = total / 3600;
            if (hr > 0) return String.format(Locale.US, "%d:%02d:%02d.%03d", hr, min, sec, milli);
            return String.format(Locale.US, "%d:%02d.%03d", min, sec, milli);
        }
    }

    static class RpmGaugeView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int rpm = 0, speed = 0;
        RpmGaugeView(Context c) { super(c); text.setTypeface(Typeface.DEFAULT_BOLD); }
        void setValues(int r, int s) { rpm=r; speed=s; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w=getWidth(), h=getHeight(); float cx=w/2f, cy=h*.96f; float r=Math.min(w*.47f, h*.86f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.BUTT); p.setStrokeWidth(dpLocal(18));
            RectF arc = new RectF(cx-r, cy-r, cx+r, cy+r);
            p.setShader(new LinearGradient(cx-r, cy, cx+r, cy, new int[]{Color.parseColor("#009BFF"), Color.parseColor("#10E6FF"), Color.parseColor("#FF3358")}, null, Shader.TileMode.CLAMP));
            c.drawArc(arc, 200, 140, false, p); p.setShader(null);
            p.setStrokeWidth(dpLocal(3));
            for(int i=0;i<=60;i++){
                float a=(float)Math.toRadians(200 + 140*i/60f); float inner = r - (i%5==0?dpLocal(30):dpLocal(18));
                p.setColor(i>48?Color.parseColor("#FF3358"):Color.argb(i%5==0?240:150,255,255,255));
                c.drawLine(cx+(float)Math.cos(a)*inner, cy+(float)Math.sin(a)*inner, cx+(float)Math.cos(a)*(r-dpLocal(2)), cy+(float)Math.sin(a)*(r-dpLocal(2)), p);
            }
            text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER); text.setTextSize(dpLocal(17));
            for(int i=0;i<=12;i+=2){ float a=(float)Math.toRadians(200 + 140*i/12f); c.drawText(String.valueOf(i), cx+(float)Math.cos(a)*(r-dpLocal(54)), cy+(float)Math.sin(a)*(r-dpLocal(54))+dpLocal(6), text); }
            float pct=Math.max(0, Math.min(1, rpm/12000f)); float na=(float)Math.toRadians(200+140*pct);
            p.setStrokeWidth(dpLocal(5)); p.setColor(Color.parseColor("#FF3358"));
            c.drawLine(cx, cy, cx+(float)Math.cos(na)*(r-dpLocal(48)), cy+(float)Math.sin(na)*(r-dpLocal(48)), p);
            p.setStyle(Paint.Style.FILL); c.drawCircle(cx, cy, dpLocal(7), p);
            text.setTextSize(dpLocal(12)); text.setColor(Color.parseColor("#16E6FF")); c.drawText(speed + " km/h", cx, cy-r*.42f, text);
        }
        private int dpLocal(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    }

    static class AccelChartView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] vals = new float[90];
        AccelChartView(Context c) { super(c); }
        void push(float v) { System.arraycopy(vals, 1, vals, 0, vals.length-1); vals[vals.length-1] = Math.max(-1, Math.min(1, v)); invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); int w=getWidth(), h=getHeight(); float mid=h*.52f;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(Color.parseColor("#18344B"));
            for(int i=0;i<=6;i++){ float y=i*h/6f; c.drawLine(0,y,w,y,p); }
            for(int i=0;i<=12;i++){ float x=i*w/12f; c.drawLine(x,0,x,h,p); }
            p.setColor(Color.parseColor("#678099")); c.drawLine(0, mid, w, mid, p);
            float bw=Math.max(2, w/(float)vals.length*.72f);
            p.setStyle(Paint.Style.FILL);
            for(int i=0;i<vals.length;i++){
                float v=vals[i]; float x=i*w/(float)vals.length; float y=mid - v*(h*.42f);
                p.setColor(v>=0?Color.parseColor("#159BFF"):Color.parseColor("#FF3358"));
                c.drawRoundRect(x, Math.min(mid,y), x+bw, Math.max(mid,y), bw/2, bw/2, p);
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.parseColor("#406A8C"));
            c.drawRect(0,0,w-1,h-1,p);
        }
    }
}
