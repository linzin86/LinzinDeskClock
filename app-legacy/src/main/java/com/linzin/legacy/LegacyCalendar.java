package com.linzin.legacy;

import android.content.Context;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

final class LegacyCalendar {
    interface Query { Cursor run(Uri uri, String[] projection); }
    static String read(Context context, long now) {
        return read(context, now, (uri, columns) -> context.getContentResolver().query(uri, columns,
            null, null, CalendarContract.Instances.BEGIN + " ASC"));
    }
    static String read(Context context, long now, Query query) {
        Cursor cursor = null;
        try {
            if (context.checkCallingOrSelfPermission("android.permission.READ_CALENDAR") != PackageManager.PERMISSION_GRANTED)
                return context.getString(R.string.calendar_permission);
            if (Thread.currentThread().isInterrupted()) return "";
            Uri.Builder range = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(range, now);
            ContentUris.appendId(range, now + 7 * 86400000L);
            // API14 cannot cancel an in-flight Binder query; discard and close its late result.
            cursor = query.run(range.build(), new String[] { "title", "begin", "allDay" });
            if (Thread.currentThread().isInterrupted()) return "";
            if (cursor == null) return context.getString(R.string.calendar_unavailable);
            StringBuilder result = new StringBuilder();
            for (int count = 0; count < 3 && cursor.moveToNext(); count++) {
                if (Thread.currentThread().isInterrupted()) return "";
                String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                long begin = cursor.getLong(cursor.getColumnIndexOrThrow("begin"));
                boolean allDay = cursor.getInt(cursor.getColumnIndexOrThrow("allDay")) != 0;
                SimpleDateFormat format = new SimpleDateFormat(allDay ? "M/d" : "M/d HH:mm", context.getResources().getConfiguration().locale);
                if (allDay) format.setTimeZone(TimeZone.getTimeZone("UTC"));
                if (result.length() > 0) result.append('\n');
                result.append(format.format(new Date(begin))).append(" · ").append(title == null || title.length() == 0
                    ? context.getString(R.string.calendar_untitled) : title);
            }
            return result.length() == 0 ? context.getString(R.string.calendar_empty) : result.toString();
        } catch (RuntimeException error) {
            return context.getString(R.string.calendar_unavailable);
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}
