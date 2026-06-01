import fs from 'node:fs';

const file = 'app/src/main/java/com/gt7/bridge/mobile/MainActivity.java';
let src = fs.readFileSync(file, 'utf8');

const marker = 'GT7_RASPBERRY_BRIDGE_MODE_V1_5_0';
if (src.includes(marker)) {
  console.log('Raspberry Bridge Mode v1.5.0 already applied.');
  process.exit(0);
}

function rep(a, b, label) {
  if (!src.includes(a)) {
    console.warn('Trecho não encontrado:', label);
    return;
  }
  src = src.replace(a, b);
}

rep('import java.util.concurrent.atomic.AtomicBoolean;\n', 'import java.util.concurrent.atomic.AtomicBoolean;\nimport org.json.JSONObject;\n', 'import JSONObject');

rep('private static final String VERSION = "1.4.7";', 'private static final String VERSION = "1.5.0"; // GT7_RASPBERRY_BRIDGE_MODE_V1_5_0', 'version 1.4.7');
rep('private static final String VERSION = "1.4.8";', 'private static final String VERSION = "1.5.0"; // GT7_RASPBERRY_BRIDGE_MODE_V1_5_0', 'version 1.4.8');
rep('private static final String VERSION = "1.4.9"; // GT7_STABLE_LAYOUT_CONNECTION_V1_4_9', 'private static final String VERSION = "1.5.0"; // GT7_RASPBERRY_BRIDGE_MODE_V1_5_0', 'version 1.4.9');

rep('    private static final String DEFAULT_PS5_IP = "192.168.1.54";\n', '    private static final String DEFAULT_PS5_IP = "192.168.1.54";\n    private static final String DEFAULT_RASPBERRY_URL = "http://192.168.1.70:8787/api/fields";\n', 'default raspberry url');

rep('    private EditText ps5Ip;\n', '    private EditText ps5Ip;\n    private EditText raspberryUrl;\n', 'raspberry url field');

rep('    private boolean detailsOpen = false;\n', '    private boolean detailsOpen = false;\n    private volatile boolean raspberryPolling = false;\n', 'raspberry polling flag');

rep(
'        hero.addView(ps5Ip, ipLp);\n\n        LinearLayout badges = new LinearLayout(this);',
'        hero.addView(ps5Ip, ipLp);\n\n        raspberryUrl = new EditText(this);\n        raspberryUrl.setTextColor(TXT);\n        raspberryUrl.setHintTextColor(MUTED);\n        raspberryUrl.setTextSize(12);\n        raspberryUrl.setSingleLine(true);\n        raspberryUrl.setHint("URL Raspberry Bridge");\n        raspberryUrl.setText(getSharedPreferences("gt7_bridge", MODE_PRIVATE).getString("raspberry_url", DEFAULT_RASPBERRY_URL));\n        raspberryUrl.setPadding(dp(12), 0, dp(12), 0);\n        raspberryUrl.setBackground(round(CARD_2, dp(14), STROKE));\n        raspberryUrl.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ getSharedPreferences("gt7_bridge", MODE_PRIVATE).edit().putString("raspberry_url", s.toString()).apply(); } public void afterTextChanged(Editable e){} });\n        LinearLayout.LayoutParams raspLp = new LinearLayout.LayoutParams(-1, dp(44));\n        raspLp.setMargins(0, dp(6), 0, dp(10));\n        hero.addView(raspberryUrl, raspLp);\n\n        LinearLayout badges = new LinearLayout(this);',
'campo URL Raspberry'
);

rep(
'        Button save = actionButton("Salvar Sessão", BLUE);\n        Button open = actionButton("Abrir gt7.online", AMBER);\n        r1.addView(start, new LinearLayout.LayoutParams(0, dp(44), 1));',
'        Button save = actionButton("Salvar Sessão", BLUE);\n        Button open = actionButton("Abrir gt7.online", AMBER);\n        Button rasp = actionButton("Raspberry Bridge", BLUE);\n        r1.addView(start, new LinearLayout.LayoutParams(0, dp(44), 1));',
'botao raspberry declara'
);

rep(
'        r2.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));\n        r2.addView(open, new LinearLayout.LayoutParams(0, dp(44), 1));',
'        r2.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1));\n        r2.addView(open, new LinearLayout.LayoutParams(0, dp(44), 1));\n        LinearLayout r3 = new LinearLayout(this); r3.setOrientation(LinearLayout.HORIZONTAL);\n        r3.addView(rasp, new LinearLayout.LayoutParams(-1, dp(44)));',
'botao raspberry linha'
);

rep(
'        actions.addView(r2, rlp2);\n        hero.addView(actions);',
'        actions.addView(r2, rlp2);\n        LinearLayout.LayoutParams rlp3 = new LinearLayout.LayoutParams(-1, -2); rlp3.setMargins(0, dp(8), 0, 0);\n        actions.addView(r3, rlp3);\n        hero.addView(actions);',
'adiciona linha raspberry'
);

rep(
'        open.setOnClickListener(v -> openSite());\n',
'        open.setOnClickListener(v -> openSite());\n        rasp.setOnClickListener(v -> startRaspberryBridge());\n',
'onclick raspberry'
);

const methods = `

    private void startRaspberryBridge(){
        raspberryPolling = true;
        running.set(false);
        Toast.makeText(this, "Raspberry Bridge conectado", Toast.LENGTH_SHORT).show();
        Thread th = new Thread(this::raspberryPollLoop);
        th.setName("GT7-Raspberry-Poll");
        th.start();
    }

    private void raspberryPollLoop(){
        while(raspberryPolling){
            try{
                String urlText = raspberryUrl != null ? raspberryUrl.getText().toString().trim() : DEFAULT_RASPBERRY_URL;
                if(urlText.length() == 0) urlText = DEFAULT_RASPBERRY_URL;
                HttpURLConnection conn = (HttpURLConnection)new URL(urlText).openConnection();
                conn.setConnectTimeout(1200);
                conn.setReadTimeout(1200);
                conn.setRequestMethod("GET");
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject j = new JSONObject(sb.toString());
                synchronized(t){ applyRaspberryJson(j, t); }
            }catch(Exception e){
                synchronized(t){ t.connected=false; t.valid=false; t.warning="Raspberry offline: " + e.getMessage(); }
            }
            try{ Thread.sleep(500); }catch(Exception ignored){}
        }
    }

    private void applyRaspberryJson(JSONObject j, Telemetry x){
        x.connected = j.optBoolean("connected", true);
        x.valid = j.optBoolean("decodeOk", false);
        x.updatedAt = j.optLong("updatedAt", System.currentTimeMillis());
        x.lastPacketSize = j.optInt("packetSize", j.optInt("lastPacketSize", 0));
        x.packetVersion = j.optString("packetVersion", "?");
        x.warning = j.optString("warning", "");
        x.speed = (float)j.optDouble("velocidade", 0);
        x.maxSpeed = Math.max(x.maxSpeed, (float)j.optDouble("velocidadeMaxima", x.speed));
        x.rpm = (float)j.optDouble("rpm", 0);
        x.gearLabel = j.optString("marcha", "N");
        x.gear = j.optInt("marchaNumero", 0);
        x.throttle = j.optInt("acelerador", 0);
        x.brake = j.optInt("freio", 0);
        x.fuel = (float)j.optDouble("combustivel", 0);
        x.fuelPct = (float)j.optDouble("combustivelPorcentagem", -1);
        x.fuelCap = (float)j.optDouble("fuelCapacity", 0);
        x.laps = j.optInt("voltasCompletadas", 0);
        JSONObject p = j.optJSONObject("position");
        if(p != null){ x.x=(float)p.optDouble("x",0); x.y=(float)p.optDouble("y",0); x.z=(float)p.optDouble("z",0); }
        x.externalBest = j.optString("melhorVolta", "");
        x.externalLast = j.optString("ultimaVolta", "");
        x.externalCurrent = j.optString("voltaAtual", "");
        x.externalTotal = j.optString("tempoTotalCorrida", "");
    }
`;

rep(
'    private void startBridge(){',
methods + '\n    private void startBridge(){',
'insert raspberry methods before startBridge'
);

rep('        set("melhorVolta", snap.fmt(snap.best));', '        set("melhorVolta", snap.externalBest != null && snap.externalBest.length() > 0 ? snap.externalBest : snap.fmt(snap.best));', 'best external');
rep('        set("ultimaVolta", snap.fmt(snap.last));', '        set("ultimaVolta", snap.externalLast != null && snap.externalLast.length() > 0 ? snap.externalLast : snap.fmt(snap.last));', 'last external');
rep('        set("voltaAtual", snap.fmt(snap.current));', '        set("voltaAtual", snap.externalCurrent != null && snap.externalCurrent.length() > 0 ? snap.externalCurrent : snap.fmt(snap.current));', 'current external');
rep('        set("tempoTotalCorrida", snap.fmt((int)snap.raceMs));', '        set("tempoTotalCorrida", snap.externalTotal != null && snap.externalTotal.length() > 0 ? snap.externalTotal : snap.fmt((int)snap.raceMs));', 'total external');

rep('    private void stopBridge(){ running.set(false); }', '    private void stopBridge(){ running.set(false); raspberryPolling=false; }', 'stop raspberry polling');

rep('float speed=0,maxSpeed=0,rpm=0,fuel=0,fuelCap=0,fuelPct=-1,x=0,y=0,z=0;', 'float speed=0,maxSpeed=0,rpm=0,fuel=0,fuelCap=0,fuelPct=-1,x=0,y=0,z=0; String externalBest="",externalLast="",externalCurrent="",externalTotal="";', 'external strings fields');

rep('n.warning=warning;n.surface=surface;n.map=new ArrayList<>(map);return n;', 'n.warning=warning;n.surface=surface;n.externalBest=externalBest;n.externalLast=externalLast;n.externalCurrent=externalCurrent;n.externalTotal=externalTotal;n.map=new ArrayList<>(map);return n;', 'copy external strings');

fs.writeFileSync(file, src);
console.log('Raspberry Bridge Mode v1.5.0 applied.');
