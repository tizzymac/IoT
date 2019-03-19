package com.specknet.orientandroid;

import android.content.Context;
import android.util.Log;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Board {
    private char boardID;

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
    private int[] pirTriggered = {2,2,2,2,2};
    private TOFSensor tof1;
    private TOFSensor tof2;

    // Two people
    private static long time_2P;
    private static int firstSensor_2P;
    private static boolean twoPeople;

    private boolean logging;
    private int firstSensor;

    public Board(Context context, String bleAddress, char boardID) {

        this.boardID = boardID;
        this.ble_address = bleAddress;

        this.tof1 = new TOFSensor(1);
        this.tof2 = new TOFSensor(2);

        this.logging = false;
        this.firstSensor = 0;

        time_2P = 0;
        firstSensor_2P = 0;
        twoPeople = false;

        this.packetData = ByteBuffer.allocate(18);
        this.packetData.order(ByteOrder.LITTLE_ENDIAN);

        this.rxBleClient = RxBleClient.create(context);
        this.device = rxBleClient.getBleDevice(ble_address);
    }

    public char getBoardID() {
        return boardID;
    }

    // Connect
    public RxBleDevice getDevice() {
        return device;
    }
    public boolean isConnected() {
        return connected;
    }
    public void setConnected(boolean connected) {
        this.connected = connected;
    }
    public boolean isLogging() {
        return logging;
    }
    public void setLogging(boolean logging) {
        this.logging = logging;
    }
    // ***

    // Get packet data
    public void putPacketData(byte[] data) {
        packetData.clear();
        packetData.put(data);
        packetData.position(0);
    }
    public short getPacketDataShort() { return packetData.getShort(); }
    public int getPacketDataInt() {
        return packetData.getInt();
    }
    // ***

    // PIRs
    public void setPirTriggered(int id, short s) { pirTriggered[id-1] = s; }
    public int getPirTriggered(int id) { return pirTriggered[id-1]; }

    // TOFs
    public void tofTriggered(int tofID, int value) {
        switch (tofID) {
            case 1 : this.tof1.setTofTriggered(value);
                     break;
            case 2 : this.tof2.setTofTriggered(value);
                     break;
        }
    }
    public void personPassed(int boardID) {
        // Find direction
        Log.d("TOF_READING", "firstSensor: " + firstSensor);

        if (boardID == 1) {
            if (firstSensor == 2) {
                // Check if it's two people
                if (twoPeople) {
                    // Count 2 people
                    Log.d("TOF_READING", "Two People");
                    MainActivity.personEnters(2);
                    twoPeople = false;
                } else {
                    // Count a person!
                    MainActivity.personEnters(1);
                }
                firstSensor = 0;
            } else {
                firstSensor = 1;
                // wait for other board to count person
            }
        }

        if (boardID == 2) {
            if (firstSensor == 1) {
                // Check if it's two people
                if (twoPeople) {
                    // Count 2 people
                    Log.d("TOF_READING", "Two People");
                    MainActivity.personExits(2);
                    twoPeople = false;
                } else {
                    // Count a person!
                    MainActivity.personExits(1);
                }
                firstSensor = 0;
            } else {
                firstSensor = 2;
                // wait for other board to count person
            }
        }
    }

    public static void twoPeoplePassing(int boardID, long time) {
        // If both boards display reading of < 200 at the same time
        // it's likely that two people are passing through the door at the same time

        if (!twoPeople) {
            if (firstSensor_2P == 0) {
                firstSensor_2P = boardID;
                time_2P = time;
            } else {
                if (firstSensor_2P == boardID) {
                    //
                } else {
                    if ((time < time_2P + 5) && (time > time_2P - 5)) {
                        // If times are within 5 milliseconds
                        // Count two people
                        twoPeople = true;
                    }
                }
                // reset
                firstSensor_2P = 0;
                time_2P = 0;
            }
        }
    }
}
