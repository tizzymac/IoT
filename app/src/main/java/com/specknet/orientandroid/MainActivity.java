package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {

    private FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();
    Map<String, Integer> readings = new HashMap<>();
    // Key needs to be a string

    //private static final String ORIENT_BLE_ADDRESS = "C7:BA:D7:9D:F8:2E"; // test device

    //private static String ORIENT_BLE_ADDRESS = "C2:28:8B:24:8E:CB"; //orange Orient sensor
    //private static String ORIENT_BLE_ADDRESS = "EB:50:C6:52:DB:DB"; //purple Orient sensor
    //private static String ORIENT_BLE_ADDRESS = "F2:6D:63:1F:17:33"; //green Orient sensor
    private static String ORIENT_BLE_ADDRESS = "C2:FC:F0:0A:8D:C3"; //our board

    //characteristics for the board
    private static final String ORIENT_QUAT_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";
    private static final String ORIENT_RAW_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    //characteristics for the Orient sensor
    //private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    //private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";

    private static final boolean raw = true;
    private RxBleDevice orient_device;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    boolean connected = false;
    private float freq = 0.f;

    //parameters for board step counting algorithm
    int stepCountBoard = 0;
    int activityBoard = 0;
    int stepsPreviouslyCounted = 0;
    int resetNeeded = 0;
    String activityText = "Standing";


    private String activity = "Standing";


    private int counter = 0;

    private boolean logging = false;

    private Button start_button;
    private Button stop_button;
    private Context ctx;
    private TextView captureStepNumberView;
    private TextView accelTextView;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;


        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        captureStepNumberView = findViewById(R.id.captureStepNumberView);
        accelTextView = findViewById(R.id.accelTextView);


        start_button.setOnClickListener(v-> {

            stepCountBoard = 0;
            resetNeeded = 1;
            start_button.setEnabled(false);


            // make a new filename based on the start timestamp
            logging = true;
            counter = 0;
            stop_button.setEnabled(true);
        });

        stop_button.setOnClickListener(v-> {
            logging = false;
            stop_button.setEnabled(false);
            start_button.setEnabled(true);
        });

        packetData = ByteBuffer.allocate(18);
        packetData.order(ByteOrder.LITTLE_ENDIAN);

        rxBleClient = RxBleClient.create(this);
        connectToOrient(ORIENT_BLE_ADDRESS);

        /* Firebase Test */
        readings.put("time1", 1);
        readings.put("time2", 2);

        mFirestore.collection("PIR_readings").document("hello")
                .set(readings);

        readings.put("time3", 3);
        mFirestore.collection("PIR_readings").document("hello")
                .set(readings);
        /* * * * * * * * */
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
                               long id) {
        // TODO Auto-generated method stub
        if (parent.getItemAtPosition(position).toString().compareTo("---") == 0) return;

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
    }

    private void connectToOrient(String addr) {
        orient_device = rxBleClient.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC; else characteristic = ORIENT_QUAT_CHARACTERISTIC;

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
                                runOnUiThread(() -> {
                                    //Toast.makeText(ctx, "Receiving sensor data",
                                     //       Toast.LENGTH_SHORT).show();
                                    start_button.setEnabled(true);
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

        if (resetNeeded == 1) {
            stepsPreviouslyCounted = packetData.getInt();
            resetNeeded = 0;
        } else {
            stepCountBoard = packetData.getInt() - stepsPreviouslyCounted;
        }
        activityBoard = packetData.getInt();

        if (stepCountBoard < 0) {
            stepCountBoard = 0;
        }

        if (logging) {

            if (counter % 12 == 0) {

                switch(activityBoard) {
                    case 0: activityText = "Standing";
                            break;
                    case 1: activityText = "Walking";
                        break;
                    case 2: activityText = "Running";
                        break;
                    case 3: activityText = "Descending stairs";
                        break;

                }

                runOnUiThread(() -> {
                    captureStepNumberView.setText("" + stepCountBoard);
                    accelTextView.setText(activityText);
                });
            }

            counter += 1;
        }
    }

}
