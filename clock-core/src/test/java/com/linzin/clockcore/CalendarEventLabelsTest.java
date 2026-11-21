package com.linzin.clockcore;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static com.linzin.clockcore.CalendarEventLabels.Kind.*;

public class CalendarEventLabelsTest {
    private final TimeZone zone = TimeZone.getTimeZone("Asia/Taipei");
    private final long now = utc("2026-02-28 04:00");
    @Test public void endedTimedEventsAreHidden() {
        assertEquals(HIDDEN, CalendarEventLabels.classify(now - 3600000, now, false, now, zone));
    }
    @Test public void todayAndTomorrowKeepDistinctTimedLabels() {
        assertEquals(TODAY_TIME, CalendarEventLabels.classify(now + 3600000, now + 7200000, false, now, zone));
        assertEquals(FUTURE_TIME, CalendarEventLabels.classify(now + 86400000, now + 90000000, false, now, zone));
    }
    @Test public void multiDayAllDayEventsCoveringTodayKeepTheModernLabel() {
        assertEquals(TODAY_ALL_DAY, CalendarEventLabels.classify(utc("2026-02-26 00:00"), utc("2026-03-01 00:00"), true, now, zone));
    }
    @Test public void allDayEndIsExclusiveAtTheLocalDatesUtcMidnight() {
        assertEquals(HIDDEN, CalendarEventLabels.classify(utc("2026-02-26 00:00"), utc("2026-02-28 00:00"), true, now, zone));
    }
    @Test public void futureAllDayEventsKeepTheirUtcDate() {
        assertEquals(FUTURE_ALL_DAY, CalendarEventLabels.classify(utc("2026-03-01 00:00"), utc("2026-03-02 00:00"), true, now, zone));
    }
    @Test public void ongoingTimedEventsFromYesterdayKeepTheExistingTodayLabel() {
        assertEquals(TODAY_ALL_DAY, CalendarEventLabels.classify(utc("2026-02-27 15:00"), now + 3600000, false, now, zone));
    }
    @Test public void theDayOfYearDoesNotConfuseDifferentYears() {
        assertEquals(FUTURE_TIME, CalendarEventLabels.classify(utc("2027-02-28 04:00"), utc("2027-02-28 05:00"), false, now, zone));
    }
    private static long utc(String text) {
        try {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            parser.setTimeZone(TimeZone.getTimeZone("UTC")); return parser.parse(text).getTime();
        } catch (java.text.ParseException invalid) { throw new AssertionError(invalid); }
    }
}
