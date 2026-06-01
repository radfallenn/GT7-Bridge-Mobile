import fs from 'node:fs';

const file = 'app/src/main/java/com/gt7/bridge/mobile/MainActivity.java';
let src = fs.readFileSync(file, 'utf8');

const marker = 'GT7_STABLE_LAYOUT_CONNECTION_V1_4_9';
if (src.includes(marker)) {
  console.log('Stable layout connection v1.4.9 already applied.');
  process.exit(0);
}

function rep(a, b, label) {
  if (!src.includes(a)) {
    console.warn('Trecho não encontrado:', label);
    return;
  }
  src = src.replace(a, b);
}

rep('private static final String VERSION = "1.4.7";', 'private static final String VERSION = "1.4.9"; // GT7_STABLE_LAYOUT_CONNECTION_V1_4_9', 'version 1.4.7');
rep('private static final String VERSION = "1.4.8"; // GT7_CONNECTION_RECOVERY_V1_4_8', 'private static final String VERSION = "1.4.9"; // GT7_STABLE_LAYOUT_CONNECTION_V1_4_9', 'version 1.4.8 recovery');

// Garantir que abrir site não inicia nem reinicia Bridge. Primeiro conexão, depois site.
rep(
'    private void openSite(){ webView.setVisibility(View.VISIBLE); webView.getLayoutParams().height = dp(420); webView.requestLayout(); webView.loadUrl("https://gt7.online"); }',
'    private void openSite(){\n        if(!running.get()) {\n            Toast.makeText(this, "Inicie o Bridge antes de abrir o gt7.online", Toast.LENGTH_LONG).show();\n            return;\n        }\n        webView.setVisibility(View.VISIBLE);\n        webView.getLayoutParams().height = dp(420);\n        webView.requestLayout();\n        webView.loadUrl("https://gt7.online");\n    }',
'openSite protegido'
);

// Voltar conexão para modo simples, igual base funcional: DatagramSocket direto e heartbeat simples.
rep(
'    private void heartbeat(){ while(running.get()){ DatagramSocket s = null; try{ String ip=ps5Ip.getText().toString().trim(); if(ip.length()>6){ s=new DatagramSocket(); s.setBroadcast(true); byte[] hb="C".getBytes("UTF-8"); s.send(new DatagramPacket(hb,hb.length,InetAddress.getByName(ip),33739)); } Thread.sleep(1000); }catch(Exception ignored){} finally { if(s!=null) s.close(); } } }',
'    private void heartbeat(){ while(running.get()){ try{ String ip=ps5Ip.getText().toString().trim(); if(ip.length()>6){ DatagramSocket s=new DatagramSocket(); byte[] hb="C".getBytes("UTF-8"); s.send(new DatagramPacket(hb,hb.length,InetAddress.getByName(ip),33739)); s.close(); } Thread.sleep(2000); }catch(Exception ignored){} } }',
'heartbeat recovery para simples'
);

rep(
'    private void udpReceiver(){ DatagramSocket socket = null; try{ socket = new DatagramSocket(null); socket.setReuseAddress(true); socket.bind(new InetSocketAddress(33740)); socket.setSoTimeout(1000); byte[] buf=new byte[4096]; while(running.get()){ try{ DatagramPacket p=new DatagramPacket(buf,buf.length); socket.receive(p); byte[] raw=Arrays.copyOf(p.getData(),p.getLength()); byte[] data=decode(raw); synchronized(t){ parse(data,raw.length,t); } }catch(SocketTimeoutException ignored){} } }catch(Exception e){ runOnUiThread(() -> Toast.makeText(this, "Erro UDP: "+e.getMessage(), Toast.LENGTH_LONG).show()); } finally { if(socket!=null) socket.close(); } }',
'    private void udpReceiver(){ try(DatagramSocket socket=new DatagramSocket(33740)){ socket.setSoTimeout(1000); byte[] buf=new byte[4096]; while(running.get()){ try{ DatagramPacket p=new DatagramPacket(buf,buf.length); socket.receive(p); byte[] raw=Arrays.copyOf(p.getData(),p.getLength()); byte[] data=decode(raw); synchronized(t){ parse(data,raw.length,t); } }catch(SocketTimeoutException ignored){} } }catch(Exception e){ runOnUiThread(() -> Toast.makeText(this, "Erro UDP: "+e.getMessage(), Toast.LENGTH_LONG).show()); } }',
'udp recovery para simples'
);

// Caso ainda esteja no código atual simples, reforçar startBridge com mensagem mas sem mudar socket.
rep(
'    private void startBridge(){\n        if(running.get()) { Toast.makeText(this, "Bridge já está ativo", Toast.LENGTH_SHORT).show(); return; }\n        synchronized(t){ t.resetSession(); }\n        running.set(true);\n        new Thread(this::udpReceiver).start();\n        new Thread(this::heartbeat).start();\n    }',
'    private void startBridge(){\n        if(running.get()) { Toast.makeText(this, "Bridge já está ativo", Toast.LENGTH_SHORT).show(); return; }\n        synchronized(t){ t.resetSession(); }\n        running.set(true);\n        Toast.makeText(this, "Bridge iniciado. Aguarde Pacote C / 368", Toast.LENGTH_SHORT).show();\n        new Thread(this::udpReceiver).start();\n        new Thread(this::heartbeat).start();\n    }',
'startBridge mensagem funcional'
);

fs.writeFileSync(file, src);
console.log('Stable connection restored while keeping modern layout.');
