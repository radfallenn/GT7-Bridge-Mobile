import fs from 'node:fs';

const file = 'app/src/main/java/com/gt7/bridge/mobile/MainActivity.java';
let src = fs.readFileSync(file, 'utf8');

const marker = 'GT7_CONNECTION_RECOVERY_V1_4_8';
if (src.includes(marker)) {
  console.log('Connection recovery v1.4.8 already applied.');
  process.exit(0);
}

function replace(search, replacement, label) {
  if (!src.includes(search)) {
    console.warn('Trecho não encontrado:', label);
    return;
  }
  src = src.replace(search, replacement);
}

replace('private static final String VERSION = "1.4.7";', 'private static final String VERSION = "1.4.8"; // GT7_CONNECTION_RECOVERY_V1_4_8', 'version');

replace(
'        buildAllDataDashboard();\n\n        webView = new WebView(this);',
'        buildAllDataDashboard();\n\n        new Timer().schedule(new TimerTask(){ public void run(){ runOnUiThread(() -> { if(!running.get()) startBridge(); }); }}, 900);\n\n        webView = new WebView(this);',
'auto start depois da UI'
);

replace(
'    private void startBridge(){\n        if(running.get()) { Toast.makeText(this, "Bridge já está ativo", Toast.LENGTH_SHORT).show(); return; }\n        synchronized(t){ t.resetSession(); }\n        running.set(true);\n        new Thread(this::udpReceiver).start();\n        new Thread(this::heartbeat).start();\n    }',
'    private void startBridge(){\n        if(running.get()) { Toast.makeText(this, "Bridge já está ativo", Toast.LENGTH_SHORT).show(); return; }\n        synchronized(t){ t.resetSession(); }\n        running.set(true);\n        Toast.makeText(this, "Bridge iniciado", Toast.LENGTH_SHORT).show();\n        Thread udp = new Thread(this::udpReceiver);\n        udp.setName("GT7-UDP-Receiver");\n        udp.start();\n        Thread hb = new Thread(this::heartbeat);\n        hb.setName("GT7-Heartbeat");\n        hb.start();\n    }',
'start bridge seguro'
);

replace(
'    private void heartbeat(){ while(running.get()){ try{ String ip=ps5Ip.getText().toString().trim(); if(ip.length()>6){ DatagramSocket s=new DatagramSocket(); byte[] hb="C".getBytes("UTF-8"); s.send(new DatagramPacket(hb,hb.length,InetAddress.getByName(ip),33739)); s.close(); } Thread.sleep(2000); }catch(Exception ignored){} } }',
'    private void heartbeat(){ while(running.get()){ DatagramSocket s = null; try{ String ip=ps5Ip.getText().toString().trim(); if(ip.length()>6){ s=new DatagramSocket(); s.setBroadcast(true); byte[] hb="C".getBytes("UTF-8"); s.send(new DatagramPacket(hb,hb.length,InetAddress.getByName(ip),33739)); } Thread.sleep(1000); }catch(Exception ignored){} finally { if(s!=null) s.close(); } } }',
'heartbeat mais frequente'
);

replace(
'    private void udpReceiver(){ try(DatagramSocket socket=new DatagramSocket(33740)){ socket.setSoTimeout(1000); byte[] buf=new byte[4096]; while(running.get()){ try{ DatagramPacket p=new DatagramPacket(buf,buf.length); socket.receive(p); byte[] raw=Arrays.copyOf(p.getData(),p.getLength()); byte[] data=decode(raw); synchronized(t){ parse(data,raw.length,t); } }catch(SocketTimeoutException ignored){} } }catch(Exception e){ runOnUiThread(() -> Toast.makeText(this, "Erro UDP: "+e.getMessage(), Toast.LENGTH_LONG).show()); } }',
'    private void udpReceiver(){ DatagramSocket socket = null; try{ socket = new DatagramSocket(null); socket.setReuseAddress(true); socket.bind(new InetSocketAddress(33740)); socket.setSoTimeout(1000); byte[] buf=new byte[4096]; while(running.get()){ try{ DatagramPacket p=new DatagramPacket(buf,buf.length); socket.receive(p); byte[] raw=Arrays.copyOf(p.getData(),p.getLength()); byte[] data=decode(raw); synchronized(t){ parse(data,raw.length,t); } }catch(SocketTimeoutException ignored){} } }catch(Exception e){ runOnUiThread(() -> Toast.makeText(this, "Erro UDP: "+e.getMessage(), Toast.LENGTH_LONG).show()); } finally { if(socket!=null) socket.close(); } }',
'udp bind seguro'
);

fs.writeFileSync(file, src);
console.log('Connection recovery v1.4.8 applied while keeping modern layout.');
