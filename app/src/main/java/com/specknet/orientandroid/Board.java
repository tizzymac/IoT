package com.specknet.orientandroid;

import android.content.Context;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Board {

    //characteristics for the board
    private static final String ORIENT_QUAT_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";
    private static final String ORIENT_RAW_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    // Bluetooth
    private static String ble_address;
    private RxBleDevice device;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    private boolean connected = false;

    // Sensors
    private int[] pirTriggered = {2,2,2};
    private TOFSensor tof1;
    private TOFSensor tof2;

    private int counter;
    private boolean logging;
    private int firstSensor;

    private char tag;

    public Board(Context context, String bleAddress, char tag) {

        this.tag = tag;
        this.ble_address = bleAddress;

        this.tof1 = new TOFSensor(1);
        this.tof2 = new TOFSensor(2);

        this.counter = 0;
        this.logging = false;
        this.firstSensor = 0;

        this.packetData = ByteBuffer.allocate(18);
        this.packetData.order(ByteOrder.LITTLE_ENDIAN);

        this.rxBleClient = RxBleClient.create(context);

        this.device = rxBleClient.getBleDevice(ble_address);
    }

    // PIRs
    public void setPirTriggered(int id, short s) { pirTriggered[id] = s; }
    public int getPirTriggered(int id) { return pirTriggered[id]; }

    public short getPacketDataShort() { return packetData.getShort(); }

    public RxBleDevice getDevice() {
        return device;
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

    public void setTofTriggered(int i, int t) {
        switch (i) {
            case 1 : this.tof1.setTofTriggered(t);
            case 2 : this.tof2.setTofTriggered(t);
        }
    }

    public int getTofTriggered(int i) {
        switch (i) {
            case 1 : return this.tof1.getTofTriggered();
            case 2 : return this.tof2.getTofTriggered();
            default: return -1;
        }
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

    public int getCounter() {
        return counter;
    }

    public boolean isLogging() {
        return logging;
    }

    public void setLogging(boolean logging) {
        this.logging = logging;
    }

    public void personPassed(int boardID) {
        // Find direction

        if (boardID == 1) {
            if (firstSensor == 2) {
                // Count a person!
                MainActivity.personEnters();
                firstSensor = 0;
            } else {
                firstSensor = 1;
                // wait for other board to count person
            }
        }

        if (boardID == 2) {
            if (firstSensor == 1) {
                // Count a person!
                MainActivity.personExits();
                firstSensor = 0;
            } else {
                firstSensor = 2;
                // wait for other board to count person
            }
        }
    }
}
