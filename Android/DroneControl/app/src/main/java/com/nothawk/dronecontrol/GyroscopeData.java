package com.nothawk.dronecontrol;

/**
 * Created by Sidharth Bambah on 3/4/2018.
 */

public class GyroscopeData {
    public float pitch;
    public float roll;

    public GyroscopeData(float x, float y) {
        pitch = x;
        roll = y;
    }
}
