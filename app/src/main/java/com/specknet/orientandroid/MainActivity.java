package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.polidea.rxandroidble2.exceptions.BleDisconnectedException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;

public class MainActivity extends Activity {

    private static String ORIENT_BLE_ADDRESS_t = "D3:06:E2:FD:ED:04"; // Tizzy's board 2nd semester
    private static String ORIENT_BLE_ADDRESS_n = "C9:22:1F:AA:18:54"; // Natasa's board

    //characteristics for the board
    private static final String ORIENT_QUAT_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";
    private static final String ORIENT_RAW_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    //characteristics for the Orient sensor
    //private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    //private static final String ORIENT_RAW_CHARACTERISTIC = "00001527-1212-efde-1523-785feabcd125";

    // Bluetooth
    private static final boolean raw = true;

    private Context ctx;
    private static TextView occupancyNumberView;
    private static TextView occupancyNumberView2;

    // Boards
    static Board board_N_PIR;   // Natasa's board
    static Board board_T_TOF;   // Tizzy's board

    private static int peopleCount;

    // Activity data from PIRs
    private Boolean[] pir1ActivityData = new Boolean[60];
    private Boolean[] pir2ActivityData = new Boolean[60];
    private Boolean[] pir3ActivityData = new Boolean[60];

    // IoT Core
    private static IotCoreCommunicator communicator;
    private static Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        occupancyNumberView = findViewById(R.id.numberView);
        occupancyNumberView2 = findViewById(R.id.numberView2);
        peopleCount = 0;
        Arrays.fill(pir1ActivityData, Boolean.FALSE);
        Arrays.fill(pir2ActivityData, Boolean.FALSE);

        // Assumes this board used for PIRs
//        board_N_PIR = new Board(ctx, ORIENT_BLE_ADDRESS_n, 'n');
//        connectToOrient(board_N_PIR);
//        Toast.makeText(ctx, "Connecting to n", Toast.LENGTH_SHORT).show();

        // Assumes this board used for TOFs
        board_T_TOF = new Board(ctx, ORIENT_BLE_ADDRESS_t, 't');
        connectToOrient(board_T_TOF);
        Toast.makeText(ctx, "Connecting to t", Toast.LENGTH_SHORT).show();

        // IoT Core Test
        communicator = new IotCoreCommunicator.Builder()
                .withContext(this)
                .withCloudRegion("europe-west1")
                .withProjectId("trans-sunset-231415")
                .withRegistryId("iot_android_app")
                .withDeviceId("test-device")
                .withPrivateKeyRawFileId(R.raw.rsa_private)
                .build();
        HandlerThread thread = new HandlerThread("MyBackgroundThread");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.post(connectOffTheMainThread); // Use whatever threading mechanism you want

        // TODO: People count need to be read in from cloud at start?

        // TODO: catch BleDisconnectedException
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
                .retryWhen(errors -> errors.flatMap(error -> {
                    if (error instanceof BleDisconnectedException) {
                        runOnUiThread(() -> {
                            Toast.makeText(ctx, "Reconnecting...", Toast.LENGTH_LONG).show();
                                });
                        Log.d("OrientAndroid", "Trying to reconnect...");
                        return Observable.just(new Object());
                    }
                    return Observable.error(error);
                }))
                .subscribe(
                        bytes -> {
                            if (!board.isConnected()) {
                                board.setConnected(true);
                                System.out.println("Connected to " + board.getBoardID());
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Receiving sensor data from " + board.getBoardID(),
                                           Toast.LENGTH_SHORT).show();
                                    board.setLogging(true);
                                });
                            }
                            if (raw) {
                                switch (board.getBoardID()) {
                                    case 'n' : handleRawPIRPacket(bytes, board);
                                    case 't' : handleRawTOFPacket(bytes, board);
                                }
                            }
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());


                        }
                );
    }

    private void handleRawPIRPacket(final byte[] bytes, Board board) {

        board.putPacketData(bytes);

        // assumes order always the same
        board.setPirTriggered(1, board.getPacketDataShort());
        board.setPirTriggered(2, board.getPacketDataShort());
        board.setPirTriggered(3, board.getPacketDataShort());

        if (board.isLogging()) {

            if (board.getCounter() % 1 == 0) {
                runOnUiThread(() -> {

                    // PIR
                    Log.d("PIR", "1:  " + board.getPirTriggered(1));
                    Log.d("PIR", "2:  " + board.getPirTriggered(2));
                    Log.d("PIR", "3:  " + board.getPirTriggered(3));

                    if (board.getPirTriggered(1) == 1) {
                        // update the array for this minute
                        pir1ActivityData[getCurrentMinute()] = true;
                    }
                    if (board.getPirTriggered(2) == 1) {
                        // update the array for this minute
                        pir2ActivityData[getCurrentMinute()] = true;
                    }
                    if (board.getPirTriggered(3) == 1) {
                        // update the array for this minute
                        pir3ActivityData[getCurrentMinute()] = true;
                    }

                    if (getCurrentMinute() == 59) {
                        // send this hour's data to cloud
                        handler.post(sendPIRData);

                        // reset array
                        Arrays.fill(pir1ActivityData, Boolean.FALSE);
                        Arrays.fill(pir2ActivityData, Boolean.FALSE);
                        Arrays.fill(pir3ActivityData, Boolean.FALSE);
                    }
                });
            }
        }
    }

    private void handleRawTOFPacket(final byte[] bytes, Board board) {

        board.putPacketData(bytes);

        // Assumes order is correct
        board.tofTriggered(1, board.getPacketDataInt());
        board.tofTriggered(2, board.getPacketDataInt());

        // TODO: Delete this?
//        if (board.isLogging()) {
//
//            if (board.getCounter() % 1 == 0) {
//                runOnUiThread(() -> {
//                    // Do we even need this?
//                });
//            }
//            board.increaseCounter();
//        }
    }

    public static void personEnters() {
        peopleCount++;
        occupancyNumberView.setText("" + peopleCount);
        occupancyNumberView2.setText("in");

        // Send data to cloud
        //handler.post(updatePeopleCount);
    }

    public static void personExits() {
        peopleCount--;
        occupancyNumberView.setText("" + peopleCount);
        occupancyNumberView2.setText("out");

        // Send data to cloud
        //handler.post(updatePeopleCount);
    }

    /* IoT Core bits */
    private final Runnable connectOffTheMainThread = new Runnable() {
        @Override
        public void run() {
            communicator.connect();
            //handler.post(sendMqttMessage);
        }
    };

    private final Runnable sendPIRData = new Runnable() {
        @Override
        public void run() {
            // Get activity level
            int activeMins1 = 0;
            for (Boolean b : pir1ActivityData) {
                if (b) {
                    activeMins1++;
                }
            }
            int activeMins2 = 0;
            for (Boolean b : pir2ActivityData) {
                if (b) {
                    activeMins2++;
                }
            }
            int activeMins3 = 0;
            for (Boolean b : pir3ActivityData) {
                if (b) {
                    activeMins3++;
                }
            }
            try {
                String subtopic = "events/pir";
                String messageJSON = new JSONObject()
                        .put("Timestamp", new Date().getTime())
                        .put("PIR1Activity", activeMins1)
                        .put("PIR2Activity", activeMins2)
                        .put("PIR3Activity", activeMins3)
                        .toString();
                communicator.publishMessage(subtopic, messageJSON);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }

        }
    };

    private static final Runnable updatePeopleCount = new Runnable() {
        @Override
        public void run() {
            try {
                String subtopic = "events"; // use default subtopic
                String messageJSON = new JSONObject()
                        .put("PeopleInRoom", peopleCount)
                        .put("Timestamp", new Date().getTime())
                        .toString();
                communicator.publishMessage(subtopic, messageJSON);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    // Just for testing
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
            try {
                // events is the default topic for MQTT communication
                String subtopic = "events";
                String messageJSON = new JSONObject()
                        .put("PeopleInRoom", i++)
                        .put("Timestamp", new Date().getTime())
                        .toString();
                communicator.publishMessage(subtopic, messageJSON);

                handler.postDelayed(this, TimeUnit.SECONDS.toMillis(5));

                // View messages :
                // gcloud pubsub subscriptions pull --auto-ack projects/trans-sunset-231415/subscriptions/topic-subscription

            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    @Override
    protected void onDestroy() {
        communicator.disconnect();
        super.onDestroy();
    }

    private int getCurrentMinute() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.MINUTE);
    }


}