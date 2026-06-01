package com.gt7.bridge.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final String VERSION = "1.5.2";
    private static final String DEFAULT_RASPBERRY_URL = "http://192.168.1.70:8787/api/fields";
    private static final String DEFAULT_PS5_IP = "192.168.1.54";

    private static final int BG = Color.rgb(3, 6, 14);
    private static final int PANEL = Color.rgb(8, 12, 24);
    private static final int CARD = Color.rgb(12, 18, 32);
    private static final int CARD_2 = Color.rgb(16, 24, 42);
    private static final int BORDER = Color.rgb(34, 48, 76);
    private static final int TXT = Color.rgb(244, 248, 255);
    private static final int MUTED = Color.rgb(137, 154, 183);
    private static final int BLUE = Color.rgb(72, 153, 255);
    private static final int CYAN = Color.rgb(0, 229, 255);
    private static final int GREEN = Color.rgb(0, 235, 149);
    private static final int YELLOW = Color.rgb(255, 214, 64);
    private static final int PURPLE = Color.rgb(174, 111, 255);
    private static final int RED = Color.rgb(255, 74, 104);
    private static final int ORANGE = Color.rgb(255, 153, 48);

    private final AtomicBoolean raspberryPolling = new AtomicBoolean(false);
    private final AtomicBoolean udpFallbackRunning = new AtomicBoolean(false);
    private final Telemetry t = new Telemetry();

    private EditText raspberryUrl;
    private EditText ps5Ip;
    private LinearLayout dashboard;
    private LinearLayout detailsBody;
    private TextView detailsToggle;
    private TextView statusBadge;
    private TextView sourceBadge;
    private TextView syncValue;
    private TextView packetValue;
    private boolean detailsOpen = false;
    private final HashMap<String, TextView> values = new HashMap<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { runOnUiThread(() -> refreshUi()); }
        }, 250, 500);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        dashboard = new LinearLayout(this);
        dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setPadding(dp(14), dp(14), dp(14), dp(28));
        scroll.addView(dashboard, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout shell = panel();
        dashboard.addView(shell);

        TextView title = label("GT7 RACE DASHBOARD", 22, TXT, true);
        title.setLetterSpacing(0.08f);
        shell.addView(title);

        TextView sub = label("Racing Telemetry • Raspberry / UDP • v" + VERSION, 11, MUTED, true);
        sub.setPadding(0, dp(2), 0, dp(12));
        shell.addView(sub);

        buildTopTabs(shell);
        buildControlTower(shell);
        buildLivePanel(shell);
        buildTimingPanel(shell);
        buildPowertrainPanel(shell);
        buildTrackPanel(shell);
        buildPitWall(shell);
    }

    private void buildTopTabs(LinearLayout root) {
        LinearLayout tabs = card(CARD, dp(16));
        tabs.setPadding(dp(6), dp(6), dp(6), dp(6));
        root.addView(tabs);

        LinearLayout row1 = row();
        LinearLayout row2 = row();
        tabs.addView(row1);
        tabs.addView(row2);
        row1.addView(tab("HUD CORRIDA", true), new LinearLayout.LayoutParams(0, dp(38), 1));
        row1.addView(tab("DATA LOGGER", false), new LinearLayout.LayoutParams(0, dp(38), 1));
        row2.addView(tab("MAPA / TRAÇADO", false), new LinearLayout.LayoutParams(0, dp(38), 1));
        row2.addView(tab("SESSÕES", false), new LinearLayout.LayoutParams(0, dp(38), 1));
    }

    private void buildControlTower(LinearLayout root) {
        root.addView(section("PIT WALL / CONTROLE", BLUE));
        LinearLayout control = card(CARD, dp(18));
        root.addView(control);

        LinearLayout top = row();
        statusBadge = chip("OFFLINE", RED);
        sourceBadge = chip("Raspberry", BLUE);
        top.addView(statusBadge);
        top.addView(space(dp(8), 1));
        top.addView(sourceBadge);
        control.addView(top);

        TextView endpointTitle = label("ENDPOINT RASPBERRY", 10, MUTED, true);
        endpointTitle.setPadding(0, dp(12), 0, dp(4));
        control.addView(endpointTitle);

        raspberryUrl = input("URL Raspberry Bridge", getPref("raspberry_url", DEFAULT_RASPBERRY_URL));
        raspberryUrl.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ setPref("raspberry_url", s.toString()); } public void afterTextChanged(Editable e){} });
        control.addView(raspberryUrl, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView ps5Title = label("IP PS5 PARA UDP DIRETO", 10, MUTED, true);
        ps5Title.setPadding(0, dp(10), 0, dp(4));
        control.addView(ps5Title);
        ps5Ip = input("IP PS5", getPref("ps5_ip", DEFAULT_PS5_IP));
        ps5Ip.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ setPref("ps5_ip", s.toString()); } public void afterTextChanged(Editable e){} });
        control.addView(ps5Ip, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout buttons1 = row();
        buttons1.setPadding(0, dp(12), 0, 0);
        Button startRasp = action("RASPBERRY PIT LINK", GREEN);
        Button stop = action("STOP", RED);
        buttons1.addView(startRasp, new LinearLayout.LayoutParams(0, dp(44), 1));
        buttons1.addView(stop, new LinearLayout.LayoutParams(0, dp(44), 1));
        control.addView(buttons1);

        LinearLayout buttons2 = row();
        buttons2.setPadding(0, dp(8), 0, 0);
        Button udp = action("UDP DIRETO", BLUE);
        Button save = action("SALVAR STINT", YELLOW);
        buttons2.addView(udp, new LinearLayout.LayoutParams(0, dp(44), 1));
        buttons2.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));
        control.addView(buttons2);

        LinearLayout info = row();
        info.setPadding(0, dp(12), 0, 0);
        syncValue = smallInfo("SINCRONIZAÇÃO", "--:--:--", GREEN);
        packetValue = smallInfo("PACOTE", "? / 0", BLUE);
        info.addView(syncValue, new LinearLayout.LayoutParams(0, -2, 1));
        info.addView(packetValue, new LinearLayout.LayoutParams(0, -2, 1));
        control.addView(info);

        startRasp.setOnClickListener(v -> startRaspberryBridge());
        stop.setOnClickListener(v -> stopAll());
        udp.setOnClickListener(v -> startUdpFallback());
        save.setOnClickListener(v -> saveSession());
    }

    private void buildLivePanel(LinearLayout root) {
        root.addView(section("PAINEL DE CORRIDA", BLUE));
        LinearLayout big = row();
        root.addView(big);
        big.addView(bigCard("VELOCIDADE", "velocidade", "km/h", TXT), new LinearLayout.LayoutParams(0, dp(132), 1));
        big.addView(bigCard("RPM", "rpm", "", YELLOW), new LinearLayout.LayoutParams(0, dp(132), 1));
        big.addView(bigCard("MARCHA", "marcha", "", GREEN), new LinearLayout.LayoutParams(0, dp(132), 1));

        LinearLayout line = row();
        root.addView(line);
        line.addView(progressCard("ACELERADOR", "acelerador", GREEN), new LinearLayout.LayoutParams(0, dp(92), 1));
        line.addView(progressCard("FREIO", "freio", RED), new LinearLayout.LayoutParams(0, dp(92), 1));

        LinearLayout fuel = row();
        root.addView(fuel);
        fuel.addView(metricCard("COMBUSTÍVEL", "combustivel", "L", TXT), new LinearLayout.LayoutParams(0, dp(105), 1));
        fuel.addView(metricCard("COMBUSTÍVEL", "combustivelPct", "%", CYAN), new LinearLayout.LayoutParams(0, dp(105), 1));
        fuel.addView(metricCard("V. MÁXIMA", "velocidadeMaxima", "km/h", YELLOW), new LinearLayout.LayoutParams(0, dp(105), 1));
    }

    private void buildTimingPanel(LinearLayout root) {
        root.addView(section("TIMING & RESULTADO", GREEN));
        LinearLayout r1 = row();
        root.addView(r1);
        r1.addView(metricCard("MELHOR VOLTA", "melhorVolta", "", PURPLE), new LinearLayout.LayoutParams(0, dp(112), 1));
        r1.addView(metricCard("ÚLTIMA VOLTA", "ultimaVolta", "", TXT), new LinearLayout.LayoutParams(0, dp(112), 1));
        r1.addView(metricCard("TEMPO TOTAL", "tempoTotal", "", CYAN), new LinearLayout.LayoutParams(0, dp(112), 1));

        LinearLayout r2 = row();
        root.addView(r2);
        r2.addView(metricCard("VOLTAS BRUTAS", "voltasBrutas", "", TXT), new LinearLayout.LayoutParams(0, dp(105), 1));
        r2.addView(metricCard("VOLTAS CORRIGIDAS", "voltasCorrigidas", "", BLUE), new LinearLayout.LayoutParams(0, dp(105), 1));
        r2.addView(metricCard("ESTADO", "estadoCorrida", "", GREEN), new LinearLayout.LayoutParams(0, dp(105), 1));
    }

    private void buildPowertrainPanel(LinearLayout root) {
        root.addView(section("POWERTRAIN & DINÂMICA", PURPLE));
        LinearLayout r = row();
        root.addView(r);
        r.addView(metricCard("TANQUE", "fuelCapacity", "L", TXT), new LinearLayout.LayoutParams(0, dp(105), 1));
        r.addView(metricCard("COORD X", "posX", "", GREEN), new LinearLayout.LayoutParams(0, dp(105), 1));
        r.addView(metricCard("COORD Y", "posY", "", ORANGE), new LinearLayout.LayoutParams(0, dp(105), 1));
        r.addView(metricCard("COORD Z", "posZ", "", BLUE), new LinearLayout.LayoutParams(0, dp(105), 1));
    }

    private void buildTrackPanel(LinearLayout root) {
        LinearLayout box = card(CARD, dp(16));
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(box);
        TextView h = label("▥ TRAÇADO & COORDENADAS", 13, PURPLE, true);
        h.setLetterSpacing(0.08f);
        box.addView(h);
        TextView desc = label("Rastro do circuito vindo do Raspberry Bridge. Use a aba Data Logger para inspeção completa.", 11, MUTED, false);
        desc.setPadding(0, dp(6), 0, 0);
        box.addView(desc);
    }

    private void buildPitWall(LinearLayout root) {
        detailsToggle = label("▣ MOSTRAR PIT WALL / DETALHES", 13, YELLOW, true);
        detailsToggle.setPadding(dp(14), dp(12), dp(14), dp(12));
        detailsToggle.setBackground(round(Color.rgb(28, 29, 35), dp(16), Color.rgb(50, 50, 60)));
        root.addView(detailsToggle);

        detailsBody = card(CARD, dp(16));
        detailsBody.setVisibility(View.GONE);
        root.addView(detailsBody);
        String[][] rows = {
                {"Fonte", "fonte"}, {"Conectado", "connected"}, {"Decode", "decodeOk"}, {"Status", "status"},
                {"Atualizado", "updatedAt"}, {"Pacote", "packetFull"}, {"Aviso", "warning"},
                {"Endpoint", "endpoint"}, {"PS5", "ps5"}
        };
        for(String[] r: rows) detailsBody.addView(detailRow(r[0], r[1]));
        detailsToggle.setOnClickListener(v -> {
            detailsOpen = !detailsOpen;
            detailsBody.setVisibility(detailsOpen ? View.VISIBLE : View.GONE);
            detailsToggle.setText(detailsOpen ? "▣ RECOLHER PIT WALL / DETALHES" : "▣ MOSTRAR PIT WALL / DETALHES");
        });
    }

    private void startRaspberryBridge() {
        raspberryPolling.set(true);
        udpFallbackRunning.set(false);
        setSource("RASPBERRY PIT LINK", GREEN);
        Toast.makeText(this, "Raspberry Bridge conectado", Toast.LENGTH_SHORT).show();
        Thread th = new Thread(this::raspberryLoop);
        th.setName("GT7-Raspberry-Bridge");
        th.start();
    }

    private void raspberryLoop() {
        while(raspberryPolling.get()) {
            try {
                String urlText = raspberryUrl.getText().toString().trim();
                if(urlText.length() == 0) urlText = DEFAULT_RASPBERRY_URL;
                HttpURLConnection conn = (HttpURLConnection)new URL(urlText).openConnection();
                conn.setConnectTimeout(1400);
                conn.setReadTimeout(1400);
                conn.setRequestMethod("GET");
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject j = new JSONObject(sb.toString());
                synchronized(t) { t.fromJson(j, urlText, ps5Ip.getText().toString()); }
            } catch(Exception e) {
                synchronized(t) { t.connected=false; t.decodeOk=false; t.status="raspberry_offline"; t.warning=e.getMessage(); }
            }
            try { Thread.sleep(500); } catch(Exception ignored) {}
        }
    }

    private void startUdpFallback() {
        raspberryPolling.set(false);
        udpFallbackRunning.set(true);
        setSource("UDP DIRETO", BLUE);
        Toast.makeText(this, "UDP direto iniciado", Toast.LENGTH_SHORT).show();
        new Thread(this::udpHeartbeat).start();
        new Thread(this::udpReceiveOnly).start();
    }

    private void udpHeartbeat() {
        while(udpFallbackRunning.get()) {
            try {
                String ip = ps5Ip.getText().toString().trim();
                DatagramSocket s = new DatagramSocket();
                byte[] hb = "C".getBytes("UTF-8");
                s.send(new DatagramPacket(hb, hb.length, InetAddress.getByName(ip), 33739));
                s.close();
                Thread.sleep(2000);
            } catch(Exception ignored) {}
        }
    }

    private void udpReceiveOnly() {
        try(DatagramSocket socket = new DatagramSocket(33740)) {
            socket.setSoTimeout(1000);
            byte[] buf = new byte[4096];
            while(udpFallbackRunning.get()) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    synchronized(t) {
                        t.connected = true;
                        t.decodeOk = false;
                        t.status = "udp_recebendo_sem_decoder_local";
                        t.packetSize = p.getLength();
                        t.packetVersion = packetName(p.getLength());
                        t.updatedAt = System.currentTimeMillis();
                        t.warning = "Use Raspberry Bridge para dados decodificados completos.";
                    }
                } catch(SocketTimeoutException ignored) {}
            }
        } catch(Exception e) {
            synchronized(t) { t.warning = e.getMessage(); }
        }
    }

    private void stopAll() {
        raspberryPolling.set(false);
        udpFallbackRunning.set(false);
        synchronized(t) { t.status = "parado"; }
        setSource("PARADO", RED);
    }

    private void saveSession() {
        String snap;
        synchronized(t) { snap = t.toJson(); }
        getSharedPreferences("gt7_bridge", MODE_PRIVATE).edit().putString("last_session", snap).apply();
        ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("GT7 Stint", snap));
        Toast.makeText(this, "Stint salvo e copiado", Toast.LENGTH_SHORT).show();
    }

    private void refreshUi() {
        Telemetry s;
        synchronized(t) { s = t.copy(); }
        statusBadge.setText(s.connected ? (s.decodeOk ? "● ONLINE" : "● PACOTE") : "● OFFLINE");
        statusBadge.setTextColor(s.connected ? (s.decodeOk ? GREEN : YELLOW) : RED);
        syncValue.setText("SINCRONIZAÇÃO\n" + (s.updatedAt > 0 ? new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(s.updatedAt)) : "--:--:--"));
        packetValue.setText("PACOTE\n" + s.packetVersion + " / " + s.packetSize + " bytes");
        set("velocidade", s.velocidade);
        set("velocidadeMaxima", s.velocidadeMaxima);
        set("rpm", s.rpm);
        set("marcha", s.marcha);
        set("acelerador", s.acelerador + "%");
        set("freio", s.freio + "%");
        set("combustivel", s.combustivel);
        set("combustivelPct", s.combustivelPorcentagem);
        set("fuelCapacity", s.fuelCapacity);
        set("melhorVolta", s.melhorVolta);
        set("ultimaVolta", s.ultimaVolta);
        set("tempoTotal", s.tempoTotalCorrida);
        set("voltasBrutas", String.valueOf(s.voltasCompletadas));
        set("voltasCorrigidas", String.valueOf(s.voltasCorrigidas));
        set("estadoCorrida", s.decodeOk ? "EM PISTA" : "AGUARDANDO");
        set("posX", s.posX);
        set("posY", s.posY);
        set("posZ", s.posZ);
        set("fonte", s.source);
        set("connected", String.valueOf(s.connected));
        set("decodeOk", String.valueOf(s.decodeOk));
        set("status", s.status);
        set("updatedAt", s.updatedAt > 0 ? String.valueOf(s.updatedAt) : "--");
        set("packetFull", s.packetVersion + " / " + s.packetSize);
        set("warning", s.warning == null || s.warning.length()==0 ? "--" : s.warning);
        set("endpoint", s.endpoint);
        set("ps5", s.ps5Ip);
    }

    private void setSource(String text, int color) { sourceBadge.setText(text); sourceBadge.setTextColor(color); }
    private void set(String key, String value) { TextView v = values.get(key); if(v != null) v.setText(value == null || value.length()==0 || value.equals("null") ? "--" : value); }
    private String packetName(int size) { if(size==368)return "C"; if(size==344)return "~"; if(size==316)return "B"; if(size==296)return "A"; return "?"; }

    private LinearLayout panel() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(14), dp(14), dp(14), dp(18)); l.setBackground(round(PANEL, dp(22), BORDER)); return l; }
    private LinearLayout card(int color, int radius) { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(10), dp(10), dp(10), dp(10)); l.setBackground(round(color, radius, BORDER)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(6), 0, dp(8)); l.setLayoutParams(lp); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView label(String text, int sp, int color, boolean bold) { TextView v = new TextView(this); v.setText(text); v.setTextSize(sp); v.setTextColor(color); if(bold)v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return v; }
    private TextView section(String text, int color) { TextView v = label("▌ " + text, 13, color, true); v.setLetterSpacing(0.08f); v.setPadding(0, dp(16), 0, dp(6)); return v; }
    private TextView tab(String text, boolean active) { TextView v = label(text, 10, active ? Color.BLACK : TXT, true); v.setGravity(Gravity.CENTER); v.setLetterSpacing(0.08f); v.setBackground(round(active ? BLUE : Color.TRANSPARENT, dp(12), active ? BLUE : Color.TRANSPARENT)); return v; }
    private TextView chip(String text, int color) { TextView v = label(text, 11, color, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(10), dp(7), dp(10), dp(7)); v.setBackground(round(Color.argb(38, Color.red(color), Color.green(color), Color.blue(color)), dp(14), color)); return v; }
    private Button action(String text, int color) { Button b = new Button(this); b.setText(text); b.setTextSize(11); b.setTextColor(color == YELLOW ? Color.rgb(30,22,0) : Color.WHITE); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(round(color, dp(14), color)); return b; }
    private EditText input(String hint, String value) { EditText e = new EditText(this); e.setText(value); e.setHint(hint); e.setSingleLine(true); e.setTextSize(12); e.setTextColor(TXT); e.setHintTextColor(MUTED); e.setPadding(dp(12), 0, dp(12), 0); e.setBackground(round(CARD_2, dp(14), BORDER)); return e; }
    private TextView smallInfo(String label, String value, int color) { TextView v = label(label + "\n" + value, 11, color, true); v.setGravity(Gravity.CENTER); v.setPadding(dp(8), dp(8), dp(8), dp(8)); v.setBackground(round(CARD_2, dp(14), BORDER)); return v; }
    private LinearLayout bigCard(String title, String key, String unit, int color) { LinearLayout c = card(CARD, dp(18)); c.setPadding(dp(10), dp(10), dp(10), dp(10)); c.addView(label(title, 10, MUTED, true)); TextView value = label("--", 34, color, true); value.setGravity(Gravity.CENTER); value.setPadding(0, dp(4), 0, 0); c.addView(value, new LinearLayout.LayoutParams(-1, 0, 1)); TextView u = label(unit, 10, MUTED, false); u.setGravity(Gravity.CENTER); c.addView(u); values.put(key, value); return c; }
    private LinearLayout metricCard(String title, String key, String unit, int color) { LinearLayout c = card(CARD, dp(16)); c.setPadding(dp(10), dp(10), dp(10), dp(10)); c.addView(label(title, 9, MUTED, true)); TextView value = label("--", 20, color, true); value.setPadding(0, dp(10), 0, 0); c.addView(value); TextView u = label(unit, 9, MUTED, false); c.addView(u); values.put(key, value); return c; }
    private LinearLayout progressCard(String title, String key, int color) { LinearLayout c = card(CARD, dp(16)); c.addView(label(title, 10, MUTED, true)); ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); pb.setMax(100); pb.setProgress(0); TextView value = label("0%", 14, color, true); value.setGravity(Gravity.RIGHT); c.addView(value); c.addView(pb, new LinearLayout.LayoutParams(-1, dp(18))); values.put(key, value); return c; }
    private LinearLayout detailRow(String label, String key) { LinearLayout r = row(); r.setPadding(0, dp(6), 0, dp(6)); r.addView(label(label, 11, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1)); TextView v = label("--", 11, TXT, true); v.setGravity(Gravity.RIGHT); r.addView(v, new LinearLayout.LayoutParams(0, -2, 1)); values.put(key, v); return r; }
    private Space space(int w, int h) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(w, h)); return s; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if(stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private String getPref(String k, String def) { return getSharedPreferences("gt7_bridge", MODE_PRIVATE).getString(k, def); }
    private void setPref(String k, String v) { getSharedPreferences("gt7_bridge", MODE_PRIVATE).edit().putString(k, v).apply(); }

    static class Telemetry {
        boolean connected=false, decodeOk=false;
        long updatedAt=0;
        int packetSize=0, voltasCompletadas=0, voltasCorrigidas=0;
        String packetVersion="?", status="offline", source="Raspberry", endpoint="", ps5Ip="", warning="";
        String velocidade="0", velocidadeMaxima="0", rpm="0", marcha="N", acelerador="0%", freio="0%";
        String combustivel="--", combustivelPorcentagem="--", fuelCapacity="--";
        String melhorVolta="--", ultimaVolta="--", tempoTotalCorrida="--";
        String posX="--", posY="--", posZ="--";
        void fromJson(JSONObject j, String ep, String ps5) {
            source = "Raspberry"; endpoint = ep; ps5Ip = ps5;
            connected = j.optBoolean("connected", false); decodeOk = j.optBoolean("decodeOk", false);
            status = j.optString("status", "--"); updatedAt = j.optLong("updatedAt", System.currentTimeMillis());
            packetSize = j.optInt("packetSize", j.optInt("lastPacketSize", 0)); packetVersion = j.optString("packetVersion", "?");
            velocidade = String.valueOf(j.optInt("velocidade", 0)); velocidadeMaxima = String.valueOf(j.optInt("velocidadeMaxima", 0));
            rpm = String.valueOf(j.optInt("rpm", 0)); marcha = j.optString("marcha", "N");
            acelerador = j.optInt("acelerador", 0) + "%"; freio = j.optInt("freio", 0) + "%";
            combustivel = fmt(j, "combustivel"); combustivelPorcentagem = fmt(j, "combustivelPorcentagem"); fuelCapacity = fmt(j, "fuelCapacity");
            melhorVolta = j.optString("melhorVolta", "--"); ultimaVolta = j.optString("ultimaVolta", "--"); tempoTotalCorrida = j.optString("tempoTotalCorrida", "--");
            voltasCompletadas = j.optInt("voltasCompletadas", 0); voltasCorrigidas = j.optInt("voltasCorrigidas", Math.max(0, voltasCompletadas - 1));
            JSONObject p = j.optJSONObject("position"); if(p != null){ posX = fmt(p,"x"); posY = fmt(p,"y"); posZ = fmt(p,"z"); }
            warning = j.optString("warning", "");
        }
        String fmt(JSONObject j, String k) { if(!j.has(k) || j.isNull(k)) return "--"; double d = j.optDouble(k, 0); if(Double.isNaN(d) || Double.isInfinite(d)) return "--"; return String.format(Locale.US, "%.2f", d); }
        Telemetry copy() { Telemetry n = new Telemetry(); n.connected=connected; n.decodeOk=decodeOk; n.updatedAt=updatedAt; n.packetSize=packetSize; n.voltasCompletadas=voltasCompletadas; n.voltasCorrigidas=voltasCorrigidas; n.packetVersion=packetVersion; n.status=status; n.source=source; n.endpoint=endpoint; n.ps5Ip=ps5Ip; n.warning=warning; n.velocidade=velocidade; n.velocidadeMaxima=velocidadeMaxima; n.rpm=rpm; n.marcha=marcha; n.acelerador=acelerador; n.freio=freio; n.combustivel=combustivel; n.combustivelPorcentagem=combustivelPorcentagem; n.fuelCapacity=fuelCapacity; n.melhorVolta=melhorVolta; n.ultimaVolta=ultimaVolta; n.tempoTotalCorrida=tempoTotalCorrida; n.posX=posX; n.posY=posY; n.posZ=posZ; return n; }
        String toJson() { return "{\"source\":\""+source+"\",\"status\":\""+status+"\",\"velocidade\":\""+velocidade+"\",\"rpm\":\""+rpm+"\",\"marcha\":\""+marcha+"\",\"tempoTotal\":\""+tempoTotalCorrida+"\"}"; }
    }
}
