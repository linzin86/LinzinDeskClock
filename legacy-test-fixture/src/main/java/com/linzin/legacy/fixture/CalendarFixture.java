package com.linzin.legacy.fixture;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import java.util.TimeZone;

public class CalendarFixture extends Instrumentation {
    private static final String ACCOUNT = "linzin-legacy-isolated-fixture";
    private String mode;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments == null ? "" : arguments.getString("mode", "");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_OK;
        try {
            ContentResolver resolver = getTargetContext().getContentResolver();
            SharedPreferences prefs = getTargetContext().getSharedPreferences("fixture", Context.MODE_PRIVATE);
            if (!"create".equals(mode) && !"cleanup".equals(mode)) throw new IllegalArgumentException("Use create or cleanup");
            cleanup(resolver, prefs);
            if ("create".equals(mode)) {
                ContentValues calendar = new ContentValues();
                calendar.put("account_name", ACCOUNT);
                calendar.put("account_type", CalendarContract.ACCOUNT_TYPE_LOCAL);
                calendar.put("name", ACCOUNT);
                calendar.put("calendar_displayName", "Linzin Legacy fixture");
                calendar.put("calendar_color", 0xff44bbff);
                calendar.put("calendar_access_level", CalendarContract.Calendars.CAL_ACCESS_OWNER);
                calendar.put("ownerAccount", ACCOUNT);
                calendar.put("visible", 1);
                calendar.put("sync_events", 1);
                calendar.put("calendar_timezone", TimeZone.getDefault().getID());
                Uri created = resolver.insert(sync(CalendarContract.Calendars.CONTENT_URI), calendar);
                if (created == null) throw new IllegalStateException("Calendar insert failed");
                long id = ContentUris.parseId(created);
                if (!prefs.edit().putLong("calendar", id).commit()) throw new IllegalStateException("Cannot retain cleanup identity");
                try {
                    ContentValues event = new ContentValues();
                    event.put("calendar_id", id);
                    event.put("title", "Linzin Legacy fixture");
                    long now = System.currentTimeMillis();
                    event.put("dtstart", now + 60000);
                    event.put("dtend", now + 3600000);
                    event.put("eventTimezone", TimeZone.getDefault().getID());
                    Uri inserted = resolver.insert(CalendarContract.Events.CONTENT_URI, event);
                    if (inserted == null) throw new IllegalStateException("Event insert failed");
                    result.putString("stream", "OK: fixture calendar=" + id + " event=" + ContentUris.parseId(inserted));
                } catch (Throwable error) { cleanup(resolver, prefs); throw error; }
            } else result.putString("stream", "OK: owned calendar and events removed");
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error); code = Activity.RESULT_CANCELED;
        }
        finish(code, result);
    }
    private static Uri sync(Uri uri) {
        return uri.buildUpon().appendQueryParameter("caller_is_syncadapter", "true")
            .appendQueryParameter("account_name", ACCOUNT)
            .appendQueryParameter("account_type", CalendarContract.ACCOUNT_TYPE_LOCAL).build();
    }
    private static void cleanup(ContentResolver resolver, SharedPreferences prefs) {
        long id = prefs.getLong("calendar", -1);
        if (id < 0) return;
        Uri uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id);
        Cursor cursor = resolver.query(uri, new String[] { "account_name", "account_type", "name" }, null, null, null);
        try {
            if (cursor == null) throw new IllegalStateException("Cannot verify fixture ownership");
            if (cursor.moveToFirst()) {
                if (!ACCOUNT.equals(cursor.getString(0)) || !CalendarContract.ACCOUNT_TYPE_LOCAL.equals(cursor.getString(1))
                    || !ACCOUNT.equals(cursor.getString(2))) throw new SecurityException("Refuse to remove unowned calendar");
                if (resolver.delete(sync(uri), null, null) != 1) throw new IllegalStateException("Cleanup failed");
            }
        } finally { if (cursor != null) cursor.close(); }
        Cursor remaining = resolver.query(uri, new String[] { "_id" }, null, null, null);
        try {
            if (remaining == null || remaining.moveToFirst()) throw new IllegalStateException("Calendar still exists");
        } finally { if (remaining != null) remaining.close(); }
        prefs.edit().remove("calendar").commit();
    }
}
