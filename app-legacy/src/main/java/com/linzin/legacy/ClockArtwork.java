package com.linzin.legacy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import java.util.HashMap;
import java.util.Map;

final class ClockArtwork {
    private final Context context;
    private final Map<String, Drawable> active = new HashMap<>();
    private final Map<String, Integer> tints = new HashMap<>();
    ClockArtwork(Context context) { this.context = context; }
    void clear() { active.clear(); tints.clear(); }
    private Drawable get(String name) {
        Drawable drawable = active.get(name);
        if (drawable == null) {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) throw new IllegalArgumentException("Missing clock artwork: " + name);
            drawable = context.getResources().getDrawable(id).mutate();
            if (drawable instanceof BitmapDrawable) ((BitmapDrawable) drawable).setFilterBitmap(true);
            active.put(name, drawable);
        }
        return drawable;
    }
    float aspect(String name) {
        Drawable drawable = get(name);
        return (float) drawable.getIntrinsicWidth() / drawable.getIntrinsicHeight();
    }
    float draw(Canvas canvas, String name, float x, float y, float height, Integer tint) {
        Drawable drawable = get(name);
        float width = height * aspect(name);
        drawable.setBounds(Math.round(x), Math.round(y), Math.round(x + width), Math.round(y + height));
        if (!tints.containsKey(name) || (tint == null ? tints.get(name) != null : !tint.equals(tints.get(name)))) {
            if (tint == null) drawable.clearColorFilter();
            else drawable.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
            tints.put(name, tint);
        }
        drawable.draw(canvas);
        return width;
    }
}
