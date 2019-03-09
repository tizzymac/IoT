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
    private static String BLE_ADDRESS;
    private static final boolean raw = true;
    private RxBleDevice device;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    private boolean connected = false;

    private int peopleCountBoard;
    private int resetNeeded;
    private int pirTriggered;
    private int tofTriggered;
    private List<Integer> tenTOFreadings;
    private int counter;
    private boolean logging;
    private int lastReading;

    private char tag;

    public Board(Context context, String bleAddress, char tag) {

        this.tag = tag;
        this.BLE_ADDRESS = bleAddress;

        this.peopleCountBoard = 0;
        this.resetNeeded = 0;
        this.pirTriggered = 2;
        this.tofTriggered = 2;
        this.tenTOFreadings = new ArrayList<>();
        this.counter = 0;
        this.logging = false;
        this.lastReading = 0;

        this.packetData = ByteBuffer.allocate(18);
        this.packetData.order(ByteOrder.LITTLE_ENDIAN);

        this.rxBleClient = RxBleClient.create(context);

        this.device = rxBleClient.getBleDevice(BLE_ADDRESS);
    }

    public RxBleDevice getDevice() {
        return device;
    }

    public void resetList() {
        this.tenTOFreadings = new ArrayList<>();
    }

    public void addTofReading() {
        tenTOFreadings.add(tofTriggered);
    }

    public int getListSize() {
        return tenTOFreadings.size();
    }

    public static String getBleAddress() {
        return BLE_ADDRESS;
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
