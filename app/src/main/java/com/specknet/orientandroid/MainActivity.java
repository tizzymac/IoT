package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
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
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    // Cloud
    private FirebaseFirestore mFirestore = FirebaseFirestore.getInstance();
    Map<String, Integer> readings_n = new HashMap<>();
    Map<String, Integer> readings_t = new HashMap<>();

    private static String ORIENT_BLE_ADDRESS_t = "D3:06:E2:FD:ED:04"; //Tizzy's board 2nd semester
    private static String ORIENT_BLE_ADDRESS_n = "C9:22:1F:AA:18:54";

    //characteristics for the board
    private static final String ORIENT_QUAT_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";
    private static final String ORIENT_RAW_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    //characteristics for the Orient sensor
    //private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    //private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";

    // Bluetooth
    private static final boolean raw = true;
    private RxBleDevice orient_device_n, orient_device_t;
    private RxBleClient rxBleClient_n, rxBleClient_t;
    private ByteBuffer packetData_n, packetData_t;

    boolean connected_n, connected_t = false;

    int peopleCountBoard_n, peopleCountBoard_t = 0;
    int resetNeeded_n, resetNeeded_t = 0;
    int pirTriggered_n, pirTriggered_t = 2;
    int tofTriggered_n, tofTriggered_t = 2;
    List<Integer> tenTOFreadings_n, tenTOFreadings_t;
    private int counter_n, counter_t = 0;
    private boolean logging_n, logging_t = false;
    int lastReading_n, lastReading_t = 0;

    private Context ctx;
    private TextView occupancyNumberView;

    // IoT Core
    private IotCoreCommunicator communicator;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        occupancyNumberView = findViewById(R.id.numberView);

        tenTOFreadings_n = new ArrayList<>();
        tenTOFreadings_t = new ArrayList<>();

        peopleCountBoard_n= 0;
        peopleCountBoard_t = 0;
        resetNeeded_n = 1;
        resetNeeded_t = 1;
        counter_n = 0;
        counter_t = 0;

        packetData_n = ByteBuffer.allocate(18);
        packetData_n.order(ByteOrder.LITTLE_ENDIAN);
        packetData_t = ByteBuffer.allocate(18);
        packetData_t.order(ByteOrder.LITTLE_ENDIAN);

        rxBleClient_n = RxBleClient.create(this);
        connectToOrient_n(ORIENT_BLE_ADDRESS_n);
        Toast.makeText(ctx, "Connected to n", Toast.LENGTH_SHORT).show();
        System.out.println("Connecting to orient n...");

        rxBleClient_t = RxBleClient.create(this);
        connectToOrient_t(ORIENT_BLE_ADDRESS_t);
        Toast.makeText(ctx, "Connected to t", Toast.LENGTH_SHORT).show();
        System.out.println("Connecting to orient t...");

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


        /* IoT Core Test */
//        // Setup the communication with your Google IoT Core details
//        communicator = new IotCoreCommunicator.Builder()
//                .withContext(this)
//                .withCloudRegion("europe-west1")
//                .withProjectId("trans-sunset-231415")
//                .withRegistryId("iot_android_app")
//                .withDeviceId("test-device")
//                //.withPrivateKeyRawFileId(R.raw.rsa_private)
//                .withPrivateKeyRawFileId(R.raw.rsa_private_pkcs8)
//                .build();
//        HandlerThread thread = new HandlerThread("MyBackgroundThread");
//        thread.start();
//        handler = new Handler(thread.getLooper());
//        handler.post(connectOffTheMainThread); // Use whatever threading mechanism you want
//        /* *** */

    }

    private void connectToOrient_n(String addr) {
        orient_device_n = rxBleClient_n.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC; else characteristic = ORIENT_QUAT_CHARACTERISTIC;

        System.out.println("Establishing connection...");
        orient_device_n.establishConnection(false)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            if (!connected_n) {
                                connected_n = true;
                                System.out.println("Connected");
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Receiving sensor data",
                                           Toast.LENGTH_SHORT).show();
                                    logging_n = true;
                                });
                            }
                            if (raw) handleRawPacket(bytes, 'n');
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());
                        }
                );
    }

    private void connectToOrient_t(String addr) {
        orient_device_t = rxBleClient_t.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC; else characteristic = ORIENT_QUAT_CHARACTERISTIC;

        System.out.println("Establishing connection...");
        orient_device_t.establishConnection(false)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            if (!connected_t) {
                                connected_t = true;
                                System.out.println("Connected");
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Receiving sensor data",
                                            Toast.LENGTH_SHORT).show();
                                    logging_t = true;
                                });
                            }
                            if (raw) handleRawPacket(bytes, 't');
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());
                        }
                );
    }

    private void handleRawPacket(final byte[] bytes, char c) {
        long ts = System.currentTimeMillis();

        String timestamp = String.valueOf(new Date().getTime());

        switch (c) {
            case ('n') :
                packetData_n.clear();
                packetData_n.put(bytes);
                packetData_n.position(0);
                tofTriggered_n  = packetData_n.getInt();
                if (logging_n) {
                    if (counter_n % 2 == 0) {
                        runOnUiThread(() -> {
                            occupancyNumberView.setText("" + tofTriggered_n);
                            tenTOFreadings_n.add(tofTriggered_n);

                            // Lowest reading of every 10
                            if (tenTOFreadings_n.size() == 10) {

    //                        // Get minimum
    //                        int minReading = Collections.min(tenTOFreadings);
    //                        occupancyNumberView.setText("min: " + minReading);
    //
    //                        // Send minimum to cloud
    //                        readings.put(String.valueOf(new Date().getTime()), minReading);
    //                        mFirestore.collection("TOF_readings")
    //                                .document("min_in_10")
    //                                .set(readings);

                                // Compare every tenth reading with previous tenth reading
                                // If they differ, send the value to the cloud

                                // Reset list
                                tenTOFreadings_n = new ArrayList<>();
                            }

                            if ((Math.abs(tofTriggered_n - lastReading_n) > 5) && (tofTriggered_n < 1000)) {
                                readings_n.put(String.valueOf(new Date().getTime()), tofTriggered_n);
                                mFirestore.collection("TOF_readings")
                                        .document(ORIENT_BLE_ADDRESS_n)
                                        .set(readings_n);
                            }
                            lastReading_n = tofTriggered_n;


                        });
                    }
                    counter_n += 1;
                }
            case ('t'):
                packetData_t.clear();
                packetData_t.put(bytes);
                packetData_t.position(0);
                tofTriggered_t = packetData_t.getInt();
                if (logging_t) {
                    if (counter_t % 2 == 0) {
                        runOnUiThread(() -> {
                            occupancyNumberView.setText("" + tofTriggered_t);
                            tenTOFreadings_t.add(tofTriggered_t);

                            // Lowest reading of every 10
                            if (tenTOFreadings_t.size() == 10) {

                                //                        // Get minimum
                                //                        int minReading = Collections.min(tenTOFreadings);
                                //                        occupancyNumberView.setText("min: " + minReading);
                                //
                                //                        // Send minimum to cloud
                                //                        readings.put(String.valueOf(new Date().getTime()), minReading);
                                //                        mFirestore.collection("TOF_readings")
                                //                                .document("min_in_10")
                                //                                .set(readings);

                                // Compare every tenth reading with previous tenth reading
                                // If they differ, send the value to the cloud

                                // Reset list
                                tenTOFreadings_t = new ArrayList<>();
                            }

                            if ((Math.abs(tofTriggered_t - lastReading_t) > 5) && (tofTriggered_t < 1000)) {
                                readings_t.put(String.valueOf(new Date().getTime()), tofTriggered_t);
                                mFirestore.collection("TOF_readings")
                                        .document(ORIENT_BLE_ADDRESS_t)
                                        .set(readings_t);
                            }
                            lastReading_t = tofTriggered_t;


                        });
                    }
                    counter_t += 1;
                }
        }
    }

    /* IoT Core bits */
    private final Runnable connectOffTheMainThread = new Runnable() {
        @Override
        public void run() {
            communicator.connect();

            handler.post(sendMqttMessage);
        }
    };

    private final Runnable sendMqttMessage = new Runnable() {
        private int i;

        /**
         * We post 10 messages as an example, 1 every 5 seconds
         */
        @Override
        public void run() {
            if (i == 10) {
                return;
            }

            // events is the default topic for MQTT communication
            String subtopic = "events";
            // Your message you want to send
            String message = "Hello World " + i++;
            communicator.publishMessage(subtopic, message);

            handler.postDelayed(this, TimeUnit.SECONDS.toMillis(5));
        }
    };

    @Override
    protected void onDestroy() {
        communicator.disconnect();
        super.onDestroy();
    }
}