package com.linzin.legacy;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

final class ArtworkChecks {
    static void run(Context context) {
        List<String> names = new ArrayList<>();
        for (String prefix : new String[] { "tk", "ttn", "rafm" }) {
            for (int digit = 0; digit < 10; digit++) names.add(prefix + "_" + digit);
            for (String suffix : new String[] { "cl", "am", "pm" }) names.add(prefix + "_" + suffix);
        }
        for (String prefix : new String[] { "abw", "oet", "zmg" })
            for (int digit : new int[] { 12, 3, 6, 9 }) names.add(prefix + "_" + digit);
        for (String prefix : new String[] { "pp", "fft" }) {
            for (int digit = 0; digit < 10; digit++)
                for (String half : new String[] { "u", "d" }) names.add(prefix + "_" + digit + "_" + half);
            for (String suffix : new String[] { "am", "pm" }) names.add(prefix + "_" + suffix);
        }
        names.add("pp_cl");
        for (String name : names) {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) throw new AssertionError("Clock artwork must be present: " + name);
            Drawable drawable = context.getResources().getDrawable(id);
            if (Build.VERSION.SDK_INT < 21 && !(drawable instanceof BitmapDrawable))
                throw new AssertionError("Pre21 artwork must be a generated bitmap: " + name);
            if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0)
                throw new AssertionError("Artwork dimensions must be positive: " + name);
        }
    }
}
