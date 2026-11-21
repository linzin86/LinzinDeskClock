package com.linzin.legacy;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.database.MatrixCursor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class CalendarChecks {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static MatrixCursor rows() {
        MatrixCursor cursor = new MatrixCursor(new String[] { "title", "begin", "end", "allDay" });
        cursor.addRow(new Object[] { "Calendar fixture", 1700000000000L, 1700003600000L, 0 });
        return cursor;
    }
    static void run(Context context) throws Exception {
        Context allowed = new ContextWrapper(context) {
            @Override public int checkCallingOrSelfPermission(String permission) { return PackageManager.PERMISSION_GRANTED; }
        };
        Context denied = new ContextWrapper(context) {
            @Override public int checkCallingOrSelfPermission(String permission) { return PackageManager.PERMISSION_DENIED; }
        };
        check(LegacyCalendar.read(denied, 1700000000000L, (uri, columns) -> {
            throw new AssertionError("Denied permission must skip provider");
        }).contains("permission"), "Denied permission must be explicit");
        MatrixCursor success = rows();
        check(LegacyCalendar.read(allowed, 1700000000000L, (uri, columns) -> success).contains("Calendar fixture"), "Event must be parsed");
        check(success.isClosed(), "Successful cursor must close");
        MatrixCursor malformed = new MatrixCursor(new String[] { "wrong" });
        malformed.addRow(new Object[] { "bad" });
        check(LegacyCalendar.read(allowed, 1700000000000L, (uri, columns) -> malformed).contains("unavailable"), "Malformed data must fail safely");
        check(malformed.isClosed(), "Malformed cursor must close");
        check(LegacyCalendar.read(allowed, 1700000000000L, (uri, columns) -> {
            throw new SecurityException("revoked");
        }).contains("unavailable"), "Revoked access must fail safely");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        MatrixCursor late = rows();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                String result = LegacyCalendar.read(allowed, 1700000000000L, (uri, columns) -> {
                    entered.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try { release.await(); break; }
                        catch (InterruptedException error) { interrupted = true; }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                    return late;
                });
                check(!result.contains("Calendar fixture"), "Cancelled query must discard late rows");
            } catch (Throwable error) { failure.set(error); }
        });
        worker.start();
        try {
            check(entered.await(2, TimeUnit.SECONDS), "Query must start");
            worker.interrupt();
        } finally { release.countDown(); worker.join(3000); }
        check(!worker.isAlive(), "Cancelled worker must finish after provider returns");
        if (failure.get() != null) throw new RuntimeException("Cancellation check failed", failure.get());
        check(late.isClosed(), "Cancelled query must close the late cursor");
    }
}
