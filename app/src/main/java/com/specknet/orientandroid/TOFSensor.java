package com.specknet.orientandroid;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TOFSensor {

    private int id;
    private int tofTriggered;  // value of latest reading

    // Variables for counting a person passing
    private List<Integer> fiveUnder500;
    private List<Integer> threeOver100;

    // People counting flags
    AtomicBoolean under500 = new AtomicBoolean(false);
    AtomicBoolean over1000 = new AtomicBoolean(false);

    public TOFSensor(int id) {
        this.id = id;

        this.fiveUnder500 = new ArrayList<>();
        this.threeOver100 = new ArrayList<>();
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
        if (under500.get()) {

            if (over1000.get()) {

                // Looking for readings over 1000, smaller reading found
                if ((reading < 1000)) {
                    // person not counted
                    // reset
                    resetFiveList();
                    resetThreeList();
                    over1000.set(false);
                    under500.set(false);
                }

                // Looking for readings over 1000, reading found
                if ((reading > 1000) && (getThreeSize() < 3)) {
                    addThreeReading();
                }

                // Just need one more reading over 1000
                if ((reading > 1000) && (getThreeSize() >= 3)) {

                    // Find direction
                    MainActivity.board_T_TOF.personPassed(id);

                    // reset
                    resetFiveList();
                    resetThreeList();
                    over1000.set(false);
                    under500.set(false);
                }
            } else {

                // Adding readings under 500
                if ((getTofTriggered() < 500) && (getFiveSize() < 4)) {
                    addFiveReading();
                }

                // Looking for readings under 500 but larger reading seen -> reset
                if ((getTofTriggered() > 1000) && (getFiveSize() < 4)) {
                    under500.set(false);
                    resetFiveList();
                }

                // After 5 readings under 500 reached, reading over 1000 seen
                if ((getFiveSize() >= 4) && (getTofTriggered() > 1000)) {
                    // switch to counting threes
                    over1000.set(true);
                    addThreeReading();
                }
            }

        } else {
            // initial
            if ((getTofTriggered() < 500) && (getFiveSize() == 0)) {
                under500.set(true);
                addFiveReading();
            }
        }
    }

    // Helper methods for calculating if a person passes
    public void resetFiveList() {
        this.fiveUnder500 = new ArrayList<>();
    }
    public void addFiveReading() {
        fiveUnder500.add(tofTriggered);
    }
    public int getFiveSize() {
        return fiveUnder500.size();
    }
    public void resetThreeList() {
        this.threeOver100 = new ArrayList<>();
    }
    public void addThreeReading() {
        threeOver100.add(tofTriggered);
    }
    public int getThreeSize() {
        return threeOver100.size();
    }
}
