package com.example.usbhidspeedtester;

import android.content.pm.PackageManager;
import android.Manifest;
import android.support.v4.app.ActivityCompat;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.usbhidspeedtester.events.LogMessageEvent;
import com.example.usbhidspeedtester.events.PrepareDevicesListEvent;
import com.example.usbhidspeedtester.events.SelectDeviceEvent;
import com.example.usbhidspeedtester.events.ShowDevicesListEvent;
import com.example.usbhidspeedtester.events.USBDataSendEvent;
import com.example.usbhidspeedtester.services.USBHIDSpeedTestService;
import com.example.usbhidspeedtester.udp.UDPReceiver;
import com.example.usbhidspeedtester.udp.UDP_Client;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;
import org.greenrobot.eventbus.Subscribe;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.Date;
import java.text.SimpleDateFormat;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    protected EventBus eventBus;

    private Intent usbService;

    private TextView debugLogMsgTextView;

    private ScrollView debugLogScrollview;

    private Timer timer = new Timer();

    private UDPReceiver datagramReceiver;

    private int UDP_SERVER_PORT = 1985;
    private int UDP_CLIENT_PORT = 1995;
    private String UDP_HOST = "192.168.0.104";

    private UDP_Client client;
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private boolean srvStopped = true;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE };
    public MainActivity() throws UnknownHostException {
    }

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        debugLogMsgTextView = findViewById(R.id.debugLogMsgTextView);
        debugLogScrollview = findViewById(R.id.debugLogScrollview);
        try {
            eventBus = EventBus.builder().logNoSubscriberMessages(false).sendNoSubscriberEvent(false).installDefaultEventBus();
        } catch (EventBusException e) {
            eventBus = EventBus.getDefault();
        }
        verifyStoragePermissions(this);
    }
    public static void verifyStoragePermissions(MainActivity activity) {
// Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
// We don't have permission so prompt the user
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE);
        }
    }
    @Override
    protected void onStart() {
        super.onStart();
        eventBus.register(this);
        prepareServices();

    }

    protected void onResume() {
        super.onResume();
        datagramReceiver = new UDPReceiver(UDP_SERVER_PORT);
        datagramReceiver.start();
    }

    protected void onPause() {
        super.onPause();
        datagramReceiver.kill();
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    private void prepareServices() {
        usbService = new Intent(this, USBHIDSpeedTestService.class);
        startService(usbService);
        srvStopped = false;

    }

    public void onSelectDevice(View v){
        eventBus.post(new PrepareDevicesListEvent());
    }


    public void onSendUDP(View v){
        if (client == null){
            try {

                if (UDP_HOST != null && UDP_HOST.length() > 0 && UDP_CLIENT_PORT > 0){
                    InetAddress UDP_CLIENT_ADDR = InetAddress.getByName(UDP_HOST);

                    client = new UDP_Client(UDP_CLIENT_ADDR, UDP_CLIENT_PORT);
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        if (client != null){
            String message = "2A233";
            client.send(message);
            String logMsg = "Sent to "+UDP_HOST+":"+UDP_CLIENT_PORT+" Message: "+message;
            onEvent(new LogMessageEvent(logMsg));
        }
    }

    public void onSetupUDP(View v){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Set UDP host & port");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(UDP_HOST+":"+UDP_CLIENT_PORT);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String addr = input.getText().toString();
                String tokens[] = addr.split(":");
                if ( tokens.length == 2){
                    UDP_HOST = tokens[0];
                    UDP_CLIENT_PORT = Integer.parseInt(tokens[1]);
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void onStopTest(View v)
    {//send stop cmd to hid
        final byte[] data = new byte[63];
        long starTime = 0;
        long periodTime = 1; //milliseconds
        boolean needw = true;

        data[0] = (byte) 0x02;
        data[1] = (byte) 0xA2;
        data[2] = (byte) 0x33;
        data[3] = (byte) 0x00;
        eventBus.post(new USBDataSendEvent(data, starTime, periodTime, needw));

        SystemClock.sleep(1000);

        data[0] = (byte) 0x02;
        data[1] = (byte) 0x19;
        data[2] = (byte) 0x95;
        data[3] = (byte) 0x0;//0 to close
        data[4] = (byte) 0x0;
        data[5] = (byte) 0x0;
        data[6] = (byte) 0x0;
        Log.d("hid test","**************************please note that stop comes**********************");
        eventBus.post(new USBDataSendEvent(data, starTime, periodTime, needw));
        debugLogMsgTextView.setText("test stopped");

    }

    public void onSend(View v){
        final byte[] data = new byte[63];
        long starTime = 0;
        long periodTime = 1;//4; //milliseconds
        boolean needw =false;
        Log.d("hid test", "onSend start  first");

        data[0] = (byte) 0x02;
        data[1] = (byte) 0x19;
        data[2] = (byte) 0x95;
        data[3] = (byte) 0x1;//0 to close
        data[4] = (byte) 0x0;
        data[5] = (byte) 0x0;
        data[6] = (byte) 0x0;
        eventBus.post(new USBDataSendEvent(data, starTime, periodTime, needw));

        SystemClock.sleep(1000);

        data[0] = (byte) 0x02;
        data[1] = (byte) 0xA2;
        data[2] = (byte) 0x33;
        data[3] = (byte) 0x01;

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss");//设置日期格式
        String filename = "save-"+df.format(new Date())+".txt";// new Date()为获取当前系统时间
        eventBus.post(new USBDataSendEvent(data, starTime, periodTime,filename));
        debugLogMsgTextView.setText("testing");
    }

    public void onClearLog(View v){
        debugLogMsgTextView.setText("");
    }

    void showListOfDevices(CharSequence devicesName[]) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if (devicesName.length == 0) {
            builder.setTitle("Please connect your USB HID device");
        } else {
            builder.setTitle("Please select your USB HID device");
        }

        builder.setItems(devicesName, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                eventBus.post(new SelectDeviceEvent(which));
            }
        });
        builder.setCancelable(true);
        builder.show();
    }

    @Subscribe
    public void onEvent(ShowDevicesListEvent event) {
        showListOfDevices(event.getCharSequenceArray());
    }

    @Subscribe
    public void onEvent(final LogMessageEvent logMessageEvent){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //debugLogMsgTextView.setText(debugLogMsgTextView.getText()+"\n"+logMessageEvent.getData());
                debugLogScrollview.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }


}
