package com.specknet.orientandroid;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TOFSensor {

    private int id;
    private int tofTriggered;  // value of latest reading

    // Variables for counting a person passing
    private List<Integer> mUnderX;
    private List<Integer> nOverY;

    // Thresholds
    int X = 500;
    int Y = 800;
    int m = 2;
    int n = 3;

    // People counting flags
    AtomicBoolean underX = new AtomicBoolean(false);
    AtomicBoolean overY = new AtomicBoolean(false);

    public TOFSensor(int id) {
        this.id = id;

        this.mUnderX = new ArrayList<>();
        this.nOverY = new ArrayList<>();
    }

    public void setTofTriggered(int t) {
        this.tofTriggered = t;
        processReading(tofTriggered);
    }
    public int getTofTriggered() {
        return tofTriggered;
    }

    public void processReading(int reading) {
        Log.d("TOF_READING_"+id, "" + reading);
        if (underX.get()) {

            if (overY.get()) {

                // Looking for readings over Y, smaller reading found
                if ((reading < Y)) {
                    // person not counted
                    // reset
                    resetFiveList();
                    resetThreeList();
                    overY.set(false);
                    underX.set(false);
                }

                // Looking for readings over Y, reading found
                if ((reading > Y) && (getThreeSize() < n)) {
                    addThreeReading();
                }

                // Just need one more reading over Y
                if ((reading > Y) && (getThreeSize() >= n)) {

                    // Find direction
                    Log.d("TOF_READING_"+id, "Person seen!");
                    MainActivity.board_T_TOF.personPassed(id);

                    // reset
                    resetFiveList();
                    resetThreeList();
                    overY.set(false);
                    underX.set(false);
                }
            } else {

                // Adding readings under X
                if ((getTofTriggered() < X) && (getFiveSize() < m-1)) {
                    addFiveReading();
                }

                // Looking for readings under X but larger reading seen -> reset
                if ((getTofTriggered() > Y) && (getFiveSize() < m-1)) {
                    underX.set(false);
                    resetFiveList();
                }

                // After m readings under X reached, reading over Y seen
                if ((getFiveSize() >= m-1) && (getTofTriggered() > Y)) {
                    // switch to counting threes
                    overY.set(true);
                    addThreeReading();
                }
            }

        } else {
            // initial
            if ((getTofTriggered() < X) && (getFiveSize() == 0)) {
                underX.set(true);
                addFiveReading();
            }
        }
    }

    // Helper methods for calculating if a person passes
    public void resetFiveList() {
        this.mUnderX = new ArrayList<>();
    }
    public void addFiveReading() {
        mUnderX.add(tofTriggered);
    }
    public int getFiveSize() {
        return mUnderX.size();
    }
    public void resetThreeList() {
        this.nOverY = new ArrayList<>();
    }
    public void addThreeReading() {
        nOverY.add(tofTriggered);
    }
    public int getThreeSize() {
        return nOverY.size();
    }
}
