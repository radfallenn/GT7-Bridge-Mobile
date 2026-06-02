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
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String VERSION = "1.5.6";
    private static final String DATA_URL = "http://192.168.1.70:8787/api/fields";
    private static final String PREF = "gt7_bridge";
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
    private final Telemetry telemetry = new Telemetry();
    private final ArrayList<CardHolder> cards = new ArrayList<>();
    private final HashMap<String, TextView> detailViews = new HashMap<>();
    private LinearLayout detailsPanel;
    private LinearLayout historyPanel;
    private TextView statusBadge, miniSpeed, miniRpm, miniGear, syncValue, packetValue, ps5Value, detailsToggle, historyToggle;
    private boolean detailsOpen = false;
    private boolean historyOpen = false;
    private boolean sessionActive = false;
    private boolean sessionSaved = false;
    private long sessionStartAt = 0;
    private int lastCorrectedLaps = 0;
    private long lastLapChangeAt = 0;
    private long lastActiveAt = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            fetchTelemetry();
            handler.postDelayed(this, 700);
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
        shell.addView(liveCards());
        shell.addView(section("VOLTAS E RESULTADOS", GREEN));
        shell.addView(resultCards());
        shell.addView(section("MOTOR & DINÂMICA DO VEÍCULO", PURPLE));
        shell.addView(vehicleCards());
        shell.addView(mapRow());
        shell.addView(divider());
        shell.addView(detailsToggle());
        shell.addView(details());
        shell.addView(historyToggle());
        shell.addView(history());
    }

    private View titleBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = hRow();
        View bar = new View(this);
        bar.setBackground(round(BLUE, 8, BLUE, 0));
        row.addView(bar, new LinearLayout.LayoutParams(dp(6), dp(24)));
        TextView title = text("DASHBOARD TELEMETRIA", 18, BLUE, true);
        title.setLetterSpacing(0.07f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(10), 0, 0, 0);
        row.addView(title, lp);
        wrap.addView(row);
        TextView hud = text("HUD TELEMETRIA", 12, Color.parseColor("#08111A"), true);
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
        mr.addView(space(12, 1));
        mr.addView(text("RPM:", 9, MUTED, true));
        mr.addView(space(5, 1));
        miniRpm = text("0", 11, YELLOW, true);
        mr.addView(miniRpm);
        mr.addView(space(12, 1));
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
        card.addView(divider());
        card.addView(infoLine("Sincronização:", true));
        card.addView(infoLine("Pacote UDP:", false));

        LinearLayout actions1 = hRow();
        actions1.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(-1, -2);
        a1.setMargins(0, dp(18), 0, 0);
        actions1.setLayoutParams(a1);
        actions1.addView(smallPill("● GRAVANDO", GREEN, Color.parseColor("#06271E")));
        actions1.addView(space(10, 1));
        TextView save = smallPill("▣ SALVAR SESSÃO", YELLOW, Color.parseColor("#2E2511"));
        save.setOnClickListener(v -> saveSession("manual"));
        actions1.addView(save);
        card.addView(actions1);

        LinearLayout actions2 = hRow();
        actions2.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(-1, -2);
        a2.setMargins(0, dp(10), 0, 0);
        actions2.setLayoutParams(a2);
        actions2.addView(space(1, 1), new LinearLayout.LayoutParams(0, -2, 1));
        TextView fs = smallPill("⛶ FULLSCREEN HUD", BLUE, Color.parseColor("#11223E"));
        fs.setOnClickListener(v -> getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY));
        actions2.addView(fs);
        card.addView(actions2);
        return card;
    }

    private View infoLine(String label, boolean sync) {
        LinearLayout r = hRow();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, 0);
        r.setLayoutParams(lp);
        r.addView(text(label, 12, MUTED, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(sync ? "--:--:--" : "v1.4 (368 bytes)", 12, sync ? TXT : BLUE, true);
        v.setGravity(Gravity.RIGHT);
        r.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
        if (sync) syncValue = v; else packetValue = v;
        return r;
    }

    private View liveCards() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(grid2(card("VELOCIDADE", "velocidade", TXT), card("VELOCIDADE MÁXIMA", "velocidadeMaxima", YELLOW)));
        wrap.addView(grid2(card("RPM", "rpm", TXT), card("MARCHA", "marcha", GREEN)));
        wrap.addView(progressCard("ACELERADOR", "acelerador", GREEN));
        wrap.addView(progressCard("FREIO", "freio", RED));
        wrap.addView(grid2(card("COMBUSTÍVEL", "combustivel", TXT), card("COMBUSTÍVEL %", "combustivelPct", CYAN)));
        return wrap;
    }

    private View resultCards() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(grid2(card("MELHOR VOLTA", "melhorVolta", PURPLE), card("ÚLTIMA VOLTA", "ultimaVolta", TXT)));
        wrap.addView(grid2(card("TEMPO TOTAL", "tempoTotal", CYAN), card("VOLTAS BRUTAS", "voltasBrutas", TXT)));
        wrap.addView(grid2(card("VOLTAS CORRIGIDAS", "voltasCorrigidas", BLUE), card("ESTADO DA CORRIDA", "estadoCorrida", BLUE)));
        wrap.addView(grid2(card("PARADAS BOXES", "paradasBoxes", TXT), card("VELOCIDADE MÁXIMA", "velocidadeMaxima", YELLOW)));
        return wrap;
    }

    private View vehicleCards() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(grid2(card("PRESSÃO DO TURBO", "turbo", PURPLE), card("VETORES VELOCIDADE", "vetorVelocidade", GREEN)));
        wrap.addView(grid2(card("ROTAÇÕES ROLL/PITCH", "rollPitch", TXT), card("VETOR YAW", "yaw", ORANGE)));
        return wrap;
    }

    private View card(String title, String key, int color) {
        LinearLayout c = box(CARD, 22, STROKE, 1);
        c.setPadding(dp(16), dp(16), dp(16), dp(12));
        c.setLayoutParams(new LinearLayout.LayoutParams(0, dp(118), 1));
        LinearLayout head = hRow();
        TextView titleView = text(title, 11, MUTED, true);
        head.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chooser = text("✓", 14, BLUE, true);
        chooser.setGravity(Gravity.CENTER);
        chooser.setPadding(dp(8), dp(2), dp(8), dp(2));
        chooser.setBackground(round(Color.parseColor("#0D1A2B"), 10, Color.parseColor("#183050"), 1));
        head.addView(chooser);
        c.addView(head);
        TextView value = text("--", 22, color, true);
        value.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        c.addView(value, new LinearLayout.LayoutParams(-1, 0, 1));
        CardHolder h = new CardHolder(titleView, value, key);
        cards.add(h);
        chooser.setOnClickListener(v -> showTelemetryMenu(v, h));
        c.setOnClickListener(v -> showTelemetryMenu(v, h));
        return c;
    }

    private View progressCard(String title, String key, int color) {
        LinearLayout c = box(CARD, 22, STROKE, 1);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(112));
        lp.setMargins(0, dp(10), 0, 0);
        c.setLayoutParams(lp);
        LinearLayout head = hRow();
        TextView titleView = text(title, 11, MUTED, true);
        head.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text("0%", 13, color, true);
        head.addView(value);
        TextView chooser = text("  ✓", 13, BLUE, true);
        head.addView(chooser);
        c.addView(head);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.getProgressDrawable().setTint(color);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(26));
        blp.setMargins(0, dp(20), 0, 0);
        c.addView(bar, blp);
        CardHolder h = new CardHolder(titleView, value, key);
        h.progress = bar;
        cards.add(h);
        chooser.setOnClickListener(v -> showTelemetryMenu(v, h));
        c.setOnClickListener(v -> showTelemetryMenu(v, h));
        return c;
    }

    private void showTelemetryMenu(View anchor, CardHolder holder) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] labels = {"Velocidade", "Velocidade Máxima", "RPM", "Marcha", "Acelerador", "Freio", "Combustível", "Combustível %", "Melhor Volta", "Última Volta", "Tempo Total", "Voltas Brutas", "Voltas Corrigidas", "Estado da Corrida", "Paradas Boxes", "Pressão do Turbo", "Vetores Velocidade", "Rotações Roll/Pitch", "Vetor Yaw", "Coordenada X", "Coordenada Y", "Coordenada Z"};
        for (String l : labels) menu.getMenu().add(l);
        menu.setOnMenuItemClickListener(item -> {
            String label = String.valueOf(item.getTitle());
            holder.key = keyFromLabel(label);
            holder.title.setText(label.toUpperCase(Locale.ROOT));
            refreshCards();
            return true;
        });
        menu.show();
    }

    private String keyFromLabel(String label) {
        if (label.equals("Velocidade Máxima")) return "velocidadeMaxima";
        if (label.equals("Velocidade")) return "velocidade";
        if (label.equals("RPM")) return "rpm";
        if (label.equals("Marcha")) return "marcha";
        if (label.equals("Acelerador")) return "acelerador";
        if (label.equals("Freio")) return "freio";
        if (label.equals("Combustível")) return "combustivel";
        if (label.equals("Combustível %")) return "combustivelPct";
        if (label.equals("Melhor Volta")) return "melhorVolta";
        if (label.equals("Última Volta")) return "ultimaVolta";
        if (label.equals("Tempo Total")) return "tempoTotal";
        if (label.equals("Voltas Brutas")) return "voltasBrutas";
        if (label.equals("Voltas Corrigidas")) return "voltasCorrigidas";
        if (label.equals("Estado da Corrida")) return "estadoCorrida";
        if (label.equals("Paradas Boxes")) return "paradasBoxes";
        if (label.equals("Pressão do Turbo")) return "turbo";
        if (label.equals("Vetores Velocidade")) return "vetorVelocidade";
        if (label.equals("Rotações Roll/Pitch")) return "rollPitch";
        if (label.equals("Vetor Yaw")) return "yaw";
        if (label.equals("Coordenada X")) return "x";
        if (label.equals("Coordenada Y")) return "y";
        if (label.equals("Coordenada Z")) return "z";
        return "velocidade";
    }

    private View mapRow() {
        LinearLayout row = box(CARD, 20, STROKE, 1);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, dp(12));
        row.setLayoutParams(lp);
        LinearLayout inner = hRow();
        row.addView(inner);
        inner.addView(text("▥ COORDENADAS E MAPA DO\nCIRCUITO", 13, PURPLE, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView right = text("EXPANDIR\nCOORDENADAS ▼", 12, MUTED, true);
        right.setGravity(Gravity.RIGHT);
        inner.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private View detailsToggle() {
        detailsToggle = toggle("▣  MOSTRAR DETALHES DO BRIDGE      ▼", YELLOW);
        detailsToggle.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            detailsPanel.setVisibility(detailsOpen ? View.VISIBLE : View.GONE);
            detailsToggle.setText(detailsOpen ? "▣  RECOLHER DETALHES DO BRIDGE      ▲" : "▣  MOSTRAR DETALHES DO BRIDGE      ▼");
        });
        return detailsToggle;
    }

    private View details() {
        detailsPanel = box(CARD_DARK, 20, STROKE, 1);
        detailsPanel.setPadding(dp(16), dp(16), dp(16), dp(16));
        detailsPanel.setVisibility(View.GONE);
        detailsPanel.addView(text("Status: gt7.online aberto. Bridge de rede local ativo.", 12, MUTED, false));
        addDetail("01", "Conectado", "connected");
        addDetail("02", "Decodificação válida", "decodeOk");
        addDetail("03", "Status da leitura", "statusLeitura");
        addDetail("04", "Última atualização", "ultimaAtualizacao");
        addDetail("05", "Versão do pacote", "versaoPacote");
        addDetail("06", "Tamanho do pacote", "tamanhoPacote");
        addDetail("07", "Aviso / diagnóstico", "diagnostico");
        addDetail("08", "Velocidade atual", "velocidadeDetalhe");
        return detailsPanel;
    }

    private View historyToggle() {
        historyToggle = toggle("▣  MOSTRAR HISTÓRICO DE SESSÕES      ▼", GREEN);
        historyToggle.setOnClickListener(v -> {
            historyOpen = !historyOpen;
            historyPanel.setVisibility(historyOpen ? View.VISIBLE : View.GONE);
            historyToggle.setText(historyOpen ? "▣  RECOLHER HISTÓRICO DE SESSÕES      ▲" : "▣  MOSTRAR HISTÓRICO DE SESSÕES      ▼");
        });
        return historyToggle;
    }

    private View history() {
        historyPanel = box(CARD_DARK, 20, STROKE, 1);
        historyPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        historyPanel.setVisibility(View.GONE);
        return historyPanel;
    }

    private TextView toggle(String label, int color) {
        TextView t = text(label, 14, color, true);
        t.setPadding(dp(18), dp(16), dp(18), dp(16));
        t.setBackground(round(Color.parseColor("#33343A"), 22, Color.parseColor("#46464F"), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(10));
        t.setLayoutParams(lp);
        return t;
    }

    private void addDetail(String idx, String label, String key) {
        LinearLayout row = box(Color.parseColor("#080D15"), 16, Color.parseColor("#111824"), 1);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(lp);
        LinearLayout inner = hRow();
        row.addView(inner);
        TextView n = text(idx, 10, MUTED, true);
        n.setGravity(Gravity.CENTER);
        n.setPadding(dp(8), dp(5), dp(8), dp(5));
        n.setBackground(round(Color.parseColor("#11151F"), 8, Color.parseColor("#1A2030"), 1));
        inner.addView(n);
        TextView mid = text(label, 12, MUTED, true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, -2, 1);
        mlp.setMargins(dp(10), 0, dp(10), 0);
        inner.addView(mid, mlp);
        TextView val = text("--", 12, TXT, true);
        val.setGravity(Gravity.CENTER);
        val.setPadding(dp(12), dp(6), dp(12), dp(6));
        val.setBackground(round(Color.parseColor("#111621"), 10, Color.parseColor("#202A3C"), 1));
        inner.addView(val);
        detailViews.put(key, val);
        detailsPanel.addView(row);
    }

    private void fetchTelemetry() {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(DATA_URL).openConnection();
                conn.setConnectTimeout(1200);
                conn.setReadTimeout(1200);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                telemetry.fromJson(new JSONObject(sb.toString()));
            } catch (Exception e) {
                telemetry.offline();
            }
            handler.post(this::applyTelemetry);
        }).start();
    }

    private void applyTelemetry() {
        statusBadge.setText(telemetry.connected ? (telemetry.decodeOk ? "CONECTADO\nAO PS5" : "ONLINE /\nPACOTE") : "OFFLINE /\nDESCONECTADO");
        statusBadge.setTextColor(telemetry.connected ? (telemetry.decodeOk ? GREEN : YELLOW) : RED);
        statusBadge.setBackground(round(telemetry.connected ? Color.parseColor("#0A3022") : Color.parseColor("#3A0C1B"), 18, telemetry.connected ? Color.parseColor("#145437") : Color.parseColor("#572234"), 1));
        miniSpeed.setText(telemetry.velocidade + " KM/H");
        miniRpm.setText(telemetry.rpm);
        miniGear.setText(telemetry.marcha);
        ps5Value.setText(telemetry.ps5Ip);
        syncValue.setText(telemetry.timeText());
        packetValue.setText(telemetry.packetVersion + " (" + telemetry.packetSize + " bytes)");
        refreshCards();
        setDetail("connected", telemetry.connected ? "Sim" : "Não");
        setDetail("decodeOk", telemetry.decodeOk ? "Sim" : "Não");
        setDetail("statusLeitura", telemetry.connected ? (telemetry.decodeOk ? "Conectado ao PS5" : "Online / Pacote") : "Offline / Desconectado");
        setDetail("ultimaAtualizacao", telemetry.timeText());
        setDetail("versaoPacote", telemetry.packetVersion);
        setDetail("tamanhoPacote", telemetry.packetSize + " bytes");
        setDetail("diagnostico", telemetry.warning);
        setDetail("velocidadeDetalhe", telemetry.velocidade + " km/h");
        updateSessionState();
    }

    private void updateSessionState() {
        long now = System.currentTimeMillis();
        boolean active = telemetry.connected && (toInt(telemetry.velocidade) > 5 || toInt(telemetry.rpm) > 1200 || toInt(telemetry.voltasCorrigidas) > 0);
        if (active) {
            lastActiveAt = now;
            if (!sessionActive) {
                sessionActive = true;
                sessionSaved = false;
                sessionStartAt = now;
                lastCorrectedLaps = toInt(telemetry.voltasCorrigidas);
                lastLapChangeAt = now;
            }
        }
        int laps = toInt(telemetry.voltasCorrigidas);
        if (laps != lastCorrectedLaps) {
            lastCorrectedLaps = laps;
            lastLapChangeAt = now;
        }
        boolean finished = sessionActive && !sessionSaved && laps > 0 && telemetry.hasValidLap() && toInt(telemetry.velocidade) <= 3 && now - lastLapChangeAt > 6000 && now - lastActiveAt > 5000;
        if (finished) saveSession("automatico");
    }

    private void saveSession(String type) {
        try {
            JSONObject obj = telemetry.toSessionJson(type, sessionStartAt == 0 ? System.currentTimeMillis() : sessionStartAt);
            JSONArray old = new JSONArray(getPrefs().getString(KEY_SESSIONS, "[]"));
            JSONArray out = new JSONArray();
            out.put(obj);
            for (int i = 0; i < old.length(); i++) out.put(old.getJSONObject(i));
            getPrefs().edit().putString(KEY_SESSIONS, out.toString()).apply();
            sessionSaved = true;
            sessionActive = false;
            loadHistory();
            ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("GT7 Sessão", obj.toString()));
            Toast.makeText(this, type.equals("manual") ? "Sessão salva manualmente" : "Sessão salva automaticamente", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar sessão", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadHistory() {
        if (historyPanel == null) return;
        historyPanel.removeAllViews();
        historyPanel.addView(text("SESSÕES SALVAS", 14, GREEN, true));
        try {
            JSONArray arr = new JSONArray(getPrefs().getString(KEY_SESSIONS, "[]"));
            if (arr.length() == 0) {
                historyPanel.addView(text("Nenhuma sessão salva ainda.", 12, MUTED, false));
                return;
            }
            int max = Math.min(arr.length(), 12);
            for (int i = 0; i < max; i++) addHistoryRow(arr.getJSONObject(i));
        } catch (Exception e) {
            historyPanel.addView(text("Histórico indisponível.", 12, RED, false));
        }
    }

    private void addHistoryRow(JSONObject o) {
        LinearLayout row = box(Color.parseColor("#080D15"), 16, Color.parseColor("#111824"), 1);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(lp);
        TextView txt = text(o.optString("tipoSalvamento", "manual").toUpperCase(Locale.ROOT) + " • " + o.optString("dataFim", "--") + "\nMelhor: " + o.optString("melhorVolta", "--") + "  Total: " + o.optString("tempoTotal", "--") + "  Voltas: " + o.optString("voltasCorrigidas", "0"), 12, TXT, true);
        row.addView(txt);
        row.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("GT7 Sessão", o.toString()));
            Toast.makeText(this, "JSON da sessão copiado", Toast.LENGTH_SHORT).show();
        });
        historyPanel.addView(row);
    }

    private void refreshCards() {
        for (CardHolder c : cards) {
            c.value.setText(telemetry.valueFor(c.key));
            if (c.progress != null) c.progress.setProgress(telemetry.percentFor(c.key));
        }
    }

    private void setDetail(String key, String val) {
        TextView v = detailViews.get(key);
        if (v != null) v.setText(val == null || val.length() == 0 ? "--" : val);
    }

    private View grid2(View a, View b) {
        LinearLayout row = hRow();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, 0);
        row.setLayoutParams(lp);
        row.addView(a);
        row.addView(space(10, 1));
        row.addView(b);
        return row;
    }

    private TextView section(String label, int color) {
        TextView t = text("▌ " + label, 15, color, true);
        t.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(14), 0, dp(4));
        t.setLayoutParams(lp);
        return t;
    }

    private TextView pill(String label, int textColor, int bgColor) {
        TextView t = text(label, 12, textColor, true);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(round(bgColor, 18, lighten(bgColor), 1));
        return t;
    }

    private TextView smallPill(String label, int textColor, int bgColor) {
        TextView t = text(label, 12, textColor, true);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        t.setBackground(round(bgColor, 18, lighten(bgColor), 1));
        return t;
    }

    private LinearLayout box(int color, int radius, int stroke, int strokeWidth) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(color, radius, stroke, strokeWidth));
        return l;
    }

    private LinearLayout hRow() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private TextView text(String label, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
        return d;
    }

    private int lighten(int c) {
        return Color.rgb(Math.min(255, (int)(Color.red(c) * 1.25)), Math.min(255, (int)(Color.green(c) * 1.25)), Math.min(255, (int)(Color.blue(c) * 1.25)));
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#152131"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(0, dp(14), 0, dp(8));
        v.setLayoutParams(lp);
        return v;
    }

    private Space space(int w, int h) {
        Space s = new Space(this);
        s.setLayoutParams(new LinearLayout.LayoutParams(dp(w), dp(h)));
        return s;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int toInt(String s) { try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); } catch (Exception e) { return 0; } }
    private SharedPreferences getPrefs() { return getSharedPreferences(PREF, MODE_PRIVATE); }

    static class CardHolder {
        TextView title, value;
        String key;
        ProgressBar progress;
        CardHolder(TextView title, TextView value, String key) { this.title = title; this.value = value; this.key = key; }
    }

    static class Telemetry {
        boolean connected = false, decodeOk = false;
        long updatedAt = 0;
        int packetSize = 368;
        String packetVersion = "v1.4", ps5Ip = "192.168.1.54";
        String velocidade = "0", velocidadeMaxima = "0", rpm = "0", marcha = "N";
        int acelerador = 0, freio = 0;
        String combustivel = "--", combustivelPct = "--", melhorVolta = "--", ultimaVolta = "--", tempoTotal = "--", voltasBrutas = "0", voltasCorrigidas = "0", estadoCorrida = "◔ EM ANDAMENTO", paradasBoxes = "0", turbo = "--", vetorVelocidade = "--", rollPitch = "Pitch: --°\nRoll: --°", yaw = "--", x = "--", y = "--", z = "--", warning = "Sem fluxo de dados";

        void fromJson(JSONObject j) {
            connected = j.optBoolean("connected", false);
            decodeOk = j.optBoolean("decodeOk", false);
            updatedAt = j.optLong("updatedAt", System.currentTimeMillis());
            packetSize = j.optInt("packetSize", packetSize);
            ps5Ip = j.optString("ps5Ip", ps5Ip);
            velocidade = intText(j, "velocidade", velocidade);
            velocidadeMaxima = intText(j, "velocidadeMaxima", velocidadeMaxima);
            rpm = intText(j, "rpm", rpm);
            marcha = j.optString("marcha", marcha);
            acelerador = j.optInt("acelerador", acelerador);
            freio = j.optInt("freio", freio);
            combustivel = intText(j, "combustivel", combustivel);
            combustivelPct = intText(j, "combustivelPorcentagem", combustivelPct);
            melhorVolta = cleanTime(j.optString("melhorVolta", melhorVolta));
            ultimaVolta = cleanTime(j.optString("ultimaVolta", ultimaVolta));
            voltasBrutas = String.valueOf(j.optInt("voltasCompletadas", toInt(voltasBrutas)));
            voltasCorrigidas = String.valueOf(j.optInt("voltasCorrigidas", Math.max(0, toInt(voltasBrutas) - 1)));
            JSONObject p = j.optJSONObject("position");
            if (p != null) { x = fmt(p.optDouble("x", 0)); y = fmt(p.optDouble("y", 0)); z = fmt(p.optDouble("z", 0)); }
            tempoTotal = totalCorrigido();
            warning = j.optString("warning", connected ? "Dados fluindo normalmente" : "Sem fluxo de dados");
        }

        void offline() { connected = false; decodeOk = false; updatedAt = System.currentTimeMillis(); warning = "Sem fluxo de dados"; }
        String valueFor(String key) {
            if (key.equals("velocidade")) return velocidade;
            if (key.equals("velocidadeMaxima")) return velocidadeMaxima;
            if (key.equals("rpm")) return rpm;
            if (key.equals("marcha")) return marcha;
            if (key.equals("acelerador")) return acelerador + "%";
            if (key.equals("freio")) return freio + "%";
            if (key.equals("combustivel")) return combustivel;
            if (key.equals("combustivelPct")) return combustivelPct;
            if (key.equals("melhorVolta")) return melhorVolta;
            if (key.equals("ultimaVolta")) return ultimaVolta;
            if (key.equals("tempoTotal")) return tempoTotal;
            if (key.equals("voltasBrutas")) return voltasBrutas;
            if (key.equals("voltasCorrigidas")) return voltasCorrigidas;
            if (key.equals("estadoCorrida")) return estadoCorrida;
            if (key.equals("paradasBoxes")) return paradasBoxes;
            if (key.equals("turbo")) return turbo;
            if (key.equals("vetorVelocidade")) return vetorVelocidade;
            if (key.equals("rollPitch")) return rollPitch;
            if (key.equals("yaw")) return yaw;
            if (key.equals("x")) return x;
            if (key.equals("y")) return y;
            if (key.equals("z")) return z;
            return "--";
        }
        int percentFor(String key) { if (key.equals("acelerador")) return acelerador; if (key.equals("freio")) return freio; if (key.equals("combustivelPct")) return toInt(combustivelPct); return 0; }
        boolean hasValidLap() { return parseMs(ultimaVolta) > 0 || parseMs(melhorVolta) > 0; }
        String timeText() { return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(updatedAt == 0 ? System.currentTimeMillis() : updatedAt)); }
        JSONObject toSessionJson(String type, long startAt) throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", "session_" + type + "_" + System.currentTimeMillis());
            o.put("tipoSalvamento", type);
            o.put("dataInicio", new Date(startAt).toString());
            o.put("dataFim", new Date().toString());
            o.put("ps5Ip", ps5Ip);
            o.put("statusFinal", estadoCorrida);
            o.put("melhorVolta", melhorVolta);
            o.put("ultimaVolta", ultimaVolta);
            o.put("tempoTotal", tempoTotal);
            o.put("voltasBrutas", voltasBrutas);
            o.put("voltasCorrigidas", voltasCorrigidas);
            o.put("velocidadeMaxima", velocidadeMaxima);
            o.put("combustivelFinal", combustivel);
            o.put("combustivelPorcentagemFinal", combustivelPct);
            o.put("rpmFinal", rpm);
            o.put("marchaFinal", marcha);
            o.put("aceleradorFinal", acelerador);
            o.put("freioFinal", freio);
            o.put("paradasBoxes", paradasBoxes);
            JSONObject snap = new JSONObject();
            snap.put("velocidade", velocidade); snap.put("rpm", rpm); snap.put("marcha", marcha); snap.put("packetSize", packetSize); snap.put("x", x); snap.put("y", y); snap.put("z", z);
            o.put("snapshotTelemetria", snap);
            return o;
        }
        String totalCorrigido() { int laps = toInt(voltasCorrigidas); if (laps <= 0) return "--"; long base = parseMs(ultimaVolta); if (base <= 0) base = parseMs(melhorVolta); if (base <= 0) return "--"; return formatMs(base * laps); }
        static String intText(JSONObject j, String key, String fallback) { if (!j.has(key) || j.isNull(key)) return fallback; return String.valueOf((int)Math.round(j.optDouble(key, 0))); }
        static String cleanTime(String s) { if (s == null || s.length() == 0 || s.equals("null")) return "--"; return s; }
        static String fmt(double d) { return String.format(Locale.US, "%.2f", d); }
        static int toInt(String s) { try { return Integer.parseInt(s.replaceAll("[^0-9-]", "")); } catch (Exception e) { return 0; } }
        static long parseMs(String s) {
            try {
                if (s == null || s.equals("--")) return 0;
                String[] a = s.trim().split(":"); long min = 0; String secPart = s.trim();
                if (a.length == 2) { min = Long.parseLong(a[0]); secPart = a[1]; }
                String[] b = secPart.split("\\."); long sec = Long.parseLong(b[0]); long ms = 0;
                if (b.length > 1) { String m = b[1] + "000"; ms = Long.parseLong(m.substring(0, 3)); }
                return min * 60000L + sec * 1000L + ms;
            } catch (Exception e) { return 0; }
        }
        static String formatMs(long ms) { long min = ms / 60000L; long sec = (ms % 60000L) / 1000L; long rem = ms % 1000L; return String.format(Locale.US, "%d:%02d.%03d", min, sec, rem); }
    }
}
