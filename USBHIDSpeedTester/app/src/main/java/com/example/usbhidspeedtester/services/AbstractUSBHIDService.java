package com.example.usbhidspeedtester.services;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;//
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.example.usbhidspeedtester.events.DeviceAttachedEvent;
import com.example.usbhidspeedtester.events.DeviceDetachedEvent;
import com.example.usbhidspeedtester.events.PrepareDevicesListEvent;
import com.example.usbhidspeedtester.events.SelectDeviceEvent;
import com.example.usbhidspeedtester.events.ShowDevicesListEvent;
import com.example.usbhidspeedtester.events.USBDataSendEvent;
import com.example.usbhidspeedtester.events.USBStopTimer;
import com.example.usbhidspeedtester.util.USBUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.nio.ByteBuffer;

public abstract class AbstractUSBHIDService extends Service {

    public static final String ACTION_USB_PERMISSION = "com.google.android.HID.action.USB_PERMISSION";
    public static final String ACTION_USB_SHOW_DEVICES_LIST = "ACTION_USB_SHOW_DEVICES_LIST";
    public static final String ACTION_USB_DATA_TYPE = "ACTION_USB_DATA_TYPE";

    private static final String TAG = AbstractUSBHIDService.class.getCanonicalName();

    private USBThreadDataReceiver usbThreadDataReceiver;

    private final Handler uiHandler = new Handler();

    private UsbManager mUsbManager;
    private UsbInterface intf;
    private UsbEndpoint endPointRead;
    private UsbEndpoint endPointWrite;
    private UsbDeviceConnection connection;
    private UsbDevice device;

    private IntentFilter filter;
    private PendingIntent mPermissionIntent;

    private int packetSize;
    private boolean sendedDataType;

    protected EventBus eventBus = EventBus.getDefault();

    private UsbInterface usbReadInterface = null;
    private UsbInterface usbWriteInterface = null;

    private Timer timer = null;
    public String fileN = "";
    public boolean needWrite = false;
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_SHOW_DEVICES_LIST);
        filter.addAction(ACTION_USB_DATA_TYPE);
        registerReceiver(mUsbReceiver, filter);
        eventBus.register(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (ACTION_USB_DATA_TYPE.equals(action)) {
            sendedDataType = intent.getBooleanExtra(ACTION_USB_DATA_TYPE, false);
        }
        onCommand(intent, action, flags, startId);
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        eventBus.unregister(this);
        super.onDestroy();
        if (usbThreadDataReceiver != null) {
            usbThreadDataReceiver.stopThis();
        }
        unregisterReceiver(mUsbReceiver);
    }

    private class USBThreadDataReceiver extends Thread {

        private volatile boolean isStopped;

        public USBThreadDataReceiver() {
        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority (-20);
            try {
                if (connection != null && endPointRead != null) {
                    while (!isStopped) {
                        int size = packetSize;
                        final byte[] buffer = new byte[size];
                        //usbrequest way
                        /*ByteBuffer byteBuffer = ByteBuffer.allocate(size);
                        UsbRequest usbRequest = new UsbRequest();
                        usbRequest.initialize(connection, endPointRead);
                        usbRequest.queue(byteBuffer, size);*/
                        //
                        final int status = connection.bulkTransfer(endPointRead, buffer, size, 1000);
                        //final int status = connection.controlTransfer( 0xA1,0x01,0, 0,buffer,size, 1000);
                        //if (connection.requestWait() == usbRequest) {
                        //    byte[] retData = byteBuffer.array();
                            if (status > 0) {
                                Log.d("hid test", "!!!!!!!!!!!!!!got one buffer ");
                                onUSBDataReceive(buffer);
                        //    onUSBDataReceive(retData);
                            }
                        //}

                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in receive thread", e);
            }
        }

        public void stopThis() {
            isStopped = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(USBDataSendEvent event){
        if(event.getFilename() != null && !event.getNeedw()){
            fileN = event.getFilename();
        }
        needWrite = event.getNeedw();
        //if(needWrite)
        //    sendStop(event.getData());
        //else
        sendData(event.getData(), event.getStartTime(), event.getPeriodTime());
    }

    @Subscribe
    public void onEvent(SelectDeviceEvent event) {
        device = (UsbDevice) mUsbManager.getDeviceList().values().toArray()[event.getDevice()];
        mUsbManager.requestPermission(device, mPermissionIntent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(PrepareDevicesListEvent event) {
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<CharSequence> list = new LinkedList<CharSequence>();
        for (UsbDevice usbDevice : mUsbManager.getDeviceList().values()) {
            list.add(onBuildingDevicesList(usbDevice));
        }
        final CharSequence devicesName[] = new CharSequence[mUsbManager.getDeviceList().size()];
        list.toArray(devicesName);
        eventBus.post(new ShowDevicesListEvent(devicesName));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStopTimer(USBStopTimer stopTimerEvent){
        if (timer != null){
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    private  int sendFeatureReport(byte[] data, int length) {
        int requestType = 0x21;
        int value = (0x2 << 8) | data[0];// preValue | (byte) reportId; // reportId;
        int interfaceID = usbWriteInterface.getId();
        return connection.controlTransfer(requestType,
               REQUEST_SET_REPORT,
               value,
               interfaceID , data, data.length, 1000);
    }
    private int hid_read_weiz(byte[] buffer, int length){

        int requestType = 0xa1;
        int value = (0x1 << 8) | buffer[0];
        int interfaceID = usbReadInterface.getId();
        return connection.controlTransfer(requestType,
                REQUEST_GET_REPORT,
                value,
                interfaceID , buffer, length, 1000);
    }
    private void sendData(final byte[] data, long startTime, long periodTime) {
        if (device != null /*&& endPointWrite != null*/ && mUsbManager.hasPermission(device)) {


                    final byte[] buffer = new byte[64];
                    int status = sendFeatureReport(data, data.length);
                    onUSBDataSended(status, data);
                    Log.d("RECEIVER","we are in run!!");
                    //get the return value from the send report.
                    buffer[0] = 0x1;
                    status = hid_read_weiz(buffer, 64);

        }
    }

    private void sendStop(final byte[] data) {
        if (device != null && endPointWrite != null && mUsbManager.hasPermission(device)) {

                    int status = sendFeatureReport(data, data.length);
                    onUSBDataSended(status, data);

        }
    }

    /** GET_REPORT request code */
    public static final int REQUEST_GET_REPORT = 0x01;
    /** SET_REPORT request code */
    public static final int REQUEST_SET_REPORT = 0x09;
    /** INPUT report type */
    public static final int REPORT_TYPE_INPUT = 0x0100;
    /** OUTPUT report type */
    public static final int REPORT_TYPE_OUTPUT = 0x0200;
    /** FEATURE report type */
    public static final int REPORT_TYPE_FEATURE = 0x0300;


    /**
     * receives the permission request to connect usb devices
     */
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                setDevice(intent);
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                setDevice(intent);
                if (device == null) {
                    onDeviceConnected(device);
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                if (device != null) {
                    device = null;
                    if (usbThreadDataReceiver != null) {
                        usbThreadDataReceiver.stopThis();
                    }
                    eventBus.post(new DeviceDetachedEvent());
                    onDeviceDisconnected(device);
                }
            }
        }

        private void setDevice(Intent intent) {

            device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                onDeviceSelected(device);
                connection = mUsbManager.openDevice(device);

                UsbEndpoint tOut = null;
                UsbEndpoint tIn = null;

                int tInCount = 0;
                int tInSelected = 0;
                int tOutCount = 0;
                int tOutSelected = 0;

                for(int i=0;i<device.getInterfaceCount();i++) {
                    UsbInterface usbif = device.getInterface(i);

                    if (usbif.getInterfaceClass() == UsbConstants.USB_CLASS_HID){
                        int tEndpointCnt = usbif.getEndpointCount();
                        for(int j=0; j<tEndpointCnt; j++){
                            UsbEndpoint endpoint = usbif.getEndpoint(j);
                            usbWriteInterface = usbif;
                            usbReadInterface = usbif;
                            int type = endpoint.getType();
                            int direction = endpoint.getDirection();
                            int maxPacketSize = endpoint.getMaxPacketSize();
                            Log.i("EndpointInfo","Type: "+type+" Direction: "+direction);

                            if( direction == UsbConstants.USB_DIR_OUT){
                                if (type == UsbConstants.USB_ENDPOINT_XFER_BULK || type == UsbConstants.USB_ENDPOINT_XFER_INT){
                                    if (tOutCount == tOutSelected ){
                                        tOut = endpoint;
//                                        usbWriteInterface = usbif;
                                    }
                                    tOutCount++;
                                }
                            }else if(direction == UsbConstants.USB_DIR_IN){
                                if(type == UsbConstants.USB_ENDPOINT_XFER_BULK || type == UsbConstants.USB_ENDPOINT_XFER_INT){
                                    if (tInCount == tInSelected){
                                        tIn = endpoint;
//                                        usbReadInterface = usbif;
                                    }
                                    tInCount++;
                                }
                            }
                        }
                    }

                }

                if (tOut != null){
                    endPointWrite = tOut;
                    boolean writeClamed = connection.claimInterface(usbWriteInterface, true);

                    Log.d(TAG, "Write interface clamed: "+writeClamed);
                }
                else{
                    Log.e("endPointWrite", "Device have no endPointWrite");
                }

                if (tIn != null){
                    boolean readClamed = connection.claimInterface(usbReadInterface, true);

                    Log.d(TAG, "Read interface clamed: "+readClamed);
                    endPointRead = tIn;
                    packetSize = endPointRead.getMaxPacketSize();
                }
                else{
                    Log.e("endPointRead", "Device have no endPointRead");
                }

                if (endPointRead != null){
                    usbThreadDataReceiver = new USBThreadDataReceiver();
                    usbThreadDataReceiver.start();
                }

                eventBus.post(new DeviceAttachedEvent());
            }
        }
    };

    public void onCommand(Intent intent, String action, int flags, int startId) {
    }

    public void onUSBDataReceive(byte[] buffer) {
    }

    public void onDeviceConnected(UsbDevice device) {
    }

    public void onDeviceDisconnected(UsbDevice device) {
    }

    public void onDeviceSelected(UsbDevice device) {
    }

    public CharSequence onBuildingDevicesList(UsbDevice usbDevice) {
        return null;
    }

    public void onUSBDataSending(byte[] data) {
    }

    public void onUSBDataSended(int status, byte[] out) {
    }

    public void onSendingError(Exception e) {
    }

}
