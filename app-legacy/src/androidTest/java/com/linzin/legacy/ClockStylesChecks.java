package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.view.View;
import android.widget.Button;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashSet;
import java.util.Set;

final class ClockStylesChecks {
    static void run(Instrumentation test, Activity activity) throws Exception {
        Button[] selector = new Button[1];
        test.runOnMainSync(() -> selector[0] = activity.getWindow().getDecorView().findViewWithTag("clock_style"));
        if (selector[0] == null) throw new AssertionError("All eight clock styles must be selectable");
        String[] expected = { "TK", "TTN", "RAFM", "ABW", "OET", "ZMG", "PP", "FFT" };
        Set<String> seen = new HashSet<>();
        Set<Integer> images = new HashSet<>();
        Bitmap grid24 = Bitmap.createBitmap(640, 960, Bitmap.Config.ARGB_8888);
        Bitmap grid12 = Bitmap.createBitmap(640, 960, Bitmap.Config.ARGB_8888);
        TimeZone originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
        for (int i = 0; i < expected.length; i++) {
            String[] label = new String[1];
            test.runOnMainSync(() -> label[0] = selector[0].getText().toString());
            if (!label[0].contains(expected[i])) throw new AssertionError("Expected " + expected[i] + ", got " + label[0]);
            if (!selector[0].getContentDescription().toString().contains(expected[i]))
                throw new AssertionError("Accessible style control must expose its current style");
            seen.add(label[0]);
            View[] clock = new View[1];
            test.runOnMainSync(() -> clock[0] = activity.getWindow().getDecorView().findViewWithTag("clock"));
            if (clock[0] == null || clock[0].getWidth() == 0) throw new AssertionError("Selected clock must draw");
            capture(test, (LegacyClockView) clock[0], grid24, i, true);
            capture(test, (LegacyClockView) clock[0], grid12, i, false);
            int[] pixels = new int[320 * 240];
            grid24.getPixels(pixels, 0, 320, i % 2 * 320, i / 2 * 240, 320, 240);
            images.add(Arrays.hashCode(pixels));
            if (i + 1 < expected.length) {
                test.runOnMainSync(() -> selector[0].performClick());
                test.waitForIdleSync();
            }
        }
        if (seen.size() != 8) throw new AssertionError("Eight distinct styles are required");
        if (images.size() != 8) throw new AssertionError("All eight rendered styles must be distinct");
        for (Bitmap grid : new Bitmap[] { grid24, grid12 }) {
            File file = new File(activity.getFilesDir(), grid == grid24 ? "clock-fixture24-grid.png" : "clock-fixture12-grid.png");
            try (FileOutputStream output = new FileOutputStream(file)) { grid.compress(Bitmap.CompressFormat.PNG, 100, output); }
        }
        } finally { TimeZone.setDefault(originalZone); grid24.recycle(); grid12.recycle(); }
    }

    private static void capture(Instrumentation test, LegacyClockView clock, Bitmap grid, int index, boolean is24) {
        String[] description = new String[1];
        AtomicReference<Throwable> failure = new AtomicReference<>();
        test.runOnMainSync(() -> {
            try {
                android.content.SharedPreferences prefs = clock.getContext().getSharedPreferences("desk_clock_prefs", 0);
                ClockAppearance style = ClockAppearance.read(prefs);
                prefs.edit().putBoolean("pref_" + style.familyKey() + "_show_sec", true).commit();
                clock.reloadSettings();
                clock.start(); clock.stop(); clock.layout(0, 0, 320, 240);
                clock.set24Hour(is24); clock.updateTime(1772283909000L);
                description[0] = clock.getContentDescription().toString();
                Canvas canvas = new Canvas(grid);
                canvas.save(); canvas.translate(index % 2 * 320, index / 2 * 240); canvas.clipRect(0, 0, 320, 240);
                clock.draw(canvas); canvas.restore();
                prefs.edit().putBoolean("pref_" + style.familyKey() + "_show_sec", false).commit();
                clock.reloadSettings(); clock.updateTime(1772283909000L);
                if (clock.getContentDescription().toString().contains(":09")) throw new AssertionError("Seconds must hide");
                prefs.edit().putBoolean("pref_" + style.familyKey() + "_show_sec", true).commit();
                clock.reloadSettings(); clock.set24Hour(true);
                TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"));
                clock.updateTime(1772283909000L);
                if (!clock.getContentDescription().toString().contains("21:05:09")) throw new AssertionError("Timezone must update");
                TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                clock.updateTime(1769903999000L);
                if (!clock.getContentDescription().toString().contains("23:59:59")) throw new AssertionError("Month end fixture");
                clock.updateTime(1769904000000L);
                if (!clock.getContentDescription().toString().contains("00:00:00")) throw new AssertionError("Month rollover fixture");
            } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new RuntimeException("Clock fixture failed", failure.get());
        String expected = is24 ? "13:05:09" : "01:05:09 PM";
        if (!description[0].contains(expected)) throw new AssertionError("Fixture expected " + expected + ", got " + description[0]);
    }
}
