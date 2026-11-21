package com.linzin.legacy;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import com.linzin.clockcore.ClockAngles;

final class AnalogClockFace {
    private final ClockArtwork artwork;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private static final int[] NUMBERS = { 12, 3, 6, 9 };
    private static final int[] COLORS = { 0xffffd166, 0xff81c784, 0xffff708d, 0xff90caf9 };
    private final float density;
    private final float[] squircleX = new float[60], squircleY = new float[60];
    AnalogClockFace(ClockArtwork artwork, float density) {
        this.artwork = artwork; this.density = density;
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90), x = Math.cos(angle), y = Math.sin(angle);
            double scale = Math.pow(Math.pow(Math.abs(x), 3.6) + Math.pow(Math.abs(y), 3.6), -1 / 3.6);
            squircleX[i] = (float) (x * scale); squircleY[i] = (float) (y * scale);
        }
    }
    void draw(Canvas canvas, int width, int height, ClockAppearance style, ClockAngles angles, float second, boolean seconds) {
        float cx = width / 2f, cy = height / 2f, r = Math.min(width, height) / 2f;
        canvas.drawColor(style == ClockAppearance.ABW ? 0xffeed55e : style == ClockAppearance.OET ? 0xff0d0d11 : 0xff161413);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
            boolean major = i % 5 == 0;
            if (style == ClockAppearance.ABW) {
                float edge = Math.min(Math.abs(x) < .0001f ? Float.MAX_VALUE : cx / Math.abs(x),
                    Math.abs(y) < .0001f ? Float.MAX_VALUE : cy / Math.abs(y));
                float length = Math.min((major ? 32 : 18) * density, r * .22f);
                line(canvas, cx + x * edge, cy + y * edge, cx + x * (edge - length), cy + y * (edge - length),
                    major ? 0xff181818 : 0x8c181818, (major ? 3.5f : 1.8f) * density, Paint.Cap.SQUARE);
            } else if (style == ClockAppearance.OET) {
                if (major) {
                    float px = cx + x * r * .885f, py = cy + y * r * .885f, size = Math.min(3.2f * density, r * .04f);
                    shape.reset(); shape.moveTo(px, py - size); shape.lineTo(px + size * .7f, py);
                    shape.lineTo(px, py + size); shape.lineTo(px - size * .7f, py); shape.close();
                    paint.setColor(0xffd4af37); canvas.drawPath(shape, paint);
                } else line(canvas, cx + x * r * .85f, cy + y * r * .85f, cx + x * r * .92f, cy + y * r * .92f,
                    0x808c7326, density, Paint.Cap.SQUARE);
            } else {
                paint.setColor(major ? 0xffe6dccd : 0xff4a403a);
                canvas.drawCircle(cx + squircleX[i] * r * .86f, cy + squircleY[i] * r * .86f,
                    Math.min((major ? 3.4f : 1.6f) * density, r * .045f), paint);
            }
        }
        if (style == ClockAppearance.OET) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f * density); paint.setColor(0x738c7326);
            canvas.drawCircle(cx, cy, r * .92f, paint); canvas.drawCircle(cx, cy, r * .85f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float numberHeight = style == ClockAppearance.ABW ? Math.min(r * .32f, 54 * density)
            : Math.min(r * (style == ClockAppearance.OET ? .30f : .28f), 62 * density);
        float numberR = r * (style == ClockAppearance.OET ? .646f : .6364f);
        for (int i = 0; i < 4; i++) {
            String name = style.prefix + "_" + NUMBERS[i];
            float w = numberHeight * artwork.aspect(name);
            float nx = cx, ny = cy;
            if (i == 0) ny -= style == ClockAppearance.ABW ? cy - numberHeight / 2 - Math.min(40 * density, r * .28f) : numberR;
            if (i == 2) ny += style == ClockAppearance.ABW ? cy - numberHeight / 2 - Math.min(40 * density, r * .28f) : numberR;
            if (i == 1) nx += style == ClockAppearance.ABW ? cx - w / 2 - Math.min(40 * density, r * .28f) : numberR;
            if (i == 3) nx -= style == ClockAppearance.ABW ? cx - w / 2 - Math.min(40 * density, r * .28f) : numberR;
            artwork.draw(canvas, name, nx - w / 2, ny - numberHeight / 2, numberHeight,
                style == ClockAppearance.ABW ? null : style == ClockAppearance.OET ? 0xffd4af37 : COLORS[i]);
        }
        if (style == ClockAppearance.OET) {
            sword(canvas, cx, cy, angles.hourDegrees, r * .50f, Math.min(4.8f * density, r * .05f));
            sword(canvas, cx, cy, angles.minuteDegrees, r * .75f, Math.min(3.8f * density, r * .04f));
        } else {
            hand(canvas, cx, cy, angles.hourDegrees, style == ClockAppearance.ZMG ? -r * .10f : 0,
                r * (style == ClockAppearance.ZMG ? .48f : .52f), style == ClockAppearance.ZMG ? 0xfff3eee6 : Color.WHITE,
                Math.min(8 * density, r * .08f));
            hand(canvas, cx, cy, angles.minuteDegrees, style == ClockAppearance.ZMG ? -r * .10f : 0,
                r * (style == ClockAppearance.ZMG ? .72f : .78f), style == ClockAppearance.ZMG ? 0xffe09f67 : Color.WHITE,
                Math.min(5.5f * density, r * .055f));
        }
        int secondColor = style == ClockAppearance.ABW ? 0xff181818 : style == ClockAppearance.OET ? 0xffd4af37 : 0xffff5252;
        if (seconds) {
            hand(canvas, cx, cy, second, -r * .16f, r * .86f, secondColor, Math.min(2 * density, r * .025f));
            if (style == ClockAppearance.ZMG) {
                double rad = Math.toRadians(second);
                paint.setColor(secondColor);
                canvas.drawCircle(cx + (float) Math.cos(rad) * r * .82f, cy + (float) Math.sin(rad) * r * .82f,
                    Math.min(5.2f * density, r * .06f), paint);
            }
        }
        paint.setColor(style == ClockAppearance.OET ? 0xffd4af37 : style == ClockAppearance.ZMG ? 0xfff3eee6 : Color.WHITE);
        canvas.drawCircle(cx, cy, Math.min(7 * density, r * .08f), paint);
        if (seconds) { paint.setColor(secondColor); canvas.drawCircle(cx, cy, Math.min(3 * density, r * .035f), paint); }
    }
    private void sword(Canvas canvas, float x, float y, float angle, float length, float width) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(angle);
        shape.reset(); shape.moveTo(length, 0); shape.lineTo(length * .72f, width * .85f); shape.lineTo(0, width);
        shape.lineTo(-length * .18f, 0); shape.lineTo(0, -width); shape.lineTo(length * .72f, -width * .85f); shape.close();
        paint.setColor(0xffe8e8ee); canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f * density); paint.setColor(0xffd4af37);
        canvas.drawPath(shape, paint); paint.setStyle(Paint.Style.FILL); canvas.restore();
    }
    private void hand(Canvas canvas, float x, float y, float angle, float from, float to, int color, float width) {
        double rad = Math.toRadians(angle);
        float dx = (float) Math.cos(rad), dy = (float) Math.sin(rad);
        line(canvas, x + from * dx, y + from * dy, x + to * dx, y + to * dy, color, width, Paint.Cap.ROUND);
    }
    private void line(Canvas canvas, float x1, float y1, float x2, float y2, int color, float width, Paint.Cap cap) {
        paint.setColor(color); paint.setStrokeWidth(width); paint.setStrokeCap(cap); canvas.drawLine(x1, y1, x2, y2, paint);
    }
}
