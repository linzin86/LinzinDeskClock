package com.linzin.clockcore;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ClockAnglesTest {
    @Test public void allHandsPointUpAtTwelve() {
        ClockAngles angles = ClockAngles.at(0, 0, 0);
        assertEquals(-90f, angles.hourDegrees, 0.0001f);
        assertEquals(-90f, angles.minuteDegrees, 0.0001f);
        assertEquals(-90f, angles.secondDegrees, 0.0001f);
    }

    @Test public void hourAndMinuteHandsIncludeElapsedMinutesAndSeconds() {
        ClockAngles angles = ClockAngles.at(3, 15, 30);
        assertEquals(7.75f, angles.hourDegrees, 0.0001f);
        assertEquals(3f, angles.minuteDegrees, 0.0001f);
        assertEquals(90f, angles.secondDegrees, 0.0001f);
    }
}
