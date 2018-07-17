package com.example.usbhidspeedtester.events;
import java.lang.String;

public class USBDataSendEvent {
    private final byte[] data;
    private long startTime = 0;
    private long periodTime = 0;
    private String file = "";
    private boolean needw=false;

    public USBDataSendEvent(byte[] data) {
        this.data = data;
    }

    public USBDataSendEvent(byte[] data, long startTime, long periodTime) {
        this.data = data;
        this.startTime = startTime;
        this.periodTime = periodTime;
    }
    public USBDataSendEvent(byte[] data, long startTime, long periodTime, boolean needw) {
        this.data = data;
        this.startTime = startTime;
        this.periodTime = periodTime;
        this.needw = needw;
    }
    public USBDataSendEvent(byte[] data, long startTime, long periodTime, String file) {
        this.data = data;
        this.startTime = startTime;
        this.periodTime = periodTime;
        this.file = file;
    }

    public byte[] getData() {
        return data;
    }

    public long getStartTime(){
        return startTime;
    }

    public long getPeriodTime(){
        return periodTime;
    }

    public String getFilename(){
        return file;
    }
    public boolean getNeedw(){
        return needw;
    }

}