package com.linzin.clockcore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.threeten.bp.Instant;
import org.threeten.bp.ZoneId;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.zone.ZoneOffsetTransition;

public final class ForecastTimeline {
    private ForecastTimeline() { }
    public static int hour(long timestampMillis, String timeZoneId) {
        return Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.of(timeZoneId)).getHour();
    }
    public static int[] upcomingIndices(long[] timestamps, long nowMillis, String timeZoneId) {
        ZoneId zone = ZoneId.of(timeZoneId);
        Instant instant = Instant.ofEpochMilli(nowMillis);
        ZonedDateTime current = instant.atZone(zone);
        long floor = current.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
        ZoneOffsetTransition transition = zone.getRules().previousTransition(instant.plusNanos(1));
        // Partial DST hours begin at the transition, including repeated half-hour segments.
        long start = Math.max(floor, transition == null ? Long.MIN_VALUE : transition.getInstant().toEpochMilli());
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < timestamps.length; i++) if (timestamps[i] >= start) indices.add(i);
        Collections.sort(indices, (left, right) -> timestamps[left] < timestamps[right] ? -1
            : timestamps[left] == timestamps[right] ? 0 : 1);
        int[] result = new int[Math.min(4, indices.size())];
        for (int i = 0; i < result.length; i++) result[i] = indices.get(i);
        return result;
    }
}
