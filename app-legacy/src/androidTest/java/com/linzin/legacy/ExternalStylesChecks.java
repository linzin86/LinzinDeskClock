package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.Button;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

final class ExternalStylesChecks {
    static void run(Instrumentation test, Activity activity) {
        Button selector = activity.getWindow().getDecorView().findViewWithTag("clock_style");
        View clock = activity.getWindow().getDecorView().findViewWithTag("clock");
        if (selector == null || clock == null) throw new AssertionError("Clock/style UI must exist");
        String[] names = { "TK", "TTN", "RAFM", "ABW", "OET", "ZMG", "PP", "FFT" };
        Set<Integer> images = new HashSet<>();
        for (int i = 0; i < names.length; i++) {
            String[] text = new String[1], readout = new String[1];
            int[] hash = new int[1];
            AtomicReference<Throwable> error = new AtomicReference<>();
            test.runOnMainSync(() -> {
                try {
                    clock.performClick();
                    text[0] = selector.getText().toString(); readout[0] = clock.getContentDescription().toString();
                    Bitmap image = Bitmap.createBitmap(clock.getWidth(), clock.getHeight(), Bitmap.Config.ARGB_8888);
                    try {
                        clock.draw(new Canvas(image));
                        int[] pixels = new int[image.getWidth() * image.getHeight()];
                        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
                        hash[0] = Arrays.hashCode(pixels);
                    } finally { image.recycle(); }
                } catch (Throwable failure) { error.set(failure); }
            });
            if (error.get() != null) throw new RuntimeException("Public clock draw failed", error.get());
            if (!text[0].contains(names[i])) throw new AssertionError("Expected style " + names[i] + ", got " + text[0]);
            SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.US);
            long now = System.currentTimeMillis();
            if (!readout[0].contains(format.format(new Date(now))) && !readout[0].contains(format.format(new Date(now - 60000))))
                throw new AssertionError("Clock must expose wall time: " + readout[0]);
            images.add(hash[0]);
            if (i < names.length - 1) { test.runOnMainSync(() -> selector.performClick()); test.waitForIdleSync(); }
        }
        if (images.size() != 8) throw new AssertionError("All eight public styles must render distinctly");
    }
}
