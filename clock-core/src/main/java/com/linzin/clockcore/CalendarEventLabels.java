package com.linzin.clockcore;

import java.util.TimeZone;
import java.util.Calendar;

public final class CalendarEventLabels {
    public enum Kind { HIDDEN, TODAY_ALL_DAY, FUTURE_ALL_DAY, TODAY_TIME, FUTURE_TIME }
    private CalendarEventLabels() { }
    public static Kind classify(long begin, long end, boolean allDay, long now, TimeZone zone) {
        Calendar current = Calendar.getInstance(zone); current.setTimeInMillis(now);
        int year = current.get(Calendar.YEAR), month = current.get(Calendar.MONTH), day = current.get(Calendar.DAY_OF_MONTH);
        if (allDay) {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.clear(); utc.set(year, month, day, 0, 0, 0);
            long today = utc.getTimeInMillis();
            if (end <= today) return Kind.HIDDEN;
            return begin <= today ? Kind.TODAY_ALL_DAY : Kind.FUTURE_ALL_DAY;
        }
        if (end <= now) return Kind.HIDDEN;
        Calendar midnight = Calendar.getInstance(zone); midnight.clear(); midnight.set(year, month, day, 0, 0, 0);
        if (begin < midnight.getTimeInMillis()) return Kind.TODAY_ALL_DAY;
        Calendar event = Calendar.getInstance(zone); event.setTimeInMillis(begin);
        return event.get(Calendar.YEAR) == year && event.get(Calendar.DAY_OF_YEAR) == current.get(Calendar.DAY_OF_YEAR)
            ? Kind.TODAY_TIME : Kind.FUTURE_TIME;
    }
}
