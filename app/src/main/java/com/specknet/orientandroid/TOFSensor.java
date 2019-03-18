package com.specknet.orientandroid;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TOFSensor {

    private int id;
    private int tofTriggered;  // value of latest reading

    // Variables for counting a person passing
    private List<Integer> fiveUnder400;
    private List<Integer> threeOver800;

    // People counting flags
    AtomicBoolean under400 = new AtomicBoolean(false);
    AtomicBoolean over800 = new AtomicBoolean(false);

    public TOFSensor(int id) {
        this.id = id;

        this.fiveUnder400 = new ArrayList<>();
        this.threeOver800 = new ArrayList<>();
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
        if (under400.get()) {

            if (over800.get()) {

                // Looking for readings over 800, smaller reading found
                if ((reading < 800)) {
                    // person not counted
                    // reset
                    resetFiveList();
                    resetThreeList();
                    over800.set(false);
                    under400.set(false);
                }

                // Looking for readings over 800, reading found
                if ((reading > 800) && (getThreeSize() < 3)) {
                    addThreeReading();
                }

                // Just need one more reading over 800
                if ((reading > 800) && (getThreeSize() >= 3)) {

                    // Find direction
                    MainActivity.board_T_TOF.personPassed(id);

                    // reset
                    resetFiveList();
                    resetThreeList();
                    over800.set(false);
                    under400.set(false);
                }
            } else {

                // Adding readings under 400
                if ((getTofTriggered() < 400) && (getFiveSize() < 4)) {
                    addFiveReading();
                }

                // Looking for readings under 500 but larger reading seen -> reset
                if ((getTofTriggered() > 800) && (getFiveSize() < 4)) {
                    under400.set(false);
                    resetFiveList();
                }

                // After 5 readings under 400 reached, reading over 800 seen
                if ((getFiveSize() >= 4) && (getTofTriggered() > 800)) {
                    // switch to counting threes
                    over800.set(true);
                    addThreeReading();
                }
            }

        } else {
            // initial
            if ((getTofTriggered() < 400) && (getFiveSize() == 0)) {
                under400.set(true);
                addFiveReading();
            }
        }
    }

    // Helper methods for calculating if a person passes
    public void resetFiveList() {
        this.fiveUnder400 = new ArrayList<>();
    }
    public void addFiveReading() {
        fiveUnder400.add(tofTriggered);
    }
    public int getFiveSize() {
        return fiveUnder400.size();
    }
    public void resetThreeList() {
        this.threeOver800 = new ArrayList<>();
    }
    public void addThreeReading() {
        threeOver800.add(tofTriggered);
    }
    public int getThreeSize() {
        return threeOver800.size();
    }
}
