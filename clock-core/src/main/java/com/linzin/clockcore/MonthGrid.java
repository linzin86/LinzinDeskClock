package com.linzin.clockcore;

import java.util.TimeZone;
import java.util.Calendar;

public final class MonthGrid {
    public final int year, month, day, firstDayOffset, daysInMonth, cellCount;
    private MonthGrid(Calendar calendar) {
        year = calendar.get(Calendar.YEAR); month = calendar.get(Calendar.MONTH) + 1; day = calendar.get(Calendar.DAY_OF_MONTH);
        daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        calendar.set(Calendar.DAY_OF_MONTH, 1); firstDayOffset = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        cellCount = (firstDayOffset + daysInMonth + 6) / 7 * 7;
    }
    public static MonthGrid at(long instant, TimeZone zone) {
        Calendar calendar = Calendar.getInstance(zone); calendar.setTimeInMillis(instant);
        return new MonthGrid(calendar);
    }
}
