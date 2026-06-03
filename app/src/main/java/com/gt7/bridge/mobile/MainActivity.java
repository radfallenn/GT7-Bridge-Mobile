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
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String VERSION = "1.59";
    private static final String BRIDGE_URL = "http://192.168.1.70:8787";
    private static final String PREF = "gt7_bridge_mobile";
    private static final String KEY_PS5_IP = "ps5_ip";
    private static final String KEY_SESSIONS = "gt7_saved_sessions";
    private static final String DEFAULT_PS5_IP = "192.168.1.54";

    private static final int BG = Color.parseColor("#02050A");
    private static final int CARD = Color.parseColor("#0B1726");
    private static final int CARD_DARK = Color.parseColor("#050911");
    private static final int STROKE = Color.parseColor("#1A2F4A");
    private static final int TXT = Color.parseColor("#F5F8FF");
    private static final int MUTED = Color.parseColor("#98A6B8");
    private static final int BLUE = Color.parseColor("#1E88FF");
    private static final int CYAN = Color.parseColor("#12E8FF");
    private static final int GREEN = Color.parseColor("#22F5A2");
    private static final int YELLOW = Color.parseColor("#FFD35A");
    private static final int PURPLE = Color.parseColor("#A86BFF");
    private static final int RED = Color.parseColor("#FF315E");
    private static final int ORANGE = Color.parseColor("#FF9E28");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Telemetry t = new Telemetry();
    private Health h = new Health();

    private TextView statusBadge, speedView, maxSpeedView, rpmView, gearView;
    private TextView throttlePct, brakePct, fuelL, fuelPct, raceState, pitStops;
    private TextView bestLap, lastLap, correctedLaps, rawLaps, totalTime;
    private TextView carCode, turbo, oil, vectors, rotation, angular;
    private ProgressBar throttleBar, brakeBar;
    private GaugeView gauge;

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
        root.setPadding(dp(12), dp(10), dp(12), dp(112));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        root.addView(topBar());
        root.addView(heroPanel());
        root.addView(progressRow());
        root.addView(statusRow());
        root.addView(resultsSection());
        root.addView(advancedSection());
        screen.addView(bottomNav(), bottomLp());

        setContentView(screen);
    }

    private View topBar() {
        LinearLayout row = hRow();
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.setMargins(0, dp(2), 0, dp(14));
        row.setLayoutParams(lp);

        TextView menu = iconBox("☰", 26);
        menu.setOnClickListener(v -> editPs5Ip());
        row.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout title = box(CARD_DARK, 10, STROKE, 1);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(14), 0, dp(12), 0);
        TextView tx = text("TELEMETRIA", 17, TXT, true);
        tx.setLetterSpacing(.05f);
        title.addView(tx);
        title.addView(text("• AO VIVO", 11, GREEN, true));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, dp(42), 1);
        titleLp.setMargins(dp(6), 0, dp(6), 0);
        row.addView(title, titleLp);

        statusBadge = text("🎮  CONECTANDO\nAO PS5", 10, GREEN, true);
        statusBadge.setGravity(Gravity.CENTER);
        statusBadge.setBackground(round(Color.parseColor("#083226"), 13, Color.parseColor("#0F6247"), 1));
        row.addView(statusBadge, new LinearLayout.LayoutParams(dp(112), dp(42)));

        TextView more = iconBox("⋮", 28);
        more.setOnClickListener(v -> showQuickInfo());
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(42), dp(42));
        mlp.setMargins(dp(6), 0, 0, 0);
        row.addView(more, mlp);
        return row;
    }

    private View heroPanel() {
        LinearLayout row = hRow();
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(226));
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);

        LinearLayout left = sideGauge("RPM", PURPLE, false);
        rpmView = (TextView) left.findViewWithTag("value");
        row.addView(left, new LinearLayout.LayoutParams(dp(76), dp(160)));

        FrameLayout center = new FrameLayout(this);
        gauge = new GaugeView(this);
        center.addView(gauge, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = vBox();
        overlay.setGravity(Gravity.CENTER);
        speedView = text("0", 60, TXT, true);
        speedView.setGravity(Gravity.CENTER);
        speedView.setIncludeFontPadding(false);
        overlay.addView(speedView);
        TextView kmh = text("km/h", 12, TXT, true);
        kmh.setGravity(Gravity.CENTER);
        overlay.addView(kmh);
        TextView vel = text("VELOCIDADE", 10, MUTED, true);
        vel.setGravity(Gravity.CENTER);
        vel.setLetterSpacing(.08f);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.setMargins(0, dp(8), 0, 0);
        overlay.addView(vel, vlp);
        maxSpeedView = text("0 km/h", 18, YELLOW, true);
        maxSpeedView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.setMargins(0, dp(8), 0, 0);
        overlay.addView(maxSpeedView, mlp);
        TextView maxLabel = text("MÁXIMA", 10, MUTED, true);
        maxLabel.setGravity(Gravity.CENTER);
        overlay.addView(maxLabel);

        center.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(222), 1);
        clp.setMargins(dp(4), 0, dp(4), 0);
        row.addView(center, clp);

        LinearLayout right = sideGauge("MARCHA", GREEN, true);
        gearView = (TextView) right.findViewWithTag("value");
        row.addView(right, new LinearLayout.LayoutParams(dp(76), dp(160)));
        return row;
    }

    private LinearLayout sideGauge(String label, int color, boolean bars) {
        LinearLayout card = box(CARD_DARK, 23, STROKE, 1);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(10), dp(8), dp(10));
        card.addView(text(label, 10, MUTED, true));
        TextView v = text(bars ? "N" : "0", 31, TXT, true);
        v.setTag("value");
        v.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.setMargins(0, dp(8), 0, 0);
        card.addView(v, vlp);
        if (!bars) {
            TextView rpmUnit = text("rpm", 10, MUTED, true);
            rpmUnit.setGravity(Gravity.CENTER);
            card.addView(rpmUnit);
        }
        SparkView sp = new SparkView(this, color, bars ? 2 : 1);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(36));
        slp.setMargins(0, dp(10), 0, 0);
        card.addView(sp, slp);
        return card;
    }

    private View progressRow() {
        LinearLayout row = hRow();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(64));
        lp.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(lp);
        row.addView(progressCard("ACELERADOR", CYAN, true), new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(space(8, 1));
        row.addView(progressCard("FREIO", RED, false), new LinearLayout.LayoutParams(0, -1, 1));
        return row;
    }

    private View progressCard(String label, int color, boolean throttle) {
        LinearLayout c = box(CARD_DARK, 18, STROKE, 1);
        c.setPadding(dp(14), dp(10), dp(14), dp(9));
        LinearLayout head = hRow();
        head.addView(text(label, 10, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView pct = text("0%", 15, color, true);
        head.addView(pct);
        c.addView(head);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.getProgressDrawable().setTint(color);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(9));
        blp.setMargins(0, dp(12), 0, 0);
        c.addView(bar, blp);
        if (throttle) { throttlePct = pct; throttleBar = bar; } else { brakePct = pct; brakeBar = bar; }
        return c;
    }

    private View statusRow() {
        LinearLayout row = hRow();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(74));
        lp.setMargins(0, 0, 0, dp(12));
        row.setLayoutParams(lp);
        row.addView(statusMini("⛽", "COMBUSTÍVEL", true), new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(space(6, 1));
        row.addView(statusMini("⛽", "COMBUSTÍVEL %", false), new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(space(6, 1));
        row.addView(stateMini(), new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(space(6, 1));
        row.addView(pitMini(), new LinearLayout.LayoutParams(0, -1, 1));
        return row;
    }

    private LinearLayout statusMini(String icon, String label, boolean liters) {
        LinearLayout c = box(CARD, 10, STROKE, 1);
        c.setPadding(dp(9), dp(8), dp(9), dp(8));
        c.addView(text(icon + "  " + label, 9, MUTED, true));
        TextView v = text(liters ? "-- L" : "--%", 17, TXT, true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.setMargins(0, dp(7), 0, 0);
        c.addView(v, vlp);
        View line = new View(this);
        line.setBackgroundColor(BLUE);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, dp(4));
        l.setMargins(0, dp(8), 0, 0);
        c.addView(line, l);
        if (liters) fuelL = v; else fuelPct = v;
        return c;
    }

    private LinearLayout stateMini() {
        LinearLayout c = box(CARD, 10, STROKE, 1);
        c.setPadding(dp(9), dp(12), dp(9), dp(8));
        c.addView(text("ESTADO DA CORRIDA", 9, BLUE, true));
        raceState = text("AGUARDANDO", 14, GREEN, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(9), 0, 0);
        c.addView(raceState, lp);
        return c;
    }

    private LinearLayout pitMini() {
        LinearLayout c = box(CARD, 10, STROKE, 1);
        c.setPadding(dp(9), dp(12), dp(9), dp(8));
        c.addView(text("PARADAS BOXES", 9, MUTED, true));
        pitStops = text("--", 17, TXT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(9), 0, 0);
        c.addView(pitStops, lp);
        return c;
    }

    private View resultsSection() {
        LinearLayout sec = sectionBox("⚑", "VOLTAS & RESULTADOS", "VER DETALHES  ›");
        LinearLayout cards = hRow();
        cards.setGravity(Gravity.CENTER);
        bestLap = addResultCard(cards, "MELHOR VOLTA", "--", PURPLE, true);
        lastLap = addResultCard(cards, "ÚLTIMA VOLTA", "--", TXT, false);
        correctedLaps = addResultCard(cards, "VOLTAS\nCORRIGIDAS", "0", BLUE, false);
        rawLaps = addResultCard(cards, "VOLTAS BRUTAS", "0", TXT, false);
        totalTime = addResultCard(cards, "TEMPO TOTAL", "--", CYAN, false);
        sec.addView(cards, new LinearLayout.LayoutParams(-1, dp(92)));
        return sec;
    }

    private TextView addResultCard(LinearLayout row, String label, String value, int color, boolean glow) {
        LinearLayout c = box(glow ? Color.parseColor("#120B25") : CARD_DARK, 12, glow ? PURPLE : STROKE, 1);
        c.setPadding(dp(8), dp(9), dp(8), dp(6));
        c.addView(text(label, 9, color, true));
        TextView v = text(value, 18, color == TXT ? TXT : color, true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.setMargins(0, dp(8), 0, 0);
        c.addView(v, vlp);
        SparkView sp = new SparkView(this, color == TXT ? MUTED : color, 3);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(20));
        slp.setMargins(0, dp(5), 0, 0);
        c.addView(sp, slp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(c, lp);
        return v;
    }

    private View advancedSection() {
        LinearLayout sec = sectionBox("〽", "TELEMETRIA AVANÇADA", "⚙  CONFIGURAR  ›");
        carCode = addAdvRow(sec, "CÓDIGO DO CARRO", "--", "▱", YELLOW, "PRESSÃO DO TURBO", "--", "◉", PURPLE);
        turbo = (TextView) sec.findViewWithTag("PRESSÃO DO TURBO");
        oil = addAdvRow(sec, "PRESSÃO DO ÓLEO", "--", "⌁", ORANGE, "VETORES VELOCIDADE", "--", "⊙", GREEN);
        vectors = (TextView) sec.findViewWithTag("VETORES VELOCIDADE");
        rotation = addAdvRow(sec, "ROTAÇÃO PITCH/\nROLL/YAW", "--", "⟲", PURPLE, "VELOCIDADE ANGULAR", "--", "⊙", CYAN);
        angular = (TextView) sec.findViewWithTag("VELOCIDADE ANGULAR");
        return sec;
    }

    private TextView addAdvRow(LinearLayout sec, String l1, String v1, String i1, int c1, String l2, String v2, String i2, int c2) {
        LinearLayout row = hRow();
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(82));
        rlp.setMargins(0, dp(7), 0, 0);
        row.setLayoutParams(rlp);
        LinearLayout cA = advCard(l1, v1, i1, c1);
        TextView first = (TextView)cA.findViewWithTag(l1);
        row.addView(cA, new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(space(7, 1));
        row.addView(advCard(l2, v2, i2, c2), new LinearLayout.LayoutParams(0, -1, 1));
        sec.addView(row);
        return first;
    }

    private LinearLayout advCard(String label, String value, String icon, int color) {
        LinearLayout c = box(CARD_DARK, 10, STROKE, 1);
        c.setPadding(dp(10), dp(9), dp(10), dp(6));
        LinearLayout head = hRow();
        head.addView(text(label, 9, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(text(icon, 25, color, true));
        c.addView(head);
        TextView v = text(value, 17, TXT, true);
        v.setTag(label);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
        vlp.setMargins(0, dp(4), 0, 0);
        c.addView(v, vlp);
        SparkView sp = new SparkView(this, color, 4);
        c.addView(sp, new LinearLayout.LayoutParams(-1, dp(18)));
        return c;
    }

    private LinearLayout sectionBox(String icon, String title, String action) {
        LinearLayout sec = box(Color.parseColor("#07111E"), 13, STROKE, 1);
        sec.setPadding(dp(11), dp(11), dp(11), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        sec.setLayoutParams(lp);
        LinearLayout head = hRow();
        head.addView(text(icon + "  " + title, 14, TXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView btn = text(action, 10, TXT, true);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(round(Color.parseColor("#0B1728"), 9, STROKE, 1));
        btn.setOnClickListener(v -> showQuickInfo());
        head.addView(btn, new LinearLayout.LayoutParams(dp(108), dp(34)));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.setMargins(0, 0, 0, dp(10));
        sec.addView(head, hlp);
        return sec;
    }

    private FrameLayout bottomNav() {
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout nav = hRow();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), 0, dp(10), 0);
        nav.setBackground(round(Color.parseColor("#07111D"), 12, STROKE, 1));
        wrap.addView(nav, new FrameLayout.LayoutParams(-1, dp(70), Gravity.BOTTOM));

        nav.addView(navItem("▦", "DASHBOARD", true), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem("⏱", "VOLTAS", false), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(new Space(this), new LinearLayout.LayoutParams(dp(96), -1));
        nav.addView(navItem("〽", "TELEMETRIA", false), new LinearLayout.LayoutParams(0, -1, 1));
        nav.addView(navItem("⚙", "CONFIGURAÇÕES", false), new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout live = vBox();
        live.setGravity(Gravity.CENTER);
        live.setBackground(round(Color.parseColor("#111923"), 999, RED, 2));
        TextView ico = text("◉", 30, TXT, true);
        ico.setGravity(Gravity.CENTER);
        live.addView(ico);
        TextView label = text("AO VIVO", 14, TXT, true);
        label.setGravity(Gravity.CENTER);
        live.addView(label);
        live.setOnClickListener(v -> fetchFields());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER | Gravity.BOTTOM);
        lp.setMargins(0, 0, 0, dp(11));
        wrap.addView(live, lp);
        return wrap;
    }

    private FrameLayout.LayoutParams bottomLp() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(116), Gravity.BOTTOM);
        lp.setMargins(dp(10), 0, dp(10), dp(6));
        return lp;
    }

    private LinearLayout navItem(String icon, String label, boolean active) {
        LinearLayout item = vBox();
        item.setGravity(Gravity.CENTER);
        item.addView(text(icon, 25, TXT, true));
        TextView t = text(label, 10, TXT, true);
        t.setGravity(Gravity.CENTER);
        item.addView(t);
        if (active) {
            View line = new View(this);
            line.setBackgroundColor(RED);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(54), dp(4));
            lp.setMargins(0, dp(5), 0, 0);
            item.addView(line, lp);
        }
        item.setOnClickListener(v -> showQuickInfo());
        return item;
    }

    private void showQuickInfo() {
        new AlertDialog.Builder(this)
                .setTitle("GT7 Bridge Mobile v" + VERSION)
                .setMessage("Bridge: " + BRIDGE_URL + "\nPS5 IP: " + getPs5Ip() + "\n\nO app atualiza a telemetria por /api/fields e mantém o visual no padrão do painel de referência.")
                .setPositiveButton("Editar IP PS5", (d, w) -> editPs5Ip())
                .setNegativeButton("Fechar", null)
                .show();
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
                    Toast.makeText(this, "IP do PS5 salvo", Toast.LENGTH_SHORT).show();
                    fetchFields();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void fetchFields() { fetchJson(BRIDGE_URL + "/api/fields", obj -> { t.fromJson(obj, getPs5Ip()); applyTelemetry(); }); }
    private void fetchHealth(boolean toast) { fetchJson(BRIDGE_URL + "/api/health", obj -> { h.fromJson(obj); if (toast) Toast.makeText(this, "Health atualizado", Toast.LENGTH_SHORT).show(); }); }

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
                handler.post(() -> { if (url.contains("/api/fields")) { t.offline(); applyTelemetry(); } });
            }
        }).start();
    }

    private void applyTelemetry() {
        statusBadge.setText(t.connected ? (t.decodeOk ? "🎮  CONECTADO\nAO PS5" : "🎮  ONLINE\nPACOTE") : "🎮  OFFLINE\nAO PS5");
        statusBadge.setTextColor(t.connected ? (t.decodeOk ? GREEN : YELLOW) : RED);
        statusBadge.setBackground(round(t.connected ? Color.parseColor("#083226") : Color.parseColor("#2C1118"), 13, t.connected ? Color.parseColor("#0F6247") : Color.parseColor("#653041"), 1));

        speedView.setText(t.velocidade);
        maxSpeedView.setText(t.velocidadeMaxima + " km/h");
        rpmView.setText(t.rpm);
        gearView.setText(t.marcha);
        throttlePct.setText(t.acelerador + "%");
        brakePct.setText(t.freio + "%");
        throttleBar.setProgress(t.acelerador);
        brakeBar.setProgress(t.freio);
        fuelL.setText(t.combustivel + " L");
        fuelPct.setText(t.combustivelPct + "%");
        raceState.setText(t.estadoCorrida);
        pitStops.setText(t.paradasBoxes);
        bestLap.setText(t.melhorVolta);
        lastLap.setText(t.ultimaVolta);
        correctedLaps.setText(t.voltasCorrigidas);
        rawLaps.setText(t.voltasBrutas);
        totalTime.setText(t.tempoTotal);
        carCode.setText(t.codigoCarro);
        turbo.setText(t.turbo);
        oil.setText(t.oilPressure);
        vectors.setText(t.speedVector);
        rotation.setText(t.rotation);
        angular.setText(t.angularVelocity);
        gauge.setValues(num(t.velocidade), num(t.rpm));
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
    private Space space(int w, int h) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(w), dp(h))); return s; }

    private TextView text(String label, int size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(true);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView iconBox(String label, int size) {
        TextView tv = text(label, size, TXT, true);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(round(CARD_DARK, 10, STROKE, 1));
        return tv;
    }

    private LinearLayout box(int color, int radius, int stroke, int sw) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(color, radius, stroke, sw));
        return l;
    }

    private GradientDrawable round(int color, int radius, int stroke, int sw) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (sw > 0) d.setStroke(dp(sw), stroke);
        return d;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    static class Health {
        boolean ok; String status="--"; boolean connected;
        void fromJson(JSONObject o){ ok=o.optBoolean("ok", false); status=o.optString("status", "--"); connected=o.optBoolean("connected", false); }
    }

    static class Telemetry {
        boolean connected=false, decodeOk=false;
        long updatedAt=0;
        int packetSize=0, acelerador=0, freio=0;
        String packetVersion="?", ps5Ip="192.168.1.54", velocidade="0", velocidadeMaxima="0", rpm="0", marcha="N", combustivel="--", combustivelPct="--", melhorVolta="--", ultimaVolta="--", tempoTotal="--", voltasBrutas="0", voltasCorrigidas="0", estadoCorrida="AGUARDANDO", paradasBoxes="--", codigoCarro="--", turbo="--", oilPressure="--", speedVector="--", rotation="--", angularVelocity="--", position="--";

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
            marcha=first(j,"marcha","gear","current_gear");
            if (marcha.equals("--")) marcha = "N";
            acelerador=percent(j,"acelerador","throttle","throttle_percent","accelerator");
            freio=percent(j,"freio","brake","brake_percent");
            combustivel=clean(first(j,"combustivel","fuel_liters","fuelLiters","fuel"), "--");
            combustivelPct=clean(first(j,"combustivelPorcentagem","combustivelPct","fuel_percent","fuelPercent"), "--");
            melhorVolta=first(j,"melhorVolta","bestLap","best_lap","best_lap_time");
            ultimaVolta=first(j,"ultimaVolta","lastLap","last_lap","last_lap_time");
            tempoTotal=first(j,"tempoTotalCorrida","tempoTotal","totalTime","total_time");
            voltasBrutas=clean(first(j,"voltasCompletadas","voltasBrutas","rawLaps","raw_laps"), "0");
            voltasCorrigidas=clean(first(j,"voltasCorrigidas","completed_laps","completedLaps","lap_count"), "0");
            paradasBoxes=clean(first(j,"paradasBoxes","pitStops","pit_stops"), "--");
            codigoCarro=first(j,"codigoCarro","carCode","carId","car_id","vehicleCode","car_code");
            turbo=first(j,"turbo","turbo_pressure");
            oilPressure=first(j,"oilPressure","oil_pressure","oil");
            speedVector=obj(j.optJSONObject("speedVector"));
            if (speedVector.equals("--")) speedVector = obj(j.optJSONObject("velocity_vector"));
            rotation=obj(j.optJSONObject("rotation"));
            angularVelocity=obj(j.optJSONObject("angularVelocity"));
            position=obj(j.optJSONObject("position"));
            estadoCorrida = (num(velocidade) > 3 || num(rpm) > 1000 || num(voltasCorrigidas) > 0) ? "EM ANDAMENTO" : "AGUARDANDO";
        }

        void offline(){ connected=false; decodeOk=false; estadoCorrida="AGUARDANDO"; }

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
        static String clean(String v, String fallback){ if(v==null || v.equals("--") || v.length()==0) return fallback; if(v.endsWith(".0")) return v.substring(0,v.length()-2); return v; }
        static String first(JSONObject j, String... keys){ for(String k: keys){ if(j.has(k) && !j.isNull(k)) return String.valueOf(j.opt(k)); } return "--"; }
        static String obj(JSONObject o){ if(o==null) return "--"; StringBuilder sb=new StringBuilder(); JSONArray n=o.names(); if(n==null) return "--"; for(int i=0;i<n.length();i++){ String k=n.optString(i); if(i>0) sb.append("  "); sb.append(k).append(": ").append(o.opt(k)); } return sb.toString(); }
    }

    static class GaugeView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int speed = 0, rpm = 0;

        GaugeView(Context c) { super(c); text.setTypeface(Typeface.DEFAULT_BOLD); }

        void setValues(int s, int r) { speed=s; rpm=r; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f + dpLocal(8);
            float r = Math.min(w, h) * .43f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.parseColor("#050910"));
            c.drawCircle(cx, cy, r * 1.08f, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dpLocal(16));
            p.setStrokeCap(Paint.Cap.BUTT);
            RectF arc = new RectF(cx-r, cy-r, cx+r, cy+r);
            p.setShader(new LinearGradient(cx-r, cy, cx+r, cy, new int[]{Color.parseColor("#1E88FF"), Color.parseColor("#12E8FF"), Color.parseColor("#FFD35A"), Color.parseColor("#FF315E")}, null, Shader.TileMode.CLAMP));
            c.drawArc(arc, 218, 284, false, p);
            p.setShader(null);

            p.setStrokeWidth(dpLocal(2));
            for (int i=0; i<=50; i++) {
                float a = (float)Math.toRadians(218 + 284 * i / 50f);
                float inner = i % 5 == 0 ? r - dpLocal(24) : r - dpLocal(15);
                float x1 = cx + (float)Math.cos(a) * inner;
                float y1 = cy + (float)Math.sin(a) * inner;
                float x2 = cx + (float)Math.cos(a) * (r - dpLocal(2));
                float y2 = cy + (float)Math.sin(a) * (r - dpLocal(2));
                p.setColor(Color.argb(i%5==0?230:130,255,255,255));
                c.drawLine(x1,y1,x2,y2,p);
            }

            text.setTextSize(dpLocal(14));
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            for (int i=0; i<=10; i++) {
                float a = (float)Math.toRadians(218 + 284 * i / 10f);
                float rr = r - dpLocal(43);
                c.drawText(String.valueOf(i), cx + (float)Math.cos(a)*rr, cy + (float)Math.sin(a)*rr + dpLocal(5), text);
            }

            float pct = Math.max(0, Math.min(1, speed / 360f));
            float na = (float)Math.toRadians(218 + 284*pct);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dpLocal(4));
            p.setColor(Color.parseColor("#FF9E28"));
            c.drawLine(cx, cy, cx + (float)Math.cos(na)*(r-dpLocal(28)), cy + (float)Math.sin(na)*(r-dpLocal(28)), p);

            text.setTextSize(dpLocal(10));
            text.setColor(Color.parseColor("#9FAABC"));
            c.drawText("x1000 RPM", cx, cy-r*.50f, text);
        }

        private int dpLocal(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    }

    static class SparkView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final int mode;
        private final float[] vals = new float[42];

        SparkView(Context c, int color, int mode) {
            super(c);
            this.color = color;
            this.mode = mode;
            Random r = new Random(mode * 1000L + color);
            for (int i=0; i<vals.length; i++) vals[i] = .25f + r.nextFloat()*.65f;
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            p.setColor(color);
            p.setStrokeWidth(Math.max(2, getHeight()/12f));
            p.setStyle(Paint.Style.STROKE);
            p.setAlpha(210);
            int w=getWidth(), h=getHeight();
            if (mode == 2) {
                p.setStrokeWidth(Math.max(4, w/18f));
                p.setStyle(Paint.Style.FILL);
                for (int i=0;i<7;i++) {
                    float bw = w/11f;
                    float x = w*.18f + i*bw*1.2f;
                    float bh = h*(.25f + i*.095f);
                    p.setAlpha(80 + i*22);
                    c.drawRoundRect(x, h-bh, x+bw, h, bw/3, bw/3, p);
                }
                return;
            }
            for (int i=1; i<vals.length; i++) {
                float x1=(i-1)*w/(float)(vals.length-1), x2=i*w/(float)(vals.length-1);
                float y1=h - vals[i-1]*h, y2=h - vals[i]*h;
                c.drawLine(x1,y1,x2,y2,p);
            }
        }
    }
}
