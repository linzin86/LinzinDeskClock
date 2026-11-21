package com.linzin.clockcore;

public final class ClockAngles {
    public final float hourDegrees;
    public final float minuteDegrees;
    public final float secondDegrees;

    private ClockAngles(float hour, float minute, float second) {
        hourDegrees = hour;
        minuteDegrees = minute;
        secondDegrees = second;
    }

    public static ClockAngles at(int hour, int minute, int second) {
        return new ClockAngles(
            (hour + minute / 60f + second / 3600f) * 30f - 90f,
            (minute + second / 60f) * 6f - 90f,
            second * 6f - 90f
        );
    }
}
