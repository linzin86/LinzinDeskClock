package com.linzin.clockcore;

import org.junit.Test;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ForecastTimelineTest {
    @Test public void sortsAndKeepsOnlyFourCurrentOrUpcomingRows() {
        assertArrayEquals(new int[] { 3, 2, 0, 4 }, ForecastTimeline.upcomingIndices(
            new long[] { 10800000, 0, 7200000, 3600000, 14400000, 18000000 }, 5400000, "UTC"));
    }
    @Test public void fractionalOffsetStartsAtTheCurrentLocalHour() throws Exception {
        long start = utc("2026-11-16 16:15");
        assertArrayEquals(new int[] { 1, 2, 3, 4 }, ForecastTimeline.upcomingIndices(
            hours(start, 6), utc("2026-11-16 18:10"), "Asia/Kathmandu"));
    }
    @Test public void firstRepeatedHourKeepsItsFirstOccurrence() throws Exception {
        assertArrayEquals(new int[] { 1, 2, 3, 4 }, ForecastTimeline.upcomingIndices(
            hours(utc("2026-11-01 04:00"), 6), utc("2026-11-01 05:10"), "America/New_York"));
    }
    @Test public void halfHourGapExcludesThePreviousLocalHour() throws Exception {
        assertArrayEquals(new int[] { 1, 2, 3, 4 }, ForecastTimeline.upcomingIndices(
            hours(utc("2026-10-03 15:00"), 5), utc("2026-10-03 15:40"), "Australia/Lord_Howe"));
    }
    @Test public void halfHourRepeatStartsAtTheTransition() throws Exception {
        assertArrayEquals(new int[] { 1, 2, 3, 4 }, ForecastTimeline.upcomingIndices(new long[] {
            utc("2026-04-04 14:30"), utc("2026-04-04 15:00"), utc("2026-04-04 16:00"),
            utc("2026-04-04 17:00"), utc("2026-04-04 18:00") }, utc("2026-04-04 15:10"), "Australia/Lord_Howe"));
    }
    @Test public void recognizesKyivOnTheBundledTimeZoneDatabase() throws Exception {
        assertEquals(15, ForecastTimeline.hour(utc("2026-02-28 13:05"), "Europe/Kyiv"));
    }
    @Test(expected = RuntimeException.class) public void unknownZoneDoesNotSilentlyBecomeGmt() {
        ForecastTimeline.hour(0, "Invalid/Zone");
    }
    private static long[] hours(long start, int count) {
        long[] values = new long[count];
        for (int i = 0; i < count; i++) values[i] = start + i * 3600000L;
        return values;
    }
    private static long utc(String text) throws Exception {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        return parser.parse(text).getTime();
    }
}
