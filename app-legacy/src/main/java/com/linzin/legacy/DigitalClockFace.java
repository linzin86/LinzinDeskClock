package com.linzin.legacy;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

final class DigitalClockFace {
    private final ClockArtwork artwork;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    DigitalClockFace(ClockArtwork artwork) { this.artwork = artwork; }

    void draw(Canvas canvas, int width, int height, ClockAppearance appearance, String time, String previous,
              boolean is24Hour, boolean am, float progress) {
        canvas.drawColor(Color.BLACK);
        boolean flip = appearance.family == 2;
        String prefix = appearance.prefix;
        float units = 0;
        for (int i = 0; i < time.length(); i++) units += digitAspect(prefix, time.charAt(i), flip) + .04f;
        String amPm = prefix + (am ? "_am" : "_pm");
        if (!is24Hour) units += artwork.aspect(amPm) * .25f + .08f;
        float digitHeight = Math.min(height * .64f, width * .90f / units);
        float x = (width - units * digitHeight) / 2f;
        float y = (height - digitHeight) / 2f;
        for (int i = 0; i < time.length(); i++) {
            char digit = time.charAt(i);
            float digitWidth = digitAspect(prefix, digit, flip) * digitHeight;
            if (digit == ':' && flip) {
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x + digitWidth / 2, y + digitHeight * .38f, digitHeight * .025f, paint);
                canvas.drawCircle(x + digitWidth / 2, y + digitHeight * .62f, digitHeight * .025f, paint);
            } else if (flip) {
                char old = previous.length() == time.length() ? previous.charAt(i) : digit;
                drawFlip(canvas, prefix, digit, old, x, y, digitHeight, progress);
            } else artwork.draw(canvas, prefix + "_" + (digit == ':' ? "cl" : digit), x, y, digitHeight, null);
            x += digitWidth + digitHeight * .04f;
        }
        if (!is24Hour) artwork.draw(canvas, amPm, x + digitHeight * .04f,
            y + digitHeight * .375f, digitHeight * .25f, null);
    }

    private float digitAspect(String prefix, char digit, boolean flip) {
        if (flip && digit == ':') return .16f;
        String key = prefix + "_" + (digit == ':' ? "cl" : digit);
        return flip ? artwork.aspect(key + "_u") / 2f : artwork.aspect(key);
    }

    private void drawFlip(Canvas canvas, String prefix, char digit, char old, float x, float y, float height, float progress) {
        float half = height / 2f;
        String incoming = prefix + "_" + digit;
        String outgoing = prefix + "_" + old;
        artwork.draw(canvas, incoming + "_u", x, y, half, null);
        artwork.draw(canvas, old != digit && progress < 1 ? outgoing + "_d" : incoming + "_d", x, y + half, half, null);
        if (old == digit || progress >= 1) return;
        canvas.save();
        if (progress < .5f) {
            canvas.scale(1, (float) Math.cos(progress * Math.PI), x, y + half);
            artwork.draw(canvas, outgoing + "_u", x, y, half, null);
        } else {
            canvas.scale(1, (float) -Math.cos(progress * Math.PI), x, y + half);
            artwork.draw(canvas, incoming + "_d", x, y + half, half, null);
        }
        canvas.restore();
    }
}
