package com.gt7.bridge.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Telemetry t = new Telemetry();
    private TextView status, values;
    private EditText ps5Ip;

    public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36,36,36,36);
        root.setBackgroundColor(Color.rgb(245,245,245));
        TextView title = new TextView(this); title.setText("GT7 Bridge Mobile"); title.setTextSize(24); title.setTextColor(Color.BLACK); root.addView(title);
        ps5Ip = new EditText(this); ps5Ip.setHint("IP do PS5"); ps5Ip.setSingleLine(true); root.addView(ps5Ip);
        Button start = new Button(this); start.setText("Iniciar Bridge"); root.addView(start);
        Button stop = new Button(this); stop.setText("Parar"); root.addView(stop);
        status = new TextView(this); status.setText("Status: parado"); status.setTextSize(18); root.addView(status);
        values = new TextView(this); values.setTextSize(16); values.setTextColor(Color.DKGRAY); root.addView(values);
        setContentView(root);
        start.setOnClickListener(v -> startBridge());
        stop.setOnClickListener(v -> stopBridge());
        new Thread(this::httpServer).start();
        new Timer().scheduleAtFixedRate(new TimerTask(){ public void run(){ runOnUiThread(() -> values.setText(t.toPretty())); }}, 500, 500);
    }

    private void startBridge() {
        if (running.get()) return;
        running.set(true);
        status.setText("Status: bridge ativo em http://127.0.0.1:8787/api/fields");
        new Thread(this::udpReceiver).start();
        new Thread(this::heartbeat).start();
    }
    private void stopBridge(){ running.set(false); status.setText("Status: parado"); }

    private void heartbeat(){
        while(running.get()){
            try{
                String ip = ps5Ip.getText().toString().trim();
                if(ip.length()>6){
                    DatagramSocket s = new DatagramSocket();
                    byte[] hb = "A".getBytes("UTF-8");
                    s.send(new DatagramPacket(hb, hb.length, InetAddress.getByName(ip), 33739));
                    s.close();
                }
                Thread.sleep(10000);
            }catch(Exception ignored){}
        }
    }

    private void udpReceiver(){
        try(DatagramSocket socket = new DatagramSocket(33740)){
            socket.setSoTimeout(1000);
            byte[] buf = new byte[4096];
            while(running.get()){
                try{
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    byte[] data = Arrays.copyOf(p.getData(), p.getLength());
                    t.lastPacketSize = data.length;
                    decodeBestEffort(data, t);
                }catch(SocketTimeoutException ignored){}
            }
        }catch(Exception e){ runOnUiThread(() -> status.setText("Erro UDP: "+e.getMessage())); }
    }

    private void decodeBestEffort(byte[] d, Telemetry x){
        x.connected = true; x.updatedAt = System.currentTimeMillis();
        if(d.length > 160){
            try{
                ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
                x.rpm = safeFloat(b, 0x3C);
                x.fuel = safeFloat(b, 0x44);
                float vx = safeFloat(b, 0x4C), vy = safeFloat(b, 0x50), vz = safeFloat(b, 0x54);
                float sp = (float)Math.sqrt(vx*vx+vy*vy+vz*vz) * 3.6f;
                if(sp >= 0 && sp < 700) x.speed = sp;
                x.positionX = safeFloat(b, 0x04); x.positionY = safeFloat(b, 0x08); x.positionZ = safeFloat(b, 0x0C);
                int gearByte = d[0x90] & 0xFF; x.gear = gearByte & 0x0F;
                x.throttle = d[0x91] & 0xFF; x.brake = d[0x92] & 0xFF;
                int lap = b.getShort(0x74) & 0xFFFF; if(lap >= x.completedLaps) x.completedLaps = lap;
                x.map.add(new float[]{x.positionX,x.positionY,x.positionZ}); if(x.map.size()>1500) x.map.remove(0);
            }catch(Exception ignored){}
        }
    }
    private float safeFloat(ByteBuffer b, int offset){ try { return b.getFloat(offset); } catch(Exception e){ return 0f; } }

    private void httpServer(){
        try(ServerSocket server = new ServerSocket(8787)){
            while(true){
                Socket c = server.accept();
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                String line = br.readLine();
                String body = t.toJson();
                OutputStream out = c.getOutputStream();
                String h = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\nContent-Length: "+body.getBytes("UTF-8").length+"\r\n\r\n";
                out.write(h.getBytes("UTF-8")); out.write(body.getBytes("UTF-8")); out.flush(); c.close();
            }
        }catch(Exception ignored){}
    }

    static class Telemetry{
        boolean connected=false; long updatedAt=0; int lastPacketSize=0, gear=0, throttle=0, brake=0, completedLaps=0, pitStops=0; float speed=0, rpm=0, fuel=0, bestLap=0, raceTime=0, rain=0, tcs=0, positionX=0, positionY=0, positionZ=0; ArrayList<float[]> map = new ArrayList<>();
        String toJson(){ return "{\"connected\":"+connected+",\"updatedAt\":"+updatedAt+",\"lastPacketSize\":"+lastPacketSize+",\"velocidade\":"+speed+",\"rpm\":"+rpm+",\"marcha\":"+gear+",\"acelerador\":"+throttle+",\"freio\":"+brake+",\"combustivel\":"+fuel+",\"melhorVolta\":"+bestLap+",\"tempoTotalCorrida\":"+raceTime+",\"voltasCompletadas\":"+completedLaps+",\"chuva\":"+rain+",\"controleTracao\":"+tcs+",\"numeroParadas\":"+pitStops+",\"position\":{\"x\":"+positionX+",\"y\":"+positionY+",\"z\":"+positionZ+"}}"; }
        String toPretty(){ return "Velocidade: "+(int)speed+" km/h\nRPM: "+(int)rpm+"\nMarcha: "+gear+"\nAcelerador: "+throttle+"%\nFreio: "+brake+"%\nCombustível: "+fuel+"\nVoltas: "+completedLaps+"\nPacote UDP: "+lastPacketSize; }
    }
}
