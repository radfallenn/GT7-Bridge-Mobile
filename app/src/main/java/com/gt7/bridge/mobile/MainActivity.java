package com.gt7.bridge.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String VERSION = "1.5.8";
    private static final String BASE_URL = "http://192.168.1.70:8787";
    private static final String FIELDS_URL = BASE_URL + "/api/fields";
    private static final String HEALTH_URL = BASE_URL + "/api/health";
    private static final String DEBUG_URL = BASE_URL + "/api/debug";
    private static final String CANDIDATES_URL = BASE_URL + "/api/candidates";
    private static final String PREF = "gt7_bridge_mobile";
    private static final String KEY_SESSIONS = "gt7_saved_sessions";

    private static final int BG = Color.parseColor("#030711");
    private static final int PANEL = Color.parseColor("#07111F");
    private static final int CARD = Color.parseColor("#0A1422");
    private static final int CARD_DARK = Color.parseColor("#050911");
    private static final int STROKE = Color.parseColor("#14263D");
    private static final int TXT = Color.parseColor("#F4F8FF");
    private static final int MUTED = Color.parseColor("#8792A3");
    private static final int BLUE = Color.parseColor("#5EA2FF");
    private static final int CYAN = Color.parseColor("#19E6FF");
    private static final int GREEN = Color.parseColor("#00EFA2");
    private static final int YELLOW = Color.parseColor("#FFD35A");
    private static final int PURPLE = Color.parseColor("#A86BFF");
    private static final int RED = Color.parseColor("#FF5C7D");
    private static final int ORANGE = Color.parseColor("#FFAC35");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<CardRef> cards = new ArrayList<>();
    private final ArrayList<ProgressRef> progressCards = new ArrayList<>();
    private Telemetry t = new Telemetry();
    private Health h = new Health();

    private TextView statusBadge, miniSpeed, miniRpm, miniGear, ps5Value, syncValue, packetValue, healthLine;
    private TextView debugText, candidatesText, historyText;
    private LinearLayout debugPanel, historyPanel;
    private boolean detailsOpen = false;
    private boolean historyOpen = false;

    private long sessionStartAt = 0;
    private boolean sessionActive = false;
    private boolean sessionSaved = false;
    private int lastLaps = 0;
    private long lastLapChange = 0;
    private long lastActive = 0;

    private final String[][] options = new String[][]{
            {"Velocidade", "velocidade"},
            {"Velocidade Máxima", "velocidadeMaxima"},
            {"RPM", "rpm"},
            {"Marcha", "marcha"},
            {"Acelerador", "acelerador"},
            {"Freio", "freio"},
            {"Combustível", "combustivel"},
            {"Combustível %", "combustivelPct"},
            {"Melhor Volta", "melhorVolta"},
            {"Última Volta", "ultimaVolta"},
            {"Tempo Total", "tempoTotal"},
            {"Voltas Brutas", "voltasBrutas"},
            {"Voltas Corrigidas", "voltasCorrigidas"},
            {"Estado da Corrida", "estadoCorrida"},
            {"Paradas Boxes", "paradasBoxes"},
            {"Pressão do Turbo", "turbo"},
            {"Pressão do Óleo", "oilPressure"},
            {"Vetores Velocidade", "speedVector"},
            {"Rotação Pitch/Roll/Yaw", "rotation"},
            {"Velocidade Angular", "angularVelocity"},
            {"Coordenadas X/Y/Z", "position"},
            {"PS5 IP", "ps5Ip"},
            {"Pacote UDP", "packet"},
            {"Status Bridge", "bridgeStatus"}
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            fetchFields();
            fetchHealth(false);
            handler.postDelayed(this, 800);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadHistory();
        handler.post(tick);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout shell = box(PANEL, 30, STROKE, 1);
        shell.setPadding(dp(16), dp(16), dp(16), dp(18));
        root.addView(shell);

        shell.addView(titleBar());
        shell.addView(statusPanel());
        shell.addView(section("TELEMETRIA AO VIVO", BLUE));
        shell.addView(liveGrid());
        shell.addView(section("VOLTAS E RESULTADOS", GREEN));
        shell.addView(resultsGrid());
        shell.addView(section("MOTOR & DINÂMICA DO VEÍCULO", PURPLE));
        shell.addView(vehicleGrid());
        shell.addView(section("COORDENADAS E MAPA DO CIRCUITO", PURPLE));
        shell.addView(singleSelectable("Coordenadas X/Y/Z", "position", CYAN));
        shell.addView(detailsToggle());
        shell.addView(debugPanel());
        shell.addView(historyToggle());
        shell.addView(historyPanel());
    }

    private View titleBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = hRow();
        View bar = new View(this);
        bar.setBackground(round(BLUE, 8, BLUE, 0));
        row.addView(bar, new LinearLayout.LayoutParams(dp(6), dp(26)));
        TextView title = text("DASHBOARD TELEMETRIA", 18, BLUE, true);
        title.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMargins(dp(10), 0, 0, 0);
        row.addView(title, tlp);
        wrap.addView(row);
        TextView hud = text("HUD TELEMETRIA  •  v" + VERSION, 12, Color.parseColor("#08111A"), true);
        hud.setGravity(Gravity.CENTER);
        hud.setPadding(dp(18), dp(12), dp(18), dp(12));
        hud.setBackground(round(BLUE, 18, BLUE, 0));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(14), 0, dp(14));
        wrap.addView(hud, hp);
        return wrap;
    }

    private View statusPanel() {
        LinearLayout card = box(CARD_DARK, 24, STROKE, 1);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.setMargins(0, dp(4), 0, dp(16));
        card.setLayoutParams(clp);

        LinearLayout row = hRow();
        statusBadge = pill("OFFLINE /\nDESCONECTADO", RED, Color.parseColor("#3A0C1B"));
        statusBadge.setGravity(Gravity.CENTER);
        row.addView(statusBadge, new LinearLayout.LayoutParams(0, dp(72), 1));
        row.addView(space(10, 1));
        LinearLayout mini = box(Color.parseColor("#03070E"), 18, Color.parseColor("#0E1828"), 1);
        mini.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.addView(mini, new LinearLayout.LayoutParams(0, dp(72), 1));
        LinearLayout mr = hRow();
        mini.addView(mr);
        mr.addView(text("VOLANTE:", 9, MUTED, true));
        mr.addView(space(5, 1));
        miniSpeed = text("0 KM/H", 11, TXT, true);
        mr.addView(miniSpeed);
        mr.addView(space(10, 1));
        mr.addView(text("RPM:", 9, MUTED, true));
        mr.addView(space(5, 1));
        miniRpm = text("0", 11, YELLOW, true);
        mr.addView(miniRpm);
        mr.addView(space(10, 1));
        mr.addView(text("M:", 9, MUTED, true));
        mr.addView(space(5, 1));
        miniGear = text("N", 11, GREEN, true);
        mr.addView(miniGear);
        card.addView(row);

        LinearLayout ip = box(Color.parseColor("#050912"), 16, Color.parseColor("#0F1928"), 1);
        ip.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-1, -2);
        ilp.setMargins(0, dp(12), 0, dp(12));
        ip.setLayoutParams(ilp);
        LinearLayout ipr = hRow();
        ip.addView(ipr);
        ipr.addView(text("PS5 IP:", 10, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        ps5Value = text("192.168.1.54", 11, YELLOW, true);
        ps5Value.setGravity(Gravity.RIGHT);
        ipr.addView(ps5Value, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(ip);

        syncValue = addInfo(card, "Sincronização:", "--:--:--", TXT);
        packetValue = addInfo(card, "Pacote UDP:", "--", BLUE);
        healthLine = addInfo(card, "Bridge /api/health:", "aguardando", CYAN);

        LinearLayout actions = hRow();
        actions.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.setMargins(0, dp(16), 0, 0);
        actions.setLayoutParams(alp);
        TextView save = smallPill("▣ SALVAR SESSÃO", YELLOW, Color.parseColor("#2E2511"));
        save.setOnClickListener(v -> saveSession("manual"));
        actions.addView(save);
        actions.addView(space(10, 1));
        TextView debug = smallPill("⟳ ATUALIZAR DEBUG", BLUE, Color.parseColor("#11223E"));
        debug.setOnClickListener(v -> { fetchDebug(true); fetchCandidates(true); fetchHealth(true); });
        actions.addView(debug);
        card.addView(actions);
        return card;
    }

    private TextView addInfo(LinearLayout card, String label, String value, int color) {
        LinearLayout r = hRow();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, 0);
        r.setLayoutParams(lp);
        r.addView(text(label, 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(value, 12, color, true);
        v.setGravity(Gravity.RIGHT);
        r.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(r);
        return v;
    }

    private View liveGrid() {
        LinearLayout wrap = vBox();
        rowSelectable(wrap, selectable("Velocidade", "velocidade", TXT), selectable("Velocidade Máxima", "velocidadeMaxima", YELLOW));
        rowSelectable(wrap, selectable("RPM", "rpm", TXT), selectable("Marcha", "marcha", GREEN));
        wrap.addView(progressCard("Acelerador", "acelerador", GREEN));
        wrap.addView(progressCard("Freio", "freio", RED));
        rowSelectable(wrap, selectable("Combustível", "combustivel", TXT), selectable("Combustível %", "combustivelPct", CYAN));
        return wrap;
    }

    private View resultsGrid() {
        LinearLayout wrap = vBox();
        rowSelectable(wrap, selectable("Melhor Volta", "melhorVolta", PURPLE), selectable("Última Volta", "ultimaVolta", TXT));
        rowSelectable(wrap, selectable("Tempo Total", "tempoTotal", CYAN), selectable("Voltas Brutas", "voltasBrutas", TXT));
        rowSelectable(wrap, selectable("Voltas Corrigidas", "voltasCorrigidas", BLUE), selectable("Estado da Corrida", "estadoCorrida", BLUE));
        rowSelectable(wrap, selectable("Paradas Boxes", "paradasBoxes", TXT), selectable("Velocidade Máxima", "velocidadeMaxima", YELLOW));
        return wrap;
    }

    private View vehicleGrid() {
        LinearLayout wrap = vBox();
        rowSelectable(wrap, selectable("Pressão do Turbo", "turbo", PURPLE), selectable("Pressão do Óleo", "oilPressure", ORANGE));
        rowSelectable(wrap, selectable("Vetores Velocidade", "speedVector", GREEN), selectable("Rotação Pitch/Roll/Yaw", "rotation", PURPLE));
        wrap.addView(singleSelectable("Velocidade Angular", "angularVelocity", CYAN));
        return wrap;
    }

    private void rowSelectable(LinearLayout wrap, TextView a, TextView b) {
        LinearLayout row = hRow();
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.setMargins(0, dp(10), 0, 0);
        row.setLayoutParams(rlp);
        row.addView(a, new LinearLayout.LayoutParams(0, dp(118), 1));
        row.addView(space(10, 1));
        row.addView(b, new LinearLayout.LayoutParams(0, dp(118), 1));
        wrap.addView(row);
    }

    private TextView selectable(String title, String key, int color) {
        TextView tv = cardView(title, valueFor(key), color);
        CardRef ref = new CardRef(tv, title, key, color);
        cards.add(ref);
        tv.setOnClickListener(v -> showCardMenu(ref));
        return tv;
    }

    private View singleSelectable(String title, String key, int color) {
        TextView tv = selectable(title, key, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(104));
        lp.setMargins(0, dp(10), 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView cardView(String title, String value, int color) {
        TextView tv = text(formatCard(title, value), 13, color, true);
        tv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(16), dp(12), dp(16), dp(12));
        tv.setBackground(round(CARD, 22, STROKE, 1));
        return tv;
    }

    private String formatCard(String title, String value) {
        return title.toUpperCase(Locale.ROOT) + "        ✓\n\n" + value;
    }

    private View progressCard(String title, String key, int color) {
        LinearLayout c = box(CARD, 22, STROKE, 1);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(112));
        lp.setMargins(0, dp(10), 0, 0);
        c.setLayoutParams(lp);
        LinearLayout head = hRow();
        TextView titleView = text(title.toUpperCase(Locale.ROOT) + "        ✓", 11, MUTED, true);
        head.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView pct = text("0%", 13, color, true);
        head.addView(pct);
        c.addView(head);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.getProgressDrawable().setTint(color);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(26));
        blp.setMargins(0, dp(20), 0, 0);
        c.addView(bar, blp);
        ProgressRef ref = new ProgressRef(c, titleView, pct, bar, title, key, color);
        progressCards.add(ref);
        c.setOnClickListener(v -> showProgressMenu(ref));
        titleView.setOnClickListener(v -> showProgressMenu(ref));
        return c;
    }

    private void showCardMenu(CardRef ref) {
        PopupMenu menu = new PopupMenu(this, ref.view);
        for (String[] item : options) menu.getMenu().add(item[0]);
        menu.setOnMenuItemClickListener(item -> {
            String label = String.valueOf(item.getTitle());
            ref.title = label;
            ref.key = keyFromLabel(label);
            ref.color = colorForKey(ref.key);
            refreshSelectableCards();
            return true;
        });
        menu.show();
    }

    private void showProgressMenu(ProgressRef ref) {
        PopupMenu menu = new PopupMenu(this, ref.container);
        for (String[] item : options) menu.getMenu().add(item[0]);
        menu.setOnMenuItemClickListener(item -> {
            String label = String.valueOf(item.getTitle());
            ref.title = label;
            ref.key = keyFromLabel(label);
            ref.color = colorForKey(ref.key);
            ref.bar.getProgressDrawable().setTint(ref.color);
            refreshSelectableCards();
            return true;
        });
        menu.show();
    }

    private String keyFromLabel(String label) {
        for (String[] item : options) if (item[0].equals(label)) return item[1];
        return "velocidade";
    }

    private int colorForKey(String key) {
        if (key.contains("Maxima") || key.equals("packet")) return YELLOW;
        if (key.equals("marcha") || key.equals("acelerador") || key.equals("speedVector")) return GREEN;
        if (key.equals("freio")) return RED;
        if (key.equals("combustivelPct") || key.equals("tempoTotal") || key.equals("angularVelocity") || key.equals("position")) return CYAN;
        if (key.equals("melhorVolta") || key.equals("turbo") || key.equals("rotation")) return PURPLE;
        if (key.equals("oilPressure")) return ORANGE;
        if (key.equals("estadoCorrida") || key.equals("voltasCorrigidas") || key.equals("bridgeStatus")) return BLUE;
        return TXT;
    }

    private View detailsToggle() {
        TextView tt = toggle("▣  MOSTRAR DETALHES DO BRIDGE      ▼", YELLOW);
        tt.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            debugPanel.setVisibility(detailsOpen ? View.VISIBLE : View.GONE);
            tt.setText(detailsOpen ? "▣  RECOLHER DETALHES DO BRIDGE      ▲" : "▣  MOSTRAR DETALHES DO BRIDGE      ▼");
        });
        return tt;
    }

    private View debugPanel() {
        debugPanel = box(CARD_DARK, 20, STROKE, 1);
        debugPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        debugPanel.setVisibility(View.GONE);
        TextView b1 = smallPill("ATUALIZAR /api/debug", BLUE, Color.parseColor("#11223E"));
        b1.setOnClickListener(v -> fetchDebug(true));
        debugPanel.addView(b1);
        debugText = text("Debug ainda não carregado.", 12, TXT, false);
        debugText.setPadding(0, dp(12), 0, dp(12));
        debugPanel.addView(debugText);
        TextView b2 = smallPill("ATUALIZAR /api/candidates", GREEN, Color.parseColor("#06271E"));
        b2.setOnClickListener(v -> fetchCandidates(true));
        debugPanel.addView(b2);
        candidatesText = text("Candidatos ainda não carregados.", 12, TXT, false);
        candidatesText.setPadding(0, dp(12), 0, 0);
        debugPanel.addView(candidatesText);
        return debugPanel;
    }

    private View historyToggle() {
        TextView tt = toggle("▣  MOSTRAR HISTÓRICO DE SESSÕES      ▼", GREEN);
        tt.setOnClickListener(v -> {
            historyOpen = !historyOpen;
            historyPanel.setVisibility(historyOpen ? View.VISIBLE : View.GONE);
            tt.setText(historyOpen ? "▣  RECOLHER HISTÓRICO DE SESSÕES      ▲" : "▣  MOSTRAR HISTÓRICO DE SESSÕES      ▼");
        });
        return tt;
    }

    private View historyPanel() {
        historyPanel = box(CARD_DARK, 20, STROKE, 1);
        historyPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        historyPanel.setVisibility(View.GONE);
        historyText = text("Nenhuma sessão salva ainda.", 12, TXT, false);
        historyPanel.addView(historyText);
        return historyPanel;
    }

    private void fetchFields() { fetchJson(FIELDS_URL, obj -> { t.fromJson(obj); applyTelemetry(); }); }
    private void fetchHealth(boolean toast) { fetchJson(HEALTH_URL, obj -> { h.fromJson(obj); applyHealth(); if (toast) Toast.makeText(this, "Health atualizado", Toast.LENGTH_SHORT).show(); }); }
    private void fetchDebug(boolean toast) { fetchJson(DEBUG_URL, obj -> { applyDebug(obj); if (toast) Toast.makeText(this, "Debug atualizado", Toast.LENGTH_SHORT).show(); }); }
    private void fetchCandidates(boolean toast) { fetchJson(CANDIDATES_URL, obj -> { applyCandidates(obj); if (toast) Toast.makeText(this, "Candidatos atualizados", Toast.LENGTH_SHORT).show(); }); }

    private interface JsonCb { void ok(JSONObject obj) throws Exception; }

    private void fetchJson(String url, JsonCb cb) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(1200);
                conn.setReadTimeout(1200);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject obj = new JSONObject(sb.toString());
                handler.post(() -> { try { cb.ok(obj); } catch (Exception ignored) {} });
            } catch (Exception e) {
                handler.post(() -> {
                    if (url.equals(FIELDS_URL)) { t.offline(); applyTelemetry(); }
                    if (url.equals(HEALTH_URL)) { h.ok = false; applyHealth(); }
                });
            }
        }).start();
    }

    private void applyTelemetry() {
        statusBadge.setText(t.connected ? (t.decodeOk ? "CONECTADO\nAO PS5" : "ONLINE /\nPACOTE") : "OFFLINE /\nDESCONECTADO");
        statusBadge.setTextColor(t.connected ? (t.decodeOk ? GREEN : YELLOW) : RED);
        statusBadge.setBackground(round(t.connected ? Color.parseColor("#0A3022") : Color.parseColor("#3A0C1B"), 18, t.connected ? Color.parseColor("#145437") : Color.parseColor("#572234"), 1));
        miniSpeed.setText(t.velocidade + " KM/H");
        miniRpm.setText(t.rpm);
        miniGear.setText(t.marcha);
        ps5Value.setText(t.ps5Ip);
        syncValue.setText(t.timeText());
        packetValue.setText(t.packetVersion + " (" + t.packetSize + " bytes)");
        refreshSelectableCards();
        updateSessionState();
    }

    private void refreshSelectableCards() {
        for (CardRef ref : cards) {
            ref.view.setText(formatCard(ref.title, valueFor(ref.key)));
            ref.view.setTextColor(ref.color);
        }
        for (ProgressRef ref : progressCards) {
            ref.titleView.setText(ref.title.toUpperCase(Locale.ROOT) + "        ✓");
            ref.valueView.setText(valueFor(ref.key));
            ref.valueView.setTextColor(ref.color);
            ref.bar.setProgress(percentFor(ref.key));
        }
    }

    private String valueFor(String key) {
        if (key.equals("velocidade")) return t.velocidade + " km/h";
        if (key.equals("velocidadeMaxima")) return t.velocidadeMaxima + " km/h";
        if (key.equals("rpm")) return t.rpm;
        if (key.equals("marcha")) return t.marcha;
        if (key.equals("acelerador")) return t.acelerador + "%";
        if (key.equals("freio")) return t.freio + "%";
        if (key.equals("combustivel")) return t.combustivel + " L";
        if (key.equals("combustivelPct")) return t.combustivelPct + " %";
        if (key.equals("melhorVolta")) return t.melhorVolta;
        if (key.equals("ultimaVolta")) return t.ultimaVolta;
        if (key.equals("tempoTotal")) return t.tempoTotal;
        if (key.equals("voltasBrutas")) return t.voltasBrutas;
        if (key.equals("voltasCorrigidas")) return t.voltasCorrigidas;
        if (key.equals("estadoCorrida")) return t.estadoCorrida;
        if (key.equals("paradasBoxes")) return t.paradasBoxes;
        if (key.equals("turbo")) return t.turbo;
        if (key.equals("oilPressure")) return t.oilPressure;
        if (key.equals("speedVector")) return t.speedVector;
        if (key.equals("rotation")) return t.rotation;
        if (key.equals("angularVelocity")) return t.angularVelocity;
        if (key.equals("position")) return t.position;
        if (key.equals("ps5Ip")) return t.ps5Ip;
        if (key.equals("packet")) return t.packetVersion + " / " + t.packetSize + " bytes";
        if (key.equals("bridgeStatus")) return h.status;
        return "--";
    }

    private int percentFor(String key) {
        if (key.equals("acelerador")) return t.acelerador;
        if (key.equals("freio")) return t.freio;
        if (key.equals("combustivelPct")) return num(t.combustivelPct);
        return Math.min(100, Math.max(0, num(valueFor(key))));
    }

    private void applyHealth() {
        healthLine.setText(h.ok ? h.status + " • " + (h.connected ? "online" : "sem pacotes") : "offline");
    }

    private void applyDebug(JSONObject obj) {
        JSONObject k = obj.optJSONObject("knownOffsets");
        StringBuilder sb = new StringBuilder();
        sb.append("/api/debug OK: ").append(obj.optBoolean("ok", false)).append("\n");
        sb.append("Pacote: ").append(obj.optString("packetVersion", "?")).append(" / ").append(obj.optInt("packetSize", 0)).append(" bytes\n\n");
        if (k != null) {
            JSONArray names = k.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i);
                sb.append(key).append(" → ").append(k.optString(key)).append("\n");
            }
        }
        debugText.setText(sb.toString());
    }

    private void applyCandidates(JSONObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append("/api/candidates OK: ").append(obj.optBoolean("ok", false)).append("\n");
        JSONObject values = obj.optJSONObject("values");
        if (values != null) {
            JSONArray names = values.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i);
                sb.append(key).append(": ").append(values.opt(key)).append("\n");
            }
        } else {
            JSONObject cand = obj.optJSONObject("candidates");
            if (cand != null) sb.append(cand.toString()); else sb.append(obj.toString());
        }
        candidatesText.setText(sb.toString());
    }

    private void updateSessionState() {
        long now = System.currentTimeMillis();
        boolean active = t.connected && (num(t.velocidade) > 5 || num(t.rpm) > 1200 || num(t.voltasCorrigidas) > 0);
        if (active) {
            lastActive = now;
            if (!sessionActive) {
                sessionActive = true;
                sessionSaved = false;
                sessionStartAt = now;
                lastLaps = num(t.voltasCorrigidas);
                lastLapChange = now;
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
            loadHistory();
            copy(o.toString());
            Toast.makeText(this, type.equals("manual") ? "Sessão salva manualmente" : "Sessão salva automaticamente", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar sessão", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadHistory() {
        if (historyText == null) return;
        try {
            JSONArray arr = new JSONArray(getPrefs().getString(KEY_SESSIONS, "[]"));
            if (arr.length() == 0) { historyText.setText("Nenhuma sessão salva ainda."); return; }
            StringBuilder sb = new StringBuilder();
            int max = Math.min(12, arr.length());
            for (int i = 0; i < max; i++) {
                JSONObject o = arr.getJSONObject(i);
                sb.append(o.optString("tipoSalvamento", "manual").toUpperCase(Locale.ROOT)).append(" • ").append(o.optString("dataFim", "--"))
                        .append("\nMelhor: ").append(o.optString("melhorVolta", "--")).append("  Total: ").append(o.optString("tempoTotal", "--")).append("\n\n");
            }
            historyText.setText(sb.toString());
        } catch (Exception e) { historyText.setText("Histórico indisponível."); }
    }

    private SharedPreferences getPrefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }
    private void copy(String s) { ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("GT7", s)); }
    private int num(String s) { try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); } catch (Exception e) { return 0; } }
    private LinearLayout vBox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout hRow() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private Space space(int w, int h) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(w), dp(h))); return s; }
    private TextView section(String label, int color) { TextView tv = text("▌ " + label, 15, color, true); tv.setLetterSpacing(0.05f); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(14), 0, dp(4)); tv.setLayoutParams(lp); return tv; }
    private TextView toggle(String label, int color) { TextView tv = text(label, 14, color, true); tv.setPadding(dp(18), dp(16), dp(18), dp(16)); tv.setBackground(round(Color.parseColor("#33343A"), 22, Color.parseColor("#46464F"), 1)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, dp(10)); tv.setLayoutParams(lp); return tv; }
    private TextView pill(String label, int textColor, int bgColor) { TextView tv = text(label, 12, textColor, true); tv.setPadding(dp(14), dp(12), dp(14), dp(12)); tv.setBackground(round(bgColor, 18, lighten(bgColor), 1)); return tv; }
    private TextView smallPill(String label, int textColor, int bgColor) { TextView tv = text(label, 12, textColor, true); tv.setPadding(dp(16), dp(10), dp(16), dp(10)); tv.setBackground(round(bgColor, 18, lighten(bgColor), 1)); return tv; }
    private LinearLayout box(int color, int radius, int stroke, int sw) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(round(color, radius, stroke, sw)); return l; }
    private TextView text(String label, int size, int color, boolean bold) { TextView tv = new TextView(this); tv.setText(label); tv.setTextSize(size); tv.setTextColor(color); if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD); return tv; }
    private GradientDrawable round(int color, int radius, int stroke, int sw) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if (sw > 0) d.setStroke(dp(sw), stroke); return d; }
    private int lighten(int c) { return Color.rgb(Math.min(255, (int)(Color.red(c) * 1.25)), Math.min(255, (int)(Color.green(c) * 1.25)), Math.min(255, (int)(Color.blue(c) * 1.25))); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    static class CardRef { TextView view; String title; String key; int color; CardRef(TextView v, String t, String k, int c) { view = v; title = t; key = k; color = c; } }
    static class ProgressRef { LinearLayout container; TextView titleView; TextView valueView; ProgressBar bar; String title; String key; int color; ProgressRef(LinearLayout c, TextView tv, TextView vv, ProgressBar b, String t, String k, int col) { container = c; titleView = tv; valueView = vv; bar = b; title = t; key = k; color = col; } }
    static class Health { boolean ok; String status="--"; boolean connected; void fromJson(JSONObject o){ ok=o.optBoolean("ok", false); status=o.optString("status", "--"); connected=o.optBoolean("connected", false); } }

    static class Telemetry {
        boolean connected=false, decodeOk=false; long updatedAt=0; int packetSize=0, acelerador=0, freio=0; String packetVersion="?", ps5Ip="192.168.1.54", velocidade="0", velocidadeMaxima="0", rpm="0", marcha="N", combustivel="--", combustivelPct="--", melhorVolta="--", ultimaVolta="--", tempoTotal="--", voltasBrutas="0", voltasCorrigidas="0", estadoCorrida="EM ANDAMENTO", paradasBoxes="0", turbo="--", oilPressure="--", speedVector="--", rotation="--", angularVelocity="--", position="--";
        void fromJson(JSONObject j){ connected=j.optBoolean("connected", false); decodeOk=j.optBoolean("decodeOk", false); updatedAt=j.optLong("updatedAt", System.currentTimeMillis()); packetSize=j.optInt("packetSize", 0); packetVersion=j.optString("packetVersion", "?"); ps5Ip=j.optString("ps5Ip", ps5Ip); velocidade=s(j,"velocidade"); velocidadeMaxima=s(j,"velocidadeMaxima"); rpm=s(j,"rpm"); marcha=j.optString("marcha", "N"); acelerador=j.optInt("acelerador",0); freio=j.optInt("freio",0); combustivel=s(j,"combustivel"); combustivelPct=s(j,"combustivelPorcentagem"); melhorVolta=j.optString("melhorVolta","--"); ultimaVolta=j.optString("ultimaVolta","--"); tempoTotal=j.optString("tempoTotalCorrida", j.optString("tempoTotal", "--")); voltasBrutas=s(j,"voltasCompletadas"); voltasCorrigidas=s(j,"voltasCorrigidas"); paradasBoxes=s(j,"paradasBoxes"); turbo=s(j,"turbo"); oilPressure=s(j,"oilPressure"); speedVector=obj(j.optJSONObject("speedVector")); rotation=obj(j.optJSONObject("rotation")); angularVelocity=obj(j.optJSONObject("angularVelocity")); position=obj(j.optJSONObject("position")); }
        void offline(){ connected=false; decodeOk=false; }
        String timeText(){ return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(updatedAt==0?System.currentTimeMillis():updatedAt)); }
        static String s(JSONObject j,String k){ if(!j.has(k)||j.isNull(k)) return "--"; return String.valueOf(j.opt(k)); }
        static String obj(JSONObject o){ if(o==null) return "--"; StringBuilder sb=new StringBuilder(); JSONArray n=o.names(); if(n==null) return "--"; for(int i=0;i<n.length();i++){ String k=n.optString(i); if(i>0) sb.append("  "); sb.append(k).append(": ").append(o.opt(k)); } return sb.toString(); }
    }
}
