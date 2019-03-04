package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {

    // Cloud
    private FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();
    Map<String, Integer> readings = new HashMap<>();

    private static String ORIENT_BLE_ADDRESS = "D3:06:E2:FD:ED:04"; //Tizzy's board 2nd semester
    //private static String ORIENT_BLE_ADDRESS = "C9:22:1F:AA:18:54";

    //characteristics for the board
    private static final String ORIENT_QUAT_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";
    private static final String ORIENT_RAW_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    //characteristics for the Orient sensor
    //private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    //private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";

    // Bluetooth
    private static final boolean raw = true;
    private RxBleDevice orient_device;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    boolean connected = false;
    private float freq = 0.f;

    int peopleCountBoard = 0;
    int resetNeeded = 0;
    int pirTriggered = 2;
    int tofTriggered = 2;
    List<Integer> tenTOFreadings;
    private int counter = 0;
    private boolean logging = false;

    private Context ctx;
    private TextView occupancyNumberView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        occupancyNumberView = findViewById(R.id.numberView);

        tenTOFreadings = new ArrayList<>();

        peopleCountBoard = 0;
        resetNeeded = 1;
        counter = 0;

        packetData = ByteBuffer.allocate(18);
        packetData.order(ByteOrder.LITTLE_ENDIAN);

        rxBleClient = RxBleClient.create(this);
        connectToOrient(ORIENT_BLE_ADDRESS);
        System.out.println("Connecting to orient...");

//        /* Firebase Test */
//        readings.put("time1", 1);
//        readings.put("time2", 2);
//
//        mFirestore.collection("PIR_readings").document("hello")
//                .set(readings);
//
//        readings.put("time3", 3);
//        mFirestore.collection("PIR_readings").document("hello")
//                .set(readings);
//        /* * * * * * * * */


    }

    private void connectToOrient(String addr) {
        orient_device = rxBleClient.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC; else characteristic = ORIENT_QUAT_CHARACTERISTIC;

        System.out.println("Establishing connection...");
        orient_device.establishConnection(false)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            if (!connected) {
                                connected = true;
                                System.out.println("Connected");
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Receiving sensor data",
                                           Toast.LENGTH_SHORT).show();
                                    logging = true;
                                });
                            }
                            if (raw) handleRawPacket(bytes);
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());
                        }
                );
    }

    private void handleRawPacket(final byte[] bytes) {
        long ts = System.currentTimeMillis();
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        String timestamp = String.valueOf(new Date().getTime());

        //pirTriggered = packetData.getInt();
        tofTriggered  = packetData.getInt();

        if (logging) {

            if (counter % 12 == 0) {

                runOnUiThread(() -> {
                    occupancyNumberView.setText("" + tofTriggered);
                    tenTOFreadings.add(tofTriggered);

                    // Lowest reading of every 10
                    if (tenTOFreadings.size() == 10) {
                        // Get minimum
                        int minReading = Collections.min(tenTOFreadings);
                        occupancyNumberView.setText("min: " + minReading);

                        // Send minimum to cloud
                        readings.put(String.valueOf(new Date().getTime()), minReading);
                        mFirestore.collection("TOF_readings")
                                .document("min_in_10")
                                .set(readings);

                        // Reset list
                        tenTOFreadings = new ArrayList<>();
                    }
                });
            }
            counter += 1;
        }
    }

}