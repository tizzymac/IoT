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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private Context ctx;
    private TextView occupancyNumberView;
    private TextView occupancyNumberView2;

    // Boards
    Board boardN;
    Board boardT;

    private int peopleCount;
    private int peopleCount2;

//    // IoT Core
//    private IotCoreCommunicator communicator;
//    private Handler handler;

    // People counting flags
    AtomicBoolean under500 = new AtomicBoolean(false);
    AtomicBoolean over1000 = new AtomicBoolean(false);
    AtomicBoolean under500n = new AtomicBoolean(false);
    AtomicBoolean over1000n = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        boardN = new Board(ctx, ORIENT_BLE_ADDRESS_n, 'n');
        boardT = new Board(ctx, ORIENT_BLE_ADDRESS_t, 't');

        occupancyNumberView = findViewById(R.id.numberView);
        occupancyNumberView2 = findViewById(R.id.numberView2);
        peopleCount = 0;
        peopleCount2 = 0;

        connectToOrient(boardN);
        Toast.makeText(ctx, "Connecting to n", Toast.LENGTH_SHORT).show();

        connectToOrient(boardT);
        Toast.makeText(ctx, "Connecting to t", Toast.LENGTH_SHORT).show();

//        /* IoT Core Test */
//        // Setup the communication with your Google IoT Core details
//        communicator = new IotCoreCommunicator.Builder()
//                .withContext(this)
//                .withCloudRegion("europe-west1")
//                .withProjectId("trans-sunset-231415")
//                .withRegistryId("iot_android_app")
//                .withDeviceId("test-device")
//                .withPrivateKeyRawFileId(R.raw.rsa_private)
//                //.withPrivateKeyRawFileId(R.raw.rsa_private_pkcs8)
//                .build();
//        HandlerThread thread = new HandlerThread("MyBackgroundThread");
//        thread.start();
//        handler = new Handler(thread.getLooper());
//        handler.post(connectOffTheMainThread); // Use whatever threading mechanism you want
//        /* *** */

    }

    private void connectToOrient(Board board) {
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC; else characteristic = ORIENT_QUAT_CHARACTERISTIC;

        System.out.println("Establishing connection...");
        board.getDevice().establishConnection(false)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            if (!board.isConnected()) {
                                board.setConnected(true);
                                System.out.println("Connected to " + board.getTag());
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Receiving sensor data from " + board.getTag(),
                                           Toast.LENGTH_SHORT).show();
                                    board.setLogging(true);
                                });
                            }
                            if (raw) handleRawPacket(bytes, board);
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());
                        }
                );
    }

    private void handleRawPacket(final byte[] bytes, Board board) {

        board.clearPacketData();
        board.putPacketData(bytes);
        board.setPacketDataPosition(0);
        board.setTofTriggered(board.getPacketDataInt());

        if (board.isLogging()) {
            if (board.getCounter() % 1 == 0) {
                runOnUiThread(() -> {
                    if (board.getTag() == 't') {

//                        occupancyNumberView.setText("" + board.getTofTriggered());
//                    }

                        // ******************************************
                        // at least 5 readings under 500 followed by
                        // Check for readings > 1000 three in a row
                        // TODO in what time period??

                        Log.d("READING_t", "" + board.getTofTriggered());
                        if (under500.get()) {

                            if (over1000.get()) {

                                // Looking for readings over 1000, smaller reading found
                                if ((board.getTofTriggered() < 1000)) {
                                    // person not counted
                                    // reset
                                    board.resetFiveList();
                                    board.resetThreeList();
                                    over1000.set(false);
                                    under500.set(false);

                                    Log.d("STATE_t", "A");
                                }

                                // Looking for readings over 1000, reading found
                                if ((board.getTofTriggered() > 1000) && (board.getThreeSize() < 3)) {
                                    board.addThreeReading();

                                    Log.d("STATE_t", "B");
                                }

                                // Just need one more reading over 1000
                                if ((board.getTofTriggered() > 1000) && (board.getThreeSize() >= 3)) {
                                    // Count a person!
                                    peopleCount++;
                                    occupancyNumberView.setText("" + peopleCount);

                                    // reset
                                    board.resetFiveList();
                                    board.resetThreeList();
                                    over1000.set(false);
                                    under500.set(false);

                                    Log.d("STATE_t", "C");
                                }
                            } else {

                                // Adding readings under 500
                                if ((board.getTofTriggered() < 500) && (board.getFiveSize() < 4)) {
                                    board.addFiveReading();

                                    Log.d("STATE_t", "D");
                                }

                                // Looking for readings under 500 but larger reading seen -> reset
                                if ((board.getTofTriggered() > 1000) && (board.getFiveSize() < 4)) {
                                    under500.set(false);
                                    board.resetFiveList();

                                    Log.d("STATE_t", "E");
                                }

                                // After 5 readings under 500 reached, reading over 1000 seen
                                if ((board.getFiveSize() >= 4) && (board.getTofTriggered() > 1000)) {
                                    // switch to counting threes
                                    over1000.set(true);
                                    board.addThreeReading();

                                    Log.d("STATE_t", "F");
                                }
                            }

                        } else {
                            // initial
                            if ((board.getTofTriggered() < 500) && (board.getFiveSize() == 0)) {
                                under500.set(true);
                                board.addFiveReading();

                                Log.d("STATE", "G");
                            }
                        }

                    } else { // tag == 'n'

                        Log.d("READING_n", "" + board.getTofTriggered());
                        if (under500n.get()) {

                            if (over1000n.get()) {

                                // Looking for readings over 1000, smaller reading found
                                if ((board.getTofTriggered() < 1000)) {
                                    // person not counted
                                    // reset
                                    board.resetFiveList();
                                    board.resetThreeList();
                                    over1000n.set(false);
                                    under500n.set(false);

                                    Log.d("STATE_n", "A");
                                }

                                // Looking for readings over 1000, reading found
                                if ((board.getTofTriggered() > 1000) && (board.getThreeSize() < 3)) {
                                    board.addThreeReading();

                                    Log.d("STATE_n", "B");
                                }

                                // Just need one more reading over 1000
                                if ((board.getTofTriggered() > 1000) && (board.getThreeSize() >= 3)) {
                                    // Count a person!
                                    peopleCount2++;
                                    occupancyNumberView2.setText("" + peopleCount2);

                                    // reset
                                    board.resetFiveList();
                                    board.resetThreeList();
                                    over1000n.set(false);
                                    under500n.set(false);

                                    Log.d("STATE_n", "C");
                                }
                            } else {

                                // Adding readings under 500
                                if ((board.getTofTriggered() < 500) && (board.getFiveSize() < 4)) {
                                    board.addFiveReading();

                                    Log.d("STATE_n", "D");
                                }

                                // Looking for readings under 500 but larger reading seen -> reset
                                if ((board.getTofTriggered() > 1000) && (board.getFiveSize() < 4)) {
                                    under500n.set(false);
                                    board.resetFiveList();

                                    Log.d("STATE_n", "E");
                                }

                                // After 5 readings under 500 reached, reading over 1000 seen
                                if ((board.getFiveSize() >= 4) && (board.getTofTriggered() > 1000)) {
                                    // switch to counting threes
                                    over1000n.set(true);
                                    board.addThreeReading();

                                    Log.d("STATE_n", "F");
                                }
                            }

                        } else {
                            // initial
                            if ((board.getTofTriggered() < 500) && (board.getFiveSize() == 0)) {
                                under500n.set(true);
                                board.addFiveReading();

                                Log.d("STATE", "G");
                            }
                        }
                    }
                    // ************************

                    switch (board.getTag()) {
                        case ('n') :
                            if ((Math.abs(board.getTofTriggered() - board.getLastReading()) > 5) && (board.getTofTriggered() < 500) && (board.getTofTriggered() > 0)) {
                                readings_n.put(String.valueOf(new Date().getTime()), board.getTofTriggered());
                                mFirestore.collection("TOF_readings")
                                        .document(ORIENT_BLE_ADDRESS_n)
                                        .set(readings_n);
                            }
                        case ('t') :
                            if ((Math.abs(board.getTofTriggered() - board.getLastReading()) > 5) && (board.getTofTriggered() < 500) && (board.getTofTriggered() > 0)) {
                                readings_t.put(String.valueOf(new Date().getTime()), board.getTofTriggered());
                                mFirestore.collection("TOF_readings")
                                        .document(ORIENT_BLE_ADDRESS_t)
                                        .set(readings_t);
                            }
                    }
                    board.setLastReading(board.getTofTriggered());

                });
            }
            board.increaseCounter();
        }
    }

//    /* IoT Core bits */
//    private final Runnable connectOffTheMainThread = new Runnable() {
//        @Override
//        public void run() {
//            communicator.connect();
//
//            handler.post(sendMqttMessage);
//        }
//    };
//
//    private final Runnable sendMqttMessage = new Runnable() {
//        private int i;
//
//        /**
//         * We post 10 messages as an example, 1 every 5 seconds
//         */
//        @Override
//        public void run() {
//            if (i == 10) {
//                return;
//            }
//
//            // events is the default topic for MQTT communication
//            String subtopic = "events";
//            // Your message you want to send
//            String message = "Hello World " + i++;
//            communicator.publishMessage(subtopic, message);
//
//            handler.postDelayed(this, TimeUnit.SECONDS.toMillis(5));
//        }
//    };
//
//    @Override
//    protected void onDestroy() {
//        communicator.disconnect();
//        super.onDestroy();
//    }
}