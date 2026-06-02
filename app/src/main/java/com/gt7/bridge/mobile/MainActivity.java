package com.gt7.bridge.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String VERSION = "1.5.3";
    private static final String DATA_URL = "http://192.168.1.70:8787/api/fields";

    private static final int BG = Color.parseColor("#040811");
    private static final int PANEL = Color.parseColor("#071120");
    private static final int CARD = Color.parseColor("#09111D");
    private static final int CARD_2 = Color.parseColor("#0B1523");
    private static final int STROKE = Color.parseColor("#132238");
    private static final int TXT = Color.parseColor("#F3F7FF");
    private static final int MUTED = Color.parseColor("#7D8797");
    private static final int BLUE = Color.parseColor("#5EA2FF");
    private static final int CYAN = Color.parseColor("#2DE4FF");
    private static final int GREEN = Color.parseColor("#00F0A6");
    private static final int YELLOW = Color.parseColor("#FFCE52");
    private static final int ORANGE = Color.parseColor("#FFAD33");
    private static final int RED = Color.parseColor("#FF597B");
    private static final int PURPLE = Color.parseColor("#A26BFF");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HashMap<String, TextView> valueViews = new HashMap<>();
    private final HashMap<String, ProgressBar> barViews = new HashMap<>();
    private LinearLayout detailsContainer;
    private TextView detailsToggle;
    private TextView statusText;
    private TextView volanteText;
    private TextView rpmMiniText;
    private TextView marchaMiniText;
    private TextView ps5IpText;
    private TextView syncText;
    private TextView packetText;
    private boolean detailsOpen = true;
    private final Telemetry t = new Telemetry();

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            fetchTelemetry();
            handler.postDelayed(this, 650);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        handler.post(pollRunnable);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout shell = box(PANEL, 30, STROKE, 1);
        shell.setPadding(dp(16), dp(16), dp(16), dp(18));
        root.addView(shell);

        LinearLayout titleRow = hRow();
        View blueBar = new View(this);
        blueBar.setBackground(round(BLUE, 8, BLUE, 0));
        titleRow.addView(blueBar, new LinearLayout.LayoutParams(dp(6), dp(24)));
        TextView title = text("DASHBOARD TELEMETRIA", 18, BLUE, true);
        title.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.setMargins(dp(10), 0, 0, 0);
        titleRow.addView(title, tlp);
        shell.addView(titleRow);

        shell.addView(tabPanel());
        shell.addView(topStatusCard());
        shell.addView(sectionHeader("TELEMETRIA AO VIVO", BLUE));
        shell.addView(liveSection());
        shell.addView(sectionHeader("VOLTAS E RESULTADOS", GREEN));
        shell.addView(resultsSection());
        shell.addView(sectionHeader("MOTOR & DINÂMICA DO VEÍCULO", PURPLE));
        shell.addView(vehicleSection());
        shell.addView(coordHeader());
        shell.addView(divider());
        shell.addView(detailsToggleCard());
        shell.addView(detailsPanel());
    }

    private View tabPanel() {
        LinearLayout tabsWrap = box(Color.parseColor("#060D18"), 20, STROKE, 1);
        tabsWrap.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-1, -2);
        wlp.setMargins(0, dp(14), 0, dp(14));
        tabsWrap.setLayoutParams(wlp);

        LinearLayout row1 = hRow();
        row1.addView(tab("HUD TELEMETRIA", true), weight());
        row1.addView(space(10,1));
        row1.addView(tab("TODOS OS DADOS", false), weight());
        LinearLayout row2 = hRow();
        LinearLayout.LayoutParams r2lp = new LinearLayout.LayoutParams(-1, -2);
        r2lp.setMargins(0, dp(10), 0, 0);
        row2.setLayoutParams(r2lp);
        row2.addView(tab("VISUALIZAR MAPA", false), weight());
        row2.addView(space(10,1));
        row2.addView(tab("SESSÕES", false), weight());
        tabsWrap.addView(row1);
        tabsWrap.addView(row2);
        return tabsWrap;
    }

    private View topStatusCard() {
        LinearLayout card = box(CARD, 24, STROKE, 1);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.setMargins(0, dp(4), 0, dp(16));
        card.setLayoutParams(clp);

        LinearLayout row = hRow();
        statusText = pill("OFFLINE /\nDESCONECTADO", RED, Color.parseColor("#3B0D1D"));
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextSize(11);
        row.addView(statusText, new LinearLayout.LayoutParams(0, dp(72), 1));
        row.addView(space(10,1));

        LinearLayout mini = box(Color.parseColor("#04080F"), 18, Color.parseColor("#0F1829"), 1);
        mini.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.addView(mini, new LinearLayout.LayoutParams(0, dp(72), 1));
        LinearLayout top = hRow();
        mini.addView(top);
        top.addView(text("VOLANTE:", 9, MUTED, true)); top.addView(space(5,1));
        volanteText = text("0 KM/H", 11, TXT, true); top.addView(volanteText);
        top.addView(space(14,1)); top.addView(text("RPM:", 9, MUTED, true)); top.addView(space(5,1));
        rpmMiniText = text("0", 11, YELLOW, true); top.addView(rpmMiniText);
        top.addView(space(14,1)); top.addView(text("M:", 9, MUTED, true)); top.addView(space(5,1));
        marchaMiniText = text("N", 11, GREEN, true); top.addView(marchaMiniText);
        card.addView(row);

        LinearLayout ipBar = box(Color.parseColor("#060A10"), 16, Color.parseColor("#0E1726"), 1);
        ipBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(-1, -2);
        ipLp.setMargins(0, dp(12), 0, dp(12));
        ipBar.setLayoutParams(ipLp);
        LinearLayout ipRow = hRow();
        ipBar.addView(ipRow);
        ipRow.addView(text("PS5 IP:", 10, MUTED, true), new LinearLayout.LayoutParams(0,-2,1));
        ps5IpText = text("192.168.1.54", 11, YELLOW, true);
        ps5IpText.setGravity(Gravity.RIGHT);
        ipRow.addView(ps5IpText, new LinearLayout.LayoutParams(0,-2,1));
        card.addView(ipBar);

        card.addView(divider());

        LinearLayout info1 = hRow();
        LinearLayout.LayoutParams i1lp = new LinearLayout.LayoutParams(-1,-2);
        i1lp.setMargins(0, dp(12), 0, 0);
        info1.setLayoutParams(i1lp);
        info1.addView(text("Sincronização:", 12, MUTED, false), new LinearLayout.LayoutParams(0,-2,1));
        syncText = text("--:--:--", 12, TXT, true); syncText.setGravity(Gravity.RIGHT);
        info1.addView(syncText, new LinearLayout.LayoutParams(0,-2,1));
        card.addView(info1);

        LinearLayout info2 = hRow();
        LinearLayout.LayoutParams i2lp = new LinearLayout.LayoutParams(-1,-2);
        i2lp.setMargins(0, dp(12), 0, 0);
        info2.setLayoutParams(i2lp);
        info2.addView(text("Pacote UDP:", 12, MUTED, false), new LinearLayout.LayoutParams(0,-2,1));
        packetText = text("v1.4 (368 bytes)", 12, BLUE, true); packetText.setGravity(Gravity.RIGHT);
        info2.addView(packetText, new LinearLayout.LayoutParams(0,-2,1));
        card.addView(info2);

        LinearLayout actionRow1 = hRow();
        LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(-1,-2);
        a1.setMargins(0, dp(18), 0, 0); actionRow1.setLayoutParams(a1);
        actionRow1.setGravity(Gravity.RIGHT);
        actionRow1.addView(smallPill("● GRAVANDO", GREEN, Color.parseColor("#06271E")));
        actionRow1.addView(space(10,1));
        actionRow1.addView(smallPill("💾 SALVAR SESSÃO", YELLOW, Color.parseColor("#2E2511")));
        card.addView(actionRow1);

        LinearLayout actionRow2 = hRow();
        actionRow2.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(-1,-2);
        a2.setMargins(0, dp(10), 0, 0); actionRow2.setLayoutParams(a2);
        actionRow2.addView(space(1,1), new LinearLayout.LayoutParams(0,-2,1));
        actionRow2.addView(smallPill("⛶ FULLSCREEN HUD", BLUE, Color.parseColor("#11223E")));
        card.addView(actionRow2);
        return card;
    }

    private View liveSection() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(grid2(metricCard("VELOCIDADE", "velocidade", "km/h", TXT, "0"), metricCard("VELOCIDADE MÁXIMA", "velocidadeMaxima", "km/h", YELLOW, "102")));
        wrap.addView(grid2(metricCard("RPM ⚡", "rpm", "", TXT, "0"), metricCard("MARCHA ⚙", "marcha", "", GREEN, "N")));
        wrap.addView(progressCard("ACELERADOR", "acelerador", GREEN));
        wrap.addView(progressCard("FREIO", "freio", RED));
        wrap.addView(grid2(metricCard("COMBUSTÍVEL ⏻", "combustivel", "Liters", TXT, "--"), metricCard("COMBUSTÍVEL\nPORCENTAGEM", "combustivelPct", "%", CYAN, "--")));
        wrap.addView(grid2(metricCard("TEMPERATURA DA ÁGUA", "tempAgua", "", CYAN, "--"), metricCard("TEMPERATURA DO ÓLEO", "tempOleo", "", ORANGE, "--")));
        return wrap;
    }

    private View resultsSection() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(grid2(metricCard("MELHOR VOLTA", "melhorVolta", "", PURPLE, "--"), metricCard("ÚLTIMA VOLTA", "ultimaVolta", "", TXT, "--")));
        wrap.addView(grid2(metricCard("TEMPO TOTAL", "tempoTotal", "", CYAN, "--"), metricCard("VOLTAS BRUTAS", "voltasBrutas", "", TXT, "0")));
        wrap.addView(grid2(metricCard("VOLTAS CORRIGIDAS", "voltasCorrigidas", "", BLUE, "0"), ghostPillCard("VOLTA ALVO", "Auto")));
        wrap.addView(grid2(metricCard("ESTADO DA CORRIDA", "estadoCorrida", "", BLUE, "◔ EM ANDAMENTO"), metricCard("PARADAS BOXES", "paradasBoxes", "", TXT, "0")));
        return wrap;
    }

    private View vehicleSection() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(grid2(metricCard("PRESSÃO DO TURBO", "turbo", "", PURPLE, "--"), metricCard("VETORES VELOCIDADE", "vetorVelocidade", "", GREEN, "--")));
        wrap.addView(grid2(metricCard("ROTAÇÕES ROLL/PITCH", "rollPitch", "", TXT, "Pitch: --°\nRoll: --°"), metricCard("VETOR YAW", "yaw", "", ORANGE, "--")));
        return wrap;
    }

    private View coordHeader() {
        LinearLayout row = box(CARD, 20, STROKE, 1);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, dp(12));
        row.setLayoutParams(lp);
        LinearLayout inner = hRow();
        row.addView(inner);
        inner.addView(text("🗺 COORDENADAS E MAPA DO\nCIRCUITO", 13, PURPLE, true), new LinearLayout.LayoutParams(0,-2,1));
        TextView right = text("EXPANDIR\nCOORDENADAS ▼", 12, MUTED, true);
        right.setGravity(Gravity.RIGHT);
        inner.addView(right, new LinearLayout.LayoutParams(0,-2,1));
        return row;
    }

    private View detailsToggleCard() {
        detailsToggle = text("📋  RECOLHER DETALHES DO BRIDGE      ▲", 14, YELLOW, true);
        detailsToggle.setPadding(dp(18), dp(16), dp(18), dp(16));
        detailsToggle.setBackground(round(Color.parseColor("#35343B"), 22, Color.parseColor("#44424B"), 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(10));
        detailsToggle.setLayoutParams(lp);
        detailsToggle.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            detailsContainer.setVisibility(detailsOpen ? View.VISIBLE : View.GONE);
            detailsToggle.setText(detailsOpen ? "📋  RECOLHER DETALHES DO BRIDGE      ▲" : "📋  MOSTRAR DETALHES DO BRIDGE      ▼");
        });
        return detailsToggle;
    }

    private View detailsPanel() {
        detailsContainer = box(CARD, 20, STROKE, 1);
        detailsContainer.setPadding(dp(16), dp(16), dp(16), dp(16));
        detailsContainer.addView(text("Status: gt7.online aberto. Bridge de rede local ativo.", 12, MUTED, false));
        TextView desc = text("Listagem completa e numerada com índices fixos garantindo compatibilidade.", 12, MUTED, false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, -2); dlp.setMargins(0, dp(8), 0, dp(10));
        desc.setLayoutParams(dlp); detailsContainer.addView(desc);
        String[][] defs = {{"01","Conectado","connected"},{"02","Decodificação válida","decodeOk"},{"03","Status da leitura","statusLeitura"},{"04","Última atualização","ultimaAtualizacao"},{"05","Versão do pacote","versaoPacote"},{"06","Tamanho do pacote","tamanhoPacote"},{"07","Aviso / diagnóstico","diagnostico"},{"08","Velocidade atual","velocidadeDetalhe"}};
        for (String[] def : defs) detailsContainer.addView(detailRow(def[0], def[1], def[2]));
        return detailsContainer;
    }

    private View detailRow(String idx, String label, String key) {
        LinearLayout row = box(Color.parseColor("#080D15"), 16, Color.parseColor("#111824"), 1);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, 0); row.setLayoutParams(lp);
        LinearLayout inner = hRow(); row.addView(inner);
        TextView left = text(idx, 10, MUTED, true); left.setGravity(Gravity.CENTER); left.setBackground(round(Color.parseColor("#11151F"), 8, Color.parseColor("#1A2030"), 1)); left.setPadding(dp(8), dp(5), dp(8), dp(5)); inner.addView(left);
        TextView mid = text(label, 12, MUTED, true); LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,-2,1); mlp.setMargins(dp(10),0,dp(10),0); inner.addView(mid, mlp);
        TextView right = text("--", 12, TXT, true); right.setGravity(Gravity.CENTER); right.setBackground(round(Color.parseColor("#111621"), 10, Color.parseColor("#202A3C"), 1)); right.setPadding(dp(12), dp(6), dp(12), dp(6)); inner.addView(right);
        valueViews.put(key, right);
        return row;
    }

    private View metricCard(String title, String key, String unit, int color, String initial) {
        LinearLayout c = box(CARD_2, 22, STROKE, 1);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setLayoutParams(new LinearLayout.LayoutParams(0, dp(118), 1));
        TextView t1 = text(title, 11, MUTED, true); t1.setLetterSpacing(0.04f); c.addView(t1);
        TextView val = text(initial != null ? initial : "--", 22, color, true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, 0, 1); vlp.setMargins(0, dp(14), 0, 0); val.setLayoutParams(vlp); val.setGravity(Gravity.BOTTOM|Gravity.LEFT); c.addView(val);
        if (unit != null && unit.length() > 0) c.addView(text(unit, 10, MUTED, false));
        valueViews.put(key, val);
        return c;
    }

    private View ghostPillCard(String title, String value) {
        LinearLayout c = box(CARD_2, 22, STROKE, 1);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.addView(text(title, 11, MUTED, true));
        TextView ghost = text(value, 18, MUTED, true); ghost.setGravity(Gravity.CENTER); ghost.setBackground(round(Color.parseColor("#06090E"), 22, Color.parseColor("#22262D"), 1));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, dp(40)); glp.setMargins(0, dp(18), 0, 0); c.addView(ghost, glp);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, dp(118), 1));
        return c;
    }

    private View progressCard(String title, String key, int color) {
        LinearLayout c = box(CARD_2, 22, STROKE, 1);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(96)); lp.setMargins(0, dp(10), 0, 0); c.setLayoutParams(lp);
        LinearLayout head = hRow(); c.addView(head);
        head.addView(text(title, 11, MUTED, true), new LinearLayout.LayoutParams(0,-2,1));
        TextView pct = text("0%", 11, color, true); head.addView(pct); valueViews.put(key, pct);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgress(0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, dp(18)); blp.setMargins(0, dp(18), 0, 0); c.addView(bar, blp); barViews.put(key, bar);
        return c;
    }

    private View grid2(View a, View b) {
        LinearLayout row = hRow(); LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2); rlp.setMargins(0, dp(10), 0, 0); row.setLayoutParams(rlp); row.addView(a); row.addView(space(10,1)); row.addView(b); return row;
    }

    private void fetchTelemetry() {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(DATA_URL).openConnection();
                conn.setConnectTimeout(1200); conn.setReadTimeout(1200);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); br.close();
                t.fromJson(new JSONObject(sb.toString()));
            } catch (Exception e) { t.offline(); }
            handler.post(this::applyTelemetry);
        }).start();
    }

    private void applyTelemetry() {
        statusText.setText(t.connected ? (t.decodeOk ? "CONECTADO\nAO PS5" : "ONLINE /\nPACOTE") : "OFFLINE /\nDESCONECTADO");
        statusText.setTextColor(t.connected ? (t.decodeOk ? GREEN : YELLOW) : RED);
        statusText.setBackground(round(t.connected ? Color.parseColor("#0A3022") : Color.parseColor("#3B0D1D"), 18, t.connected ? Color.parseColor("#145437") : Color.parseColor("#572234"), 1));
        volanteText.setText(t.velocidade + " KM/H"); rpmMiniText.setText(t.rpm); marchaMiniText.setText(t.marcha); ps5IpText.setText(t.ps5Ip); syncText.setText(t.lastUpdateFormatted()); packetText.setText(t.packetVersion + " (" + t.packetSize + " bytes)");
        setValue("velocidade", t.velocidade); setValue("velocidadeMaxima", t.velocidadeMaxima); setValue("rpm", t.rpm); setValue("marcha", t.marcha); setValue("combustivel", t.combustivel); setValue("combustivelPct", t.combustivelPct); setValue("tempAgua", t.tempAgua); setValue("tempOleo", t.tempOleo); setValue("melhorVolta", t.melhorVolta); setValue("ultimaVolta", t.ultimaVolta); setValue("tempoTotal", t.tempoTotal); setValue("voltasBrutas", t.voltasBrutas); setValue("voltasCorrigidas", t.voltasCorrigidas); setValue("estadoCorrida", t.estadoCorrida); setValue("paradasBoxes", t.paradasBoxes); setValue("turbo", t.turbo); setValue("vetorVelocidade", t.vetorVelocidade); setValue("rollPitch", t.rollPitch); setValue("yaw", t.yaw); setValue("acelerador", t.acelerador + "%"); setValue("freio", t.freio + "%"); setBar("acelerador", t.acelerador); setBar("freio", t.freio);
        setValue("connected", t.connected ? "Sim" : "Não"); setValue("decodeOk", t.decodeOk ? "Sim" : "Não"); setValue("statusLeitura", t.connected ? (t.decodeOk ? "Conectado ao PS5" : "Online / Pacote") : "Offline / Desconectado"); setValue("ultimaAtualizacao", t.lastUpdateFormatted()); setValue("versaoPacote", t.packetVersion); setValue("tamanhoPacote", t.packetSize + " bytes"); setValue("diagnostico", t.warning); setValue("velocidadeDetalhe", t.velocidade + " km/h");
    }

    private void setValue(String key, String value) { TextView tv = valueViews.get(key); if (tv != null) tv.setText(value == null || value.trim().isEmpty() ? "--" : value); }
    private void setBar(String key, int value) { ProgressBar pb = barViews.get(key); if (pb != null) pb.setProgress(Math.max(0, Math.min(100, value))); }
    private LinearLayout box(int color, int radiusDp, int stroke, int strokeDp) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(round(color, radiusDp, stroke, strokeDp)); return l; }
    private TextView tab(String title, boolean active) { TextView t = text(title, 12, active ? Color.parseColor("#0A1119") : MUTED, true); t.setGravity(Gravity.CENTER); t.setBackground(round(active ? BLUE : Color.TRANSPARENT, 16, active ? BLUE : Color.TRANSPARENT, 0)); t.setPadding(dp(10), dp(12), dp(10), dp(12)); return t; }
    private TextView pill(String label, int textColor, int bgColor) { TextView t = text(label, 12, textColor, true); t.setPadding(dp(14), dp(12), dp(14), dp(12)); t.setBackground(round(bgColor, 18, lighten(bgColor), 1)); return t; }
    private TextView smallPill(String label, int textColor, int bgColor) { TextView t = text(label, 12, textColor, true); t.setPadding(dp(16), dp(10), dp(16), dp(10)); t.setBackground(round(bgColor, 18, lighten(bgColor), 1)); return t; }
    private TextView sectionHeader(String text, int color) { TextView t = text("▌ " + text, 15, color, true); t.setLetterSpacing(0.05f); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(12), 0, dp(4)); t.setLayoutParams(lp); return t; }
    private View divider() { View v = new View(this); v.setBackgroundColor(Color.parseColor("#152131")); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1)); lp.setMargins(0, dp(14), 0, dp(8)); v.setLayoutParams(lp); return v; }
    private LinearLayout hRow() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String s, int sp, int color, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextColor(color); t.setTextSize(sp); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); return t; }
    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp)); if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor); return d; }
    private int lighten(int color) { return Color.rgb(Math.min(255, (int)(Color.red(color)*1.25)), Math.min(255, (int)(Color.green(color)*1.25)), Math.min(255, (int)(Color.blue(color)*1.25))); }
    private Space space(int dpW, int dpH) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(dpW), dp(dpH))); return s; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    static class Telemetry {
        boolean connected = false, decodeOk = false;
        String ps5Ip = "192.168.1.54"; long updatedAt = 0; int packetSize = 376; String packetVersion = "v1.4";
        String velocidade = "0", velocidadeMaxima = "102", rpm = "0", marcha = "N"; int acelerador = 0, freio = 0;
        String combustivel = "--", combustivelPct = "--", tempAgua = "--", tempOleo = "--", melhorVolta = "--", ultimaVolta = "--", tempoTotal = "--", voltasBrutas = "0", voltasCorrigidas = "0", estadoCorrida = "◔ EM ANDAMENTO", paradasBoxes = "0", turbo = "--", vetorVelocidade = "--", rollPitch = "Pitch: --°\nRoll: --°", yaw = "--", warning = "Sem fluxo de dados";
        void fromJson(JSONObject j) { connected = j.optBoolean("connected", false); decodeOk = j.optBoolean("decodeOk", false); updatedAt = j.optLong("updatedAt", System.currentTimeMillis()); packetSize = j.optInt("packetSize", 376); String pv = j.optString("packetVersion", "C"); packetVersion = pv != null && pv.length() == 1 ? "v1.4" : pv; ps5Ip = j.optString("ps5Ip", ps5Ip); velocidade = fmtInt(j,"velocidade",velocidade); velocidadeMaxima = fmtInt(j,"velocidadeMaxima",velocidadeMaxima); rpm = fmtInt(j,"rpm",rpm); marcha = j.optString("marcha",marcha); acelerador = j.optInt("acelerador",acelerador); freio = j.optInt("freio",freio); combustivel = fmt2(j,"combustivel",combustivel); combustivelPct = fmt2(j,"combustivelPorcentagem",combustivelPct); melhorVolta = j.optString("melhorVolta",melhorVolta); ultimaVolta = j.optString("ultimaVolta",ultimaVolta); tempoTotal = j.optString("tempoTotalCorrida",tempoTotal); voltasBrutas = String.valueOf(j.optInt("voltasCompletadas", parseIntSafe(voltasBrutas))); voltasCorrigidas = String.valueOf(j.optInt("voltasCorrigidas", parseIntSafe(voltasCorrigidas))); warning = j.optString("warning", connected ? "Dados fluindo normalmente" : "Sem fluxo de dados"); }
        void offline() { connected = false; decodeOk = true; updatedAt = System.currentTimeMillis(); warning = "Sem fluxo de dados"; }
        String lastUpdateFormatted() { return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(updatedAt == 0 ? System.currentTimeMillis() : updatedAt)); }
        private static String fmtInt(JSONObject j, String key, String fallback) { if (!j.has(key)) return fallback; return String.valueOf((int)Math.round(j.optDouble(key, 0))); }
        private static String fmt2(JSONObject j, String key, String fallback) { if (!j.has(key)) return fallback; return String.format(Locale.US, "%.0f", j.optDouble(key, 0)); }
        private static int parseIntSafe(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    }
}
