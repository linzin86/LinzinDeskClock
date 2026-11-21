package com.linzin.clockcore;

import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class MonthGridTest {
    @Test public void leapFebruaryIncludesAllDaysWithSundayFirstRows() {
        MonthGrid month = MonthGrid.at(1709208000000L, TimeZone.getTimeZone("UTC"));
        assertEquals(2024, month.year); assertEquals(2, month.month); assertEquals(29, month.day);
        assertEquals(29, month.daysInMonth); assertEquals(4, month.firstDayOffset); assertEquals(35, month.cellCount);
    }
    @Test public void sameInstantUsesTheRequestedCalendarTimeZone() {
        MonthGrid west = MonthGrid.at(1709253000000L, TimeZone.getTimeZone("Pacific/Honolulu"));
        MonthGrid east = MonthGrid.at(1709253000000L, TimeZone.getTimeZone("Pacific/Kiritimati"));
        assertEquals(2, west.month); assertEquals(29, west.day); assertEquals(29, west.daysInMonth);
        assertEquals(3, east.month); assertEquals(1, east.day); assertEquals(31, east.daysInMonth);
        assertEquals(5, east.firstDayOffset); assertEquals(42, east.cellCount);
    }
    @Test public void yearRolloverRebuildsTheMonth() {
        MonthGrid before = MonthGrid.at(1767225599000L, TimeZone.getTimeZone("UTC"));
        MonthGrid after = MonthGrid.at(1767225600000L, TimeZone.getTimeZone("UTC"));
        assertEquals(2025, before.year); assertEquals(12, before.month); assertEquals(31, before.day);
        assertEquals(2026, after.year); assertEquals(1, after.month); assertEquals(1, after.day);
        assertEquals(4, after.firstDayOffset); assertEquals(35, after.cellCount);
    }
}
