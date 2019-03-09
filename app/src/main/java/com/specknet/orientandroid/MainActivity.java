package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    // Boards
    Board boardN;
    Board boardT;

    // IoT Core
//    private IotCoreCommunicator communicator;
//    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        boardN = new Board(ctx, ORIENT_BLE_ADDRESS_n, 'n');
        boardT = new Board(ctx, ORIENT_BLE_ADDRESS_t, 't');

        occupancyNumberView = findViewById(R.id.numberView);

        connectToOrient(boardN);
        Toast.makeText(ctx, "Connected to n", Toast.LENGTH_SHORT).show();

        connectToOrient(boardT);
        Toast.makeText(ctx, "Connected to t", Toast.LENGTH_SHORT).show();

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
            if (board.getCounter() % 3 == 0) {
                runOnUiThread(() -> {
                    if (board.getTag() == 'n') {
                        occupancyNumberView.setText("" + board.getTofTriggered());
                    }
                    board.addTofReading();

                    switch (board.getTag()) {
                        case ('n') :
                            if ((Math.abs(board.getTofTriggered() - board.getLastReading()) > 5) && (board.getTofTriggered() < 1000)) {
                                readings_n.put(String.valueOf(new Date().getTime()), board.getTofTriggered());
                                mFirestore.collection("TOF_readings")
                                        .document(ORIENT_BLE_ADDRESS_n)
                                        .set(readings_n);
                            }
                        case ('t') :
                            if ((Math.abs(board.getTofTriggered() - board.getLastReading()) > 5) && (board.getTofTriggered() < 1000)) {
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