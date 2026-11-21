package com.linzin.legacy;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

final class CalendarPeriodChecks {
    static void run(Context context, boolean rangeOnly) {
        Context allowed = new ContextWrapper(context) {
            @Override public int checkCallingOrSelfPermission(String permission) { return PackageManager.PERMISSION_GRANTED; }
        };
        Calendar local = Calendar.getInstance(); local.clear(); local.set(2026, Calendar.FEBRUARY, 28, 12, 0, 0);
        long now = local.getTimeInMillis(); local.set(Calendar.HOUR_OF_DAY, 0); long localStart = local.getTimeInMillis();
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")); utc.clear(); utc.set(2026, Calendar.FEBRUARY, 28);
        long utcStart = utc.getTimeInMillis();
        MatrixCursor rows = new MatrixCursor(new String[] { "title", "begin", "end", "allDay" });
        if (!rangeOnly) {
            rows.addRow(new Object[] { "Multiday", utcStart - 2 * 86400000L, utcStart + 86400000L, 1 });
            rows.addRow(new Object[] { "Ongoing", localStart - 3600000, now + 3600000, 0 });
            rows.addRow(new Object[] { "Ended", now - 7200000, now - 3600000, 0 });
            rows.addRow(new Object[] { "Next", now + 3600000, now + 7200000, 0 });
            rows.addRow(new Object[] { "Fourth", now + 10800000, now + 14400000, 0 });
        }
        String result = LegacyCalendar.read(allowed, now, (uri, columns) -> {
            if (rangeOnly) {
                List<String> parts = uri.getPathSegments();
                long end = Long.parseLong(parts.get(parts.size() - 1));
                if (end != now + 172800000L) throw new AssertionError("Upcoming provider range must match the modern 48-hour contract; delta=" + (end - now));
                if (!Arrays.asList(columns).contains("end")) throw new AssertionError("Event end is required for ongoing/exclusive-end rules");
            }
            return rows;
        });
        if (!rows.isClosed()) throw new AssertionError("Calendar content checks must close their cursor");
        if (rangeOnly) return;
        if (!result.contains("Multiday") || !result.contains("Ongoing") || !result.contains("Next")
            || result.contains("Ended") || result.contains("Fourth"))
            throw new AssertionError("The three event slots must exclude ended rows and retain ongoing/all-day content: " + result);
        int label = context.getResources().getIdentifier("event_all_day_today", "string", context.getPackageName());
        if (label == 0 || !result.contains(context.getString(label))) throw new AssertionError("Ongoing/all-day labels must preserve the modern content rules");
    }
}
