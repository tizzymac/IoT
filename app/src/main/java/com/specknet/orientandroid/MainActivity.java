package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Button;
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

    // Heatmap
    private Button area1;
    private Button area2;
    private Button area3;
    private Button area4;
    private Button area5;

    // Boards
    static Board board_N_PIR;   // Natasa's board
    static Board board_T_TOF;   // Tizzy's board

    private static int peopleCount;

    // Activity data from PIRs
    private Boolean[] pir1ActivityData = new Boolean[20];
    private Boolean[] pir2ActivityData = new Boolean[20];
    private Boolean[] pir3ActivityData = new Boolean[20];
    private Boolean windowReached;

    // IoT Core
    private static IotCoreCommunicator communicator;
    private static Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        occupancyNumberView = findViewById(R.id.numberView);
        peopleCount = 0;

        // Activity levels
        Arrays.fill(pir1ActivityData, Boolean.FALSE);
        Arrays.fill(pir2ActivityData, Boolean.FALSE);
        Arrays.fill(pir3ActivityData, Boolean.FALSE);

        // Heatmap
        area1 = findViewById(R.id.area1);
        area2 = findViewById(R.id.area2);
        area3 = findViewById(R.id.area3);
        area4 = findViewById(R.id.area4);
        area5 = findViewById(R.id.area5);

        // Assumes this board used for PIRs
        board_N_PIR = new Board(ctx, ORIENT_BLE_ADDRESS_n, 'n');
        connectToOrient(board_N_PIR);
        Toast.makeText(ctx, "Connecting to n", Toast.LENGTH_SHORT).show();

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
        if (board.getBoardID() == 'n') {

            board.putPacketData(bytes);

            // assumes order always the same
            board.setPirTriggered(1, board.getPacketDataShort());
            board.setPirTriggered(2, board.getPacketDataShort());
            board.setPirTriggered(3, board.getPacketDataShort());

            // PIR
            Log.d("PIR", "1:  " + board.getPirTriggered(1));
            Log.d("PIR", "2:  " + board.getPirTriggered(2));
            Log.d("PIR", "3:  " + board.getPirTriggered(3));

            if (board.getPirTriggered(1) == 1) {
                // update the array for this minute
                pir1ActivityData[(getCurrentMinute()%20)] = true;
            }
            Log.d("PIR", "Activity 1:" + getActivityLevel(pir1ActivityData));

            if (board.getPirTriggered(2) == 1) {
                // update the array for this minute
                pir2ActivityData[(getCurrentMinute()%20)] = true;
            }
            Log.d("PIR", "Activity 2:" + getActivityLevel(pir2ActivityData));

            if (board.getPirTriggered(3) == 1) {
                // update the array for this minute
                pir3ActivityData[(getCurrentMinute()%20)] = true;
            }
            Log.d("PIR", "Activity 3:" + getActivityLevel(pir3ActivityData));

            // update heatmap
            updateHeatMap();

            // Window size: 20 mins
            if ((getCurrentMinute()%20) == 0) {
                if (windowReached) {

                    // send this time window's data to cloud
                    Log.d("CLOUD", "Sending PIR data to cloud");
                    handler.post(sendPIRData);

                    // reset array
                    Arrays.fill(pir1ActivityData, Boolean.FALSE);
                    Arrays.fill(pir2ActivityData, Boolean.FALSE);
                    Arrays.fill(pir3ActivityData, Boolean.FALSE);
                }
                windowReached = false;
            }
            if (((getCurrentMinute()%20) == 19)) {
                windowReached = true;
            }
        }
    }

    private void handleRawTOFPacket(final byte[] bytes, Board board) {
        if (board.getBoardID() == 't') {

            board.putPacketData(bytes);

            // Assumes order is correct
            board.tofTriggered(1, board.getPacketDataInt());
            board.tofTriggered(2, board.getPacketDataInt());

            runOnUiThread(() -> {
                occupancyNumberView.setText("" + peopleCount);
            });
        }
    }

    public static void personEnters() {
        peopleCount++;
        Log.d("TOF_READING", "Person enters. People count: " + peopleCount);

//        occupancyNumberView.setText("" + peopleCount);

        // Send data to cloud
        Log.d("CLOUD", "Sending TOF data to cloud");
        handler.post(sendPeopleCount);
    }

    public static void personExits() {
        peopleCount--;
        Log.d("TOF_READING", "Person enters. People count: " + peopleCount);

//        occupancyNumberView.setText("" + peopleCount);

        // Send data to cloud
        Log.d("CLOUD", "Sending TOF data to cloud");
        handler.post(sendPeopleCount);
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
            try {
                String subtopic = "events/pir";
                String messageJSON = new JSONObject()
                        .put("Timestamp", new Date().getTime())
                        .put("PIR1Activity", getActivityLevel(pir1ActivityData))
                        //.put("PIR2Activity", getActivityLevel(pir2ActivityData))
                        .put("PIR3Activity", getActivityLevel(pir3ActivityData))
                        .toString();
                communicator.publishMessage(subtopic, messageJSON);
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }

        }
    };

    private static final Runnable sendPeopleCount = new Runnable() {
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

    // PIR Activity
    private void updateHeatMap() {
        runOnUiThread(() -> {
            area1.setBackgroundColor(getActivityColor(getActivityLevel(pir1ActivityData)));
            area2.setBackgroundColor(getActivityColor(getActivityLevel(pir2ActivityData)));
            area3.setBackgroundColor(getActivityColor(getActivityLevel(pir3ActivityData)));

        });
    }

    private int getActivityLevel(Boolean[] activityData) {
        int activeMins = 0;
        for (Boolean b : activityData) {
            if (b) {
                activeMins++;
            }
        }
        return activeMins;
    }

    private int getActivityColor(int activityLevel) {
        int a = activityLevel/2;
        switch (a) {
            case 0: return ContextCompat.getColor(this, R.color.activity0);
            case 1: return ContextCompat.getColor(this, R.color.activity1);
            case 2: return ContextCompat.getColor(this, R.color.activity2);
            case 3: return ContextCompat.getColor(this, R.color.activity3);
            case 4: return ContextCompat.getColor(this, R.color.activity4);
            case 5: return ContextCompat.getColor(this, R.color.activity5);
            case 6: return ContextCompat.getColor(this, R.color.activity6);
            case 7: return ContextCompat.getColor(this, R.color.activity7);
            case 8: return ContextCompat.getColor(this, R.color.activity8);
            case 9: return ContextCompat.getColor(this, R.color.activity9);
        }
        return R.color.activity9;
    }

}