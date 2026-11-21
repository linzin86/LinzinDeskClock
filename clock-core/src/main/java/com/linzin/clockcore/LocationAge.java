package com.linzin.clockcore;

public final class LocationAge {
    private LocationAge() { }
    public static Long knownFixTime(Long fixTimeMillis, Long observedAtMillis, long nowMillis) {
        return fixTimeMillis != null && fixTimeMillis > 0 && fixTimeMillis <= nowMillis
            && (observedAtMillis == null || observedAtMillis >= fixTimeMillis && observedAtMillis <= nowMillis)
            ? fixTimeMillis : null;
    }
}
