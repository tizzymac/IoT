package com.specknet.orientandroid;

import android.content.Context;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Board {

    //characteristics for the board
    private static final String ORIENT_QUAT_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";
    private static final String ORIENT_RAW_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    // Bluetooth
    private static String ble_address;
    private static final boolean raw = true;
    private RxBleDevice device;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    private boolean connected = false;

    private int pir1Triggered;
    private int pir2Triggered;
    private int pir3Triggered;
    private int tofTriggered;
    private List<Integer> fiveUnder500;
    private List<Integer> threeOver100;
    private int counter;
    private boolean logging;
    private int lastReading;

    private char tag;

    public Board(Context context, String bleAddress, char tag) {

        this.tag = tag;
        this.ble_address = bleAddress;

        this.pir1Triggered = 2;
        this.pir2Triggered = 2;
        this.pir3Triggered = 2;
        this.tofTriggered = 2;

        this.fiveUnder500 = new ArrayList<>();
        this.threeOver100 = new ArrayList<>();
        this.counter = 0;
        this.logging = false;
        this.lastReading = 0;

        this.packetData = ByteBuffer.allocate(18);
        this.packetData.order(ByteOrder.LITTLE_ENDIAN);

        this.rxBleClient = RxBleClient.create(context);

        this.device = rxBleClient.getBleDevice(ble_address);
    }

    public void setPir1Triggered(short s) { pir1Triggered = s; }
    public void setPir2Triggered(short s) { pir2Triggered = s; }
    public void setPir3Triggered(short s) { pir3Triggered = s; }

    public int getPir1Triggered() {
        return pir1Triggered;
    }
    public int getPir2Triggered() {
        return pir2Triggered;
    }
    public int getPir3Triggered() {
        return pir3Triggered;
    }

    public short getPacketDataShort() { return packetData.getShort(); }

    public RxBleDevice getDevice() {
        return device;
    }

    public void resetFiveList() {
        this.fiveUnder500 = new ArrayList<>();
    }

    public void addFiveReading() {
        fiveUnder500.add(tofTriggered);
    }

    public int getFiveSize() {
        return fiveUnder500.size();
    }

    public void resetThreeList() {
        this.threeOver100 = new ArrayList<>();
    }

    public void addThreeReading() {
        threeOver100.add(tofTriggered);
    }

    public int getThreeSize() {
        return threeOver100.size();
    }

    public static String getBleAddress() {
        return ble_address;
    }

    public void setLastReading(int lastReading) {
        this.lastReading = lastReading;
    }

    public void increaseCounter() {
        counter++;
    }

    public int getPacketDataInt() {
        return packetData.getInt();
    }

    public void clearPacketData() {
        packetData.clear();
    }
    public void putPacketData(byte[] data) {
        packetData.put(data);
    }
    public void setPacketDataPosition(int i) {
        packetData.position(i);
    }

    public void setTofTriggered(int tofTriggered) {
        this.tofTriggered = tofTriggered;
    }

    public char getTag() {
        return tag;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public int getTofTriggered() {
        return tofTriggered;
    }

    public int getCounter() {
        return counter;
    }

    public boolean isLogging() {
        return logging;
    }

    public void setLogging(boolean logging) {
        this.logging = logging;
    }

    public int getLastReading() {
        return lastReading;
    }
}
