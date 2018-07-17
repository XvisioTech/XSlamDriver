package com.example.usbhidspeedtester.services;

import android.content.Intent;
import android.hardware.usb.UsbDevice;

import com.example.usbhidspeedtester.events.LogMessageEvent;
import com.example.usbhidspeedtester.util.ByteUtils;
import com.example.usbhidspeedtester.util.USBUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.lang.System;
import  java.io.DataInputStream ;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import android.os.Environment;
import android.util.Log;
import java.io.FileWriter;
//import java.nio.FloatBuffer;
import java.io.RandomAccessFile;
import static com.example.usbhidspeedtester.util.ByteUtils.bytesToLong;
import static java.lang.String.*;
import java.lang.Math;
import android.os.Handler;
import android.os.Process;
import android.os.Message;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.FileWriter;
import java.io.IOException;


public class USBHIDSpeedTestService extends AbstractUSBHIDService{

    public static long lastDeltaTimestamp = 0;
    long validMessageCounter = 0;
    long nonValidMessageCounter = 0;
    long lastStatusUpdateTime = 0;
    long statusUpdateTimeFrame = 3000;
    int frame_count =0;
    float[] pose_rot = new float[9];
    float[] pose_tran = new float[3];
    float[] pose_angle = new float[3];
    float[] accelData = new float[3];
    float[] gyroData = new float[3];
    byte[] gBuffer = new byte[64];
    private final int REFRESH = 1;
    private Lock lock = new ReentrantLock();
    private Lock lock_b = new ReentrantLock();
    public ByteArrayOutputStream outputStream1 = new ByteArrayOutputStream( );
    public ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
    private  long prevTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        mLog("Service created.");
    }

    @Override
    public void onCommand(Intent intent, String action, int flags, int startId) {

        super.onCommand(intent, action, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onDeviceConnected(UsbDevice device) {
        mLog("device connected");
    }

    @Override
    public void onDeviceDisconnected(UsbDevice device) {
        mLog("device disconnected");
    }

    @Override
    public void onDeviceSelected(UsbDevice device) {
        mLog("Selected device VID:" + Integer.toHexString(device.getVendorId()) + " PID:" + Integer.toHexString(device.getProductId()));
    }

    @Override
    public CharSequence onBuildingDevicesList(UsbDevice usbDevice) {
        return "devID:" + usbDevice.getDeviceId() + " VID:" + Integer.toHexString(usbDevice.getVendorId()) + " PID:" + Integer.toHexString(usbDevice.getProductId()) + " " + usbDevice.getDeviceName();
    }

    @Override
    public void onUSBDataSending(byte[] data) {
        mLog("Sending: " + USBUtils.bytesToHex(data));
    }

    @Override
    public void onUSBDataSended(int status, byte[] out) {
//        mLog("Sended " + status + " bytes");
//        if(status > 0 ){
//            mLog("Sended: " + USBUtils.bytesToHex(out));
//        }

//        for (int i = 0; i < out.length && out[i] != 0; i++) {
//            mLog(" " + USBUtils.toInt(out[i]));
//        }
    }

    @Override
    public void onSendingError(Exception e) {
        mLog("Please check your bytes, sent as text");
    }

    public void processBuffer1(byte[] buffer) {
        byte device_state;
        byte map_state;

        int frame_number;
        float time_stamps;

        device_state = buffer[1];
        map_state = buffer[2];
        frame_number = (int) ((buffer[4]&0xff) << 8 | buffer[3]&0xff);

        if (device_state <= 5 && device_state >=0 && map_state >=0 && map_state <=9) {
           // if (frame_count != frame_number) {
                frame_count = frame_number;

                int tmp_int;

                //MotionCompact->time: int (was float), buffer[5...8]
                tmp_int = (buffer[8]&0xff) << 24 | (buffer[7]&0xff) << 16 | (buffer[6]&0xff) << 8 | (buffer[5]&0xff);
                time_stamps = (float) (tmp_int/1024.0f);


                tmp_int = (USBUtils.toByte(buffer[10])) *256 + buffer[9];
                pose_rot[0] = (float) (tmp_int/16384.0f);
                tmp_int = (USBUtils.toByte(buffer[12])) *256 + buffer[11];
                pose_rot[1] = (float) (tmp_int/16384.0f);
                tmp_int = (USBUtils.toByte(buffer[14])) *256 + buffer[13];
                pose_rot[2] = (float) (tmp_int/16384.0f);
                tmp_int = (USBUtils.toByte(buffer[16])) *256 + buffer[15];
                pose_rot[3] = (float) (tmp_int/16384.0f);
                tmp_int = (USBUtils.toByte(buffer[18])) *256 + buffer[17];
                pose_rot[4] = (float) (tmp_int/16384.0f);
                tmp_int = (USBUtils.toByte(buffer[20])) *256 + buffer[19];
                pose_rot[5] = (float) (tmp_int/16384.0f);
                tmp_int = (USBUtils.toByte(buffer[22])) *256 + buffer[21];
                pose_rot[6] = (float) (tmp_int/16384.0f);
                tmp_int = (USBUtils.toByte(buffer[24])) *256 + buffer[23];
                pose_rot[7] = (float) (tmp_int/16384.0f);
                tmp_int = (USBUtils.toByte(buffer[26])) *256 + buffer[25];
                pose_rot[8] = (float) (tmp_int/16384.0f);

            //skip Unknown bytes: 2 byte of padding

                //float: pose trans: buffer[27...38]
                tmp_int = (USBUtils.toByte(buffer[32])) << 24 | buffer[31] << 16 | buffer[30] << 8 | buffer[29];
                pose_tran[0] = (float) (tmp_int/32768.0f);
                tmp_int = (USBUtils.toByte(buffer[36])) << 24 | buffer[35] << 16 | buffer[34] << 8 | buffer[33];
                pose_tran[1] = (float) (tmp_int/32768.0f);
                tmp_int = (USBUtils.toByte(buffer[40])) << 24 | buffer[39] << 16 | buffer[38] << 8 | buffer[37];
                pose_tran[2] = (float) (tmp_int/32768.0f);

                //short: pose angle buffer[39...44], 3x4 = 12 bytes
                tmp_int = (USBUtils.toByte(buffer[42])) *256 + buffer[41];
                pose_angle[0] = (float) (tmp_int/128.0f);
                tmp_int = (USBUtils.toByte(buffer[44])) *256 + buffer[43];
                pose_angle[1] = (float) (tmp_int/128.0f);
                tmp_int = (USBUtils.toByte(buffer[46])) *256 + buffer[45];
                pose_angle[2] = (float) (tmp_int/128.0f);

            //skip Unknown bytes: 2 byte of padding

                //short: accelDatat : buffer[45..50]
                tmp_int = (USBUtils.toByte(buffer[50])) *256 + buffer[49];
                accelData[0] = (float) (tmp_int/128.0f);
                tmp_int = (USBUtils.toByte(buffer[52])) *256 + buffer[51];
                accelData[1] = (float) (tmp_int/128.0f);
                tmp_int = (USBUtils.toByte(buffer[54])) *256 + buffer[53];
                accelData[2] = (float) (tmp_int/128.0f);

                //short: gyroData : buffer[51..56]
                tmp_int = (USBUtils.toByte(buffer[56])) *256 + buffer[55];
                gyroData[0] = (float) (tmp_int/128.0f);
                tmp_int = (USBUtils.toByte(buffer[58])) *256 + buffer[57];
                gyroData[1] = (float) (tmp_int/128.0f);
                tmp_int = (USBUtils.toByte(buffer[60])) *256 + buffer[59];
                gyroData[2] = (float) (tmp_int/128.0f);

                mLog("device state: " + device_state + "\n" +
                        "map state: " + map_state + "\n" +
                        "frame_number: " + frame_number + "\n" +
                        "time_stamps: " + time_stamps +"\n" +
                        "pose_rot: " + pose_rot[0] + " " + pose_rot[1] + " " + pose_rot[2] + "\n" +
                        "pose_rot: " + pose_rot[3] + " " + pose_rot[4] + " " + pose_rot[5] + "\n" +
                        "pose_rot: " + pose_rot[6] + " " + pose_rot[7] + " " + pose_rot[8] + "\n" +
                        "pose_tran: " + pose_tran[0] + " " + pose_tran[1] + " " + pose_tran[2] + "\n" +
                        "pose_angle: " + pose_angle[0] + " " + pose_angle[1] + " " + pose_angle[2] + "\n" +
                        "accelData: " + accelData[0] + " " + accelData[1] + " " + accelData[2] + "\n" +
                        "gyroData: " + gyroData[0] + " " + gyroData[1] + " " + gyroData[2] + "\n"
                );
        }
    }
    float[] rotation_to_quaternion(float rotation[])
    {
        float quat[] = new float[4]; //// qr, qi, qj, qk

        float traceR = rotation[0] + rotation[4] + rotation[8];

        if (traceR >= (float)0.0f)
        {
            //// traceR is not close to -1
            quat[0] = (float) ((float)0.5f * Math.sqrt((float)1.0f + traceR));
            float inverse4qr = (float)0.25f / quat[0];//qr;

            quat[1] = (rotation[7] - rotation[5]) * inverse4qr;
            quat[2] = (rotation[2] - rotation[6]) * inverse4qr;
            quat[3] = (rotation[3] - rotation[1]) * inverse4qr;
        }
        else
        {
            //// the following is safe
            int i = 0;
            if (rotation[4] > rotation[0])    i = 1;
            if (rotation[8] > rotation[4*i])    i = 2;

            int j = (i + 1) % 3;
            int k = (j + 1) % 3;
            float t = (float) Math.sqrt(rotation[4*i] - rotation[4*j] - rotation[4*k] + (float)1.0f);
            quat[i + 1] = (float)0.5f * t;
            t = (float)0.5f / t;
            quat[0] = (rotation[3*k+j] - rotation[3*j+k]) * t;
            quat[j + 1] = (rotation[3*j+i] + rotation[3*i+j]) * t;
            quat[k + 1] = (rotation[3*k+i] + rotation[3*i+k]) * t;
        }

        return quat;
    }

    float[] rotation_to_euler(float rotation[])
    {
        float alpha, beta, gamma;
        float arr[] = new float[3];
        float ff = -1.0f;
        Log.d("hid test","rotation in converter :\n"+ rotation[0]+
        ", "+rotation[1] +", "+rotation[2]+", "+rotation[3]+
                ", "+rotation[4] +", "+rotation[5]+", "+rotation[6]+
                ", "+rotation[7] +", "+rotation[8]+"\n"+"-1f is : "+ ff
        );

        if ((rotation[5] != (float)1.0f) && (rotation[5] != (float)-1.0f))
        {
            alpha = (float) ((float) (-1.0f) * Math.asin(rotation[5]));
            float csx = (float) Math.cos(alpha);
            beta = (float) Math.atan2(rotation[2]/csx, rotation[8]/csx);
            gamma = (float) Math.atan2(rotation[3]/csx, rotation[4]/csx);
            // Caution: there are 2 cases
        }
        else
        {
            beta = (float)0.0f; // anything, can set to 0
            if (rotation[5] == (float)-1.0f)
            {
                alpha = (float) (Math.PI / (float)2.0f);
                gamma = (float) (beta + Math.atan2((float) ((float)-1.0f) *rotation[1], rotation[0]));
            }
            else
            {
                alpha = (float) ((float) ((float)-1.0f) * Math.PI / (float)2.0f);
                gamma = (float) ((float) ((float)-1.0f) *beta + Math.atan2((float) ((float)-1.0f) *rotation[1], rotation[0]));
            }
        }
        Log.d("\n$$$$$$$  hid test","alpha: "+ alpha+ ",beta: "+beta+",gamma: "+gamma+"\n");
        arr[0] = alpha;
        arr[1] = beta;
        arr[2] = gamma;
        return arr;
    }
    float rad_to_deg(float x)
    {
        x = (float) (x / Math.PI * (float)180.0);
        return x;
    }
    /*
    public void processBuffer22(byte[] buffer) throws IOException {
        // Reading data (packet 64 bytes)
        DataInputStream data;
        data = new DataInputStream((new ByteArrayInputStream(buffer)));

        int im = 0;
        while (im++ < 64) {
            int lInconnu = data.readUnsignedByte();//need to skip the first byte, always is 2
            Log.d("hid test", "check byte no " + im + " is: " + lInconnu);
        }
    }*/

    public void processBuffer2(byte[] buffer) throws IOException {
        // Reading data (packet 64 bytes)
        DataInputStream data;
        byte[] buffer1 = new byte[64];
        System.arraycopy(buffer, 0, buffer1, 0, 64);
        data = new DataInputStream((new ByteArrayInputStream(buffer1)));
        data.readByte();
        data.readByte();
        data.readByte();

        byte[] lShortArray = new byte[2];
        byte[] lIntArray = new byte[4];
        byte[] lFloatArray = new byte[4];
        long lFrameTime;
        short lShortValue;
        float lValue;
        float rot[] = new float[9];

        long lFrame1 = data.readUnsignedByte();
        long lFrame2 = data.readUnsignedByte();
        long lFrame3 = data.readUnsignedByte();
        long lFrame4 = data.readUnsignedByte();
        long lft = lFrame1+lFrame2 * 256 + lFrame3 * 65536 + lFrame4*16777216;
        lFrameTime = lft;
        if (prevTime == lFrameTime){
            return;
        } else {
            prevTime = lFrameTime;
        }
        float timestamp = (float)lFrameTime/(1000f*1000f);
        Log.d("hid test","enter process buffer" );

        String lMessage = "\nonUSBDataReceive:\ntime: "+String.format("%.9f",timestamp);
        String bufToWr = "";

        lMessage += "\npose_translation: ";
        // pose_translation: float data
        data.readFully(lIntArray);
        int lPosXint = ByteBuffer.wrap(lIntArray).order(ByteOrder.LITTLE_ENDIAN).getInt();//data.readInt();//ByteBuffer.wrap(lFloatArray).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        data.readFully(lIntArray);
        int lPosYint = ByteBuffer.wrap(lIntArray).order(ByteOrder.LITTLE_ENDIAN).getInt();//data.readInt();//ByteBuffer.wrap(lFloatArray).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        data.readFully(lIntArray);
        int lPosZint = ByteBuffer.wrap(lIntArray).order(ByteOrder.LITTLE_ENDIAN).getInt();//data.readInt();//ByteBuffer.wrap(lFloatArray).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        float lPosX = lPosXint / 16384.0f;
        float lPosY = lPosYint / 16384.0f;
        float lPosZ = lPosZint / 16384.0f;
        lMessage += String.format("%.6f", lPosX) +"; " +String.format("%.6f", lPosY) +"; " + String.format("%.6f", lPosZ)+"; ";
        bufToWr += String.format("%.9f", lPosX) +"; " +String.format("%.9f", lPosY) +"; " + String.format("%.9f", lPosZ)+"; ";

        // pose_rotation: short data
        lMessage += "\npose_rotation:\n";

        for(int i=0; i < 9; i++)
        {
            short shortv1 = data.readByte();
            short shortv2 = data.readByte();
            lShortValue = (short) (shortv1 + (short) (shortv2 * 256));
            // Conversion to float
            lValue = lShortValue / 16384.0f;
            lMessage += lValue;
            rot[i] = lValue;
            if(i<8)
            {
                lMessage += "; ";
            }
            if (i == 2|| i==5)
            {
                lMessage +="\n";
            }
        }
        //lMessage+=lMessage;
        int lFrameNumber1 = data.readUnsignedByte();
        int lFrameNumber2 = data.readUnsignedByte();
//        int lFrameNumber = lFrameNumber1 + lFrameNumber2*256;
//        lMessage += String.format("\nframe no:\n %d;\n ", lFrameNumber);

        float quat4[] = new float[4];
        quat4 = rotation_to_quaternion(rot);
        bufToWr += String.format("%.9f; ", quat4[0])+String.format("%.9f; ", quat4[1])
                     +String.format("%.9f; ", quat4[2])+String.format("%.9f; ", quat4[3]);

        float angle[] = new float[3];
        //angle = rotation_to_euler(rot);
        angle[0] = rad_to_deg((float) Math.atan2(rot[7],rot[8]));
        angle[1] = rad_to_deg((float) Math.atan2((-1.0f)*rot[6],Math.sqrt(rot[7]*rot[7]+rot[8]*rot[8])));
        angle[2] = rad_to_deg((float) Math.atan2(rot[3],rot[0]));
        bufToWr += String.format("%.9f; ", angle[0])+String.format("%.9f; ", angle[1])
                +String.format("%.9f; ", angle[2]);
//        lMessage +="ANGEL:\n";
//        lMessage += String.format("%.6f", angle[0]) +"; " +String.format("%.6f", angle[1]) +"; " + String.format("%.6f", angle[2])+"; ";

        bufToWr += String.format("%.9f",timestamp) + "\n";
        if(super.fileN != null && lFrameTime != 0)
            outputStream1.write(bufToWr.getBytes());

        //Log.d("hid test", "FILE NAME IS : "+ super.fileN);

        //if(super.fileN != null && lFrameTime != 0) {
            //Log.d("hid test","begin save, filename :" + super.fileN);
            //saveFile(bufToWr, /*"save.txt"*/ super.fileN);
            //Log.d("hid test","end save");
        //}

        lMessage +="\n";

/*
        lMessage += "accelData: ";
        for(int i=0; i < 3; i++)
        {
            data.readFully(lShortArray);

            lShortValue = ByteBuffer.wrap(lShortArray).order(ByteOrder.LITTLE_ENDIAN).getShort();

            // Conversion to float
            lValue = lShortValue / 128.0f;
            String.format("%.6f", lValue);
            lMessage += lValue + ";";
        }

        lMessage +="\n";

        lMessage += "gyroData: ";
        for(int i=0; i < 3; i++)
        {
            data.readFully(lShortArray);

            lShortValue = ByteBuffer.wrap(lShortArray).order(ByteOrder.LITTLE_ENDIAN).getShort();

            // Conversion to float
            lValue = lShortValue / 128.0f;
            String.format("%.6f", lValue);
            lMessage += lValue + ";";
        }

        lMessage +="\n";
        lMessage += "MegData: ";
        for(int i=0; i < 3; i++)
        {
            data.readFully(lShortArray);

            lShortValue = ByteBuffer.wrap(lShortArray).order(ByteOrder.LITTLE_ENDIAN).getShort();

            // Conversion to float
            lValue = lShortValue / 128.0f;
            String.format("%.6f", lValue);
            lMessage += lValue + ";";
        }
        int lMapState = data.readUnsignedByte();
        int confidenceLevel = data.readUnsignedByte();
        lMessage += "\nonUSBDataReceive:\nmap state: "+ lMapState + "\nconfidenceLevel: "+confidenceLevel+ "; ";
        mLog(lMessage);
*/
        //Log.d("hid test", "-----start here----\n"+lMessage+"++++STOP HERE+++++\n");
//        data.readUnsignedByte();
//        data.readUnsignedByte();data.readUnsignedByte();data.readUnsignedByte();
        //Log.d("hid test","exitt process buffer" );

    }

    Handler myHandler = new Handler() {
        // 接收到消息后处理
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case 1:
                    lock_b.lock();
                    int size =  outputStream.size();
                    Log.d("hid test","buffersize: "+size);
                    if (size > 0) {
                        byte[] tmp = outputStream.toByteArray();//new byte[size];

                        System.arraycopy(tmp, 0, gBuffer, 0, 64);
                        outputStream.reset();
                        outputStream.write(tmp, 64, size - 64);
                    }
                    lock_b.unlock();

                    lock.lock();
                    try {
                        processBuffer2(gBuffer); // 刷新界面
                    }catch (IOException e) {
                        e.printStackTrace();
                    }finally {
                        lock.unlock(); // 释放锁
                    }
                    break;
            }
        }
    };
    @Override
    public void onUSBDataReceive(byte[] buffer) {
        if (isValidMessage(buffer)){
            validMessageCounter++;
        }
        else{
            nonValidMessageCounter++;
        }

        long time = System.currentTimeMillis();
        if (lastStatusUpdateTime == 0){
            lastStatusUpdateTime = time;
        }
        else{
            long timeElapsed = time - lastStatusUpdateTime;

            if (timeElapsed > statusUpdateTimeFrame){
                double elapsedTimeInSeconds = (double) timeElapsed / (double) 1000.0f;
                double messagesPerSecond = validMessageCounter / elapsedTimeInSeconds;
                mLog("\nMessages per second received: "+ String.format("%.2f", messagesPerSecond)+" total messages received: "+validMessageCounter);
                validMessageCounter = 0;
                lastStatusUpdateTime = time;
                nonValidMessageCounter = 0;
            }
        }

//        mLog(USBUtils.bytesToHex(buffer));
/*
        if (validMessageCounter % 50 == 0)
            processBuffer1(buffer);
*/
        //if (validMessageCounter % 1/*50*/ == 0)
        try {
            //gBuffer = buffer;
            lock_b.lock();
            outputStream.write(buffer);
            lock_b.unlock();
            Message message = new Message();
            message.what = this.REFRESH;
            this.myHandler.sendMessage(message);

            //processBuffer2(buffer);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(super.needWrite)
        {
            if(super.fileN != null) {
            Log.d("hid test","begin save to"+super.fileN);
            saveFile(outputStream1,  super.fileN);
            outputStream1.reset();
            outputStream.reset();
            Log.d("hid test","end save");
            }
            super.needWrite = false;
        }
    }

    private void processArduinoMessage(byte[] buffer) {
        if (buffer[0] == 'a') {
            if (validMessageCounter % 10 == 0) {
                decodeMessage(buffer);
            }
        }
        if (buffer[15] == 'z') {
            validMessageCounter++;
        }
        byte[] byteForTimestamp = Arrays.copyOfRange(buffer, 8, 16);
        String s = USBUtils.bytesToHex(byteForTimestamp);

        long messageTimestamp = Long.parseLong(s, 16);
    }


    private void decodeMessage(byte[] buffer){
        long systemTimestamp = System.currentTimeMillis();
        //mLog(USBUtils.bytesToHex(buffer)+"\n");
        long messageCount = buffer[1] << 24 | buffer[2] << 16 | buffer[3] << 8 | buffer[4];
        byte[] byteForTimestamp = Arrays.copyOfRange(buffer, 8, 16 );
        String s = USBUtils.bytesToHex(byteForTimestamp);

        long messageTimestamp = Long.parseLong(s, 16);

        lastDeltaTimestamp = systemTimestamp - messageTimestamp;
        mLog("Msg: "+messageCount+" timestamp: "+messageTimestamp+" system timestamp:"+systemTimestamp +" deltatimestamp: "+lastDeltaTimestamp+"\n");
    }


    private boolean isValidMessage(byte[] buffer){
       return true;
    }

    private void mLog(String log) {
        eventBus.post(new LogMessageEvent(log));
    }


    public static void saveFile(ByteArrayOutputStream buf, String fileName) {
        // 创建String对象保存文件名路径
        RandomAccessFile raf = null;
        //FileWriter fw = null;
        try {
            // 创建指定路径的文件
            File file = new File(Environment.getExternalStorageDirectory(), fileName);

            raf = new RandomAccessFile(file, "rw");
            raf.seek(file.length());
            raf.write(buf.toByteArray());

            //using filewriter
            //fw = new FileWriter(Environment.getExternalStorageDirectory()+"/"+fileName,true);
            //fw.write(str);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                }
                //fw.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
       /*
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileWriter writer = new FileWriter(fileName, true);
            writer.write(str);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }*/

    }
    /**
     * 删除已存储的文件
     */
    public static void deletefile(String fileName) {
        try {
            // 找到文件所在的路径并删除该文件
            File file = new File(Environment.getExternalStorageDirectory(), fileName);
            file.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * 读取文件里面的内容
     *
     * @return
     */
    public static String getFile(String fileName) {
        try {
            // 创建文件
            File file = new File(Environment.getExternalStorageDirectory(),fileName);
            // 创建FileInputStream对象
            FileInputStream fis = new FileInputStream(file);
            // 创建字节数组 每次缓冲1M
            byte[] b = new byte[1024];
            int len = 0;// 一次读取1024字节大小，没有数据后返回-1.
            // 创建ByteArrayOutputStream对象
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // 一次读取1024个字节，然后往字符输出流中写读取的字节数
            while ((len = fis.read(b)) != -1) {
                baos.write(b, 0, len);
            }
            // 将读取的字节总数生成字节数组
            byte[] data = baos.toByteArray();
            // 关闭字节输出流
            baos.close();
            // 关闭文件输入流
            fis.close();
            // 返回字符串对象
            return new String(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }
}
