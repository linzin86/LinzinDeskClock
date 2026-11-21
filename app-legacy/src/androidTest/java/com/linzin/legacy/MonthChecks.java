package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.widget.TextView;
import java.util.TimeZone;

final class MonthChecks {
    static void run(Instrumentation test, Activity activity) {
        LegacyMonthView month = activity.getWindow().getDecorView().findViewWithTag("month_calendar");
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            test.runOnMainSync(() -> { month.stop(); month.showTime(1709208000000L); });
            if (!title(month).contains("February 2024") || month.findViewWithTag("day_30") != null)
                throw new AssertionError("Leap February must contain exactly 29 dates");
            TextView leapDay = month.findViewWithTag("day_29");
            if (leapDay == null || leapDay.getBackground() == null || !leapDay.getContentDescription().toString().contains("Thursday"))
                throw new AssertionError("Today needs a visible highlight and full accessible date");
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            test.runOnMainSync(() -> month.showTime(1709253000000L));
            if (!title(month).contains("February")) throw new AssertionError("West-zone month boundary");
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            test.runOnMainSync(() -> month.showTime(1709253000000L));
            if (!title(month).contains("March") || month.findViewWithTag("day_31") == null)
                throw new AssertionError("Time-zone change must rebuild all dates");
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            test.runOnMainSync(() -> month.showTime(1767225600000L));
            if (!title(month).contains("January 2026")) throw new AssertionError("Year rollover must rebuild the title");
        } finally {
            TimeZone.setDefault(original);
            test.runOnMainSync(() -> { month.showTime(System.currentTimeMillis()); month.start(); });
        }
    }
    private static String title(LegacyMonthView month) { return ((TextView) month.findViewWithTag("month_title")).getText().toString(); }
}
