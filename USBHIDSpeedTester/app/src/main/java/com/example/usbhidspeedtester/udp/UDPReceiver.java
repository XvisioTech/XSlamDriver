package com.example.usbhidspeedtester.udp;


import com.example.usbhidspeedtester.events.LogMessageEvent;
import org.greenrobot.eventbus.EventBus;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPReceiver extends Thread {
    private boolean bKeepRunning = true;
    private String lastMessage = "";
    private int port;
    private int MAX_UDP_DATAGRAM_LEN = 1600;
    protected EventBus eventBus = EventBus.getDefault();


    public UDPReceiver(int port){
        this.port = port;
    }

    public void run() {
        String message;
        DatagramSocket socket = null;
        byte[] lmessage = new byte[MAX_UDP_DATAGRAM_LEN];
        DatagramPacket packet = new DatagramPacket(lmessage, lmessage.length);

        long totalMessageCount = 0;
        long messageCount = 0;
        long startTime = System.currentTimeMillis();
        long lastSecondTime = System.currentTimeMillis();
        try {
            socket = new DatagramSocket(port);

            while(bKeepRunning) {
                socket.receive(packet);
                message = new String(lmessage, 0, packet.getLength());
                lastMessage = message;
                totalMessageCount++;
                messageCount++;

                long time = System.currentTimeMillis();
                long deltaLastSecond = time - lastSecondTime;


                if ( deltaLastSecond > 1000){
                    long totalTime = time - startTime;
                    String messageTimestampStr = message.substring(0, 6);
                    long messageTimestamp = Long.parseLong(messageTimestampStr);
                    String msg = "Total UDP message "+messageCount+"/sec | last message timestamp: "+messageTimestamp+" | totalMessageCount: "+totalMessageCount;
                    eventBus.post(new LogMessageEvent("Last UDP message: "+lastMessage));
                    eventBus.post(new LogMessageEvent(msg));
                    lastSecondTime = time;
                    messageCount = 0;
                }


            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if (socket != null) {
            socket.close();
        }
    }

    public void kill() {
        bKeepRunning = false;
    }

    public String getLastMessage() {
        return lastMessage;
    }
}
