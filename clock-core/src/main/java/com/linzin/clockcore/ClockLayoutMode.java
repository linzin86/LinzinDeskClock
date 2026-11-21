package com.linzin.clockcore;

public enum ClockLayoutMode {
    SINGLE, DUAL_LEFT_LARGE, DUAL_RIGHT_LARGE;
    public static ClockLayoutMode fromPersisted(String value) {
        if (value != null) {
            try { return valueOf(value); }
            catch (IllegalArgumentException ignored) { }
        }
        return SINGLE;
    }
}
