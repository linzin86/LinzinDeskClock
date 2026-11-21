package com.linzin.legacy;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import com.linzin.clockcore.ClockAngles;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

final class AnalogRenderingChecks {
    static void run(Context context) throws Exception {
        cacheFailure();
        float density = context.getResources().getDisplayMetrics().density;
        AnalogClockFace reused = new AnalogClockFace(new ClockArtwork(context), density);
        int[][] sizes = {{320, 240}, {240, 320}, {320, 240}, {96, 96}, {320, 240},
            {1024, 1024}, {1025, 1024}, {320, 240}};
        ClockAppearance[] styles = {ClockAppearance.ABW, ClockAppearance.OET, ClockAppearance.ZMG,
            ClockAppearance.OET, ClockAppearance.ABW};
        File folder = new File(context.getFilesDir(), "analog-fixtures");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Cannot save analog fixtures");
        for (int s = 0; s < sizes.length; s++) {
            for (int i = 0; i < styles.length; i++) {
                int width = sizes[s][0], height = sizes[s][1];
                ClockAngles angles = ClockAngles.at(1 + i, 5 + s, i == 0 ? 59 : 0);
                boolean seconds = (s + i) % 2 == 0;
                Bitmap expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Bitmap actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                try {
                    new AnalogClockFace(new ClockArtwork(context), density).draw(new Canvas(expected), width, height,
                        styles[i], angles, angles.secondDegrees, seconds);
                    reused.draw(new Canvas(actual), width, height, styles[i], angles, angles.secondDegrees, seconds);
                    int[] before = new int[width * height], after = new int[width * height];
                    expected.getPixels(before, 0, width, 0, 0, width, height);
                    actual.getPixels(after, 0, width, 0, 0, width, height);
                    if (!Arrays.equals(before, after))
                        throw new AssertionError("Reusing a renderer changed pixels at size=" + s + " style=" + styles[i]);
                    File file = new File(folder, s + "-" + i + "-" + styles[i] + ".png");
                    try (FileOutputStream output = new FileOutputStream(file)) {
                        if (!actual.compress(Bitmap.CompressFormat.PNG, 100, output))
                            throw new java.io.IOException("Cannot encode analog fixture");
                    }
                } finally { expected.recycle(); actual.recycle(); }
            }
        }
    }

    private static void cacheFailure() {
        Bitmap[] pending = new Bitmap[1];
        try {
            Bitmap result = AnalogClockFace.buildDial(32, 32, bitmap -> {
                pending[0] = bitmap;
                throw new OutOfMemoryError("fixture: artwork loading after bitmap allocation");
            });
            if (result != null || pending[0] == null || !pending[0].isRecycled())
                throw new AssertionError("An incomplete dial must be released and never published");
        } finally { if (pending[0] != null && !pending[0].isRecycled()) pending[0].recycle(); }
        RuntimeException expected = new IllegalStateException("fixture: invalid artwork");
        try {
            AnalogClockFace.buildDial(32, 32, bitmap -> { pending[0] = bitmap; throw expected; });
            throw new AssertionError("Non-memory errors must remain errors");
        } catch (RuntimeException observed) {
            if (observed != expected || !pending[0].isRecycled())
                throw new AssertionError("Unexpected errors must release their unpublished bitmap and propagate");
        } finally { if (pending[0] != null && !pending[0].isRecycled()) pending[0].recycle(); }
        Bitmap complete = AnalogClockFace.buildDial(32, 32, bitmap -> bitmap.eraseColor(0xffd4af37));
        if (complete == null) throw new AssertionError("A completed dial must be published");
        try {
            if (complete.isRecycled() || complete.getPixel(0, 0) != 0xffd4af37)
                throw new AssertionError("Publishing must preserve completed bitmap contents");
        } finally { complete.recycle(); }
    }
}
