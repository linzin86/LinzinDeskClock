package com.linzin.legacy;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.view.View;
import com.linzin.clockcore.ClockAngles;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

final class LegacyClockView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private final Calendar calendar = Calendar.getInstance();
    private boolean active;
    private boolean is24Hour;
    private long wallTime = System.currentTimeMillis();
    private float secondAngle = Float.NaN;
    private String displayedTime;
    private ValueAnimator animator;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!active) return;
            wallTime = System.currentTimeMillis();
            calendar.setTimeInMillis(wallTime);
            ClockAngles angles = angles();
            if (animator != null) animator.cancel();
            if (Float.isNaN(secondAngle)) secondAngle = angles.secondDegrees;
            secondAngle %= 360f;
            float delta = secondHandAdvance(secondAngle, calendar.get(Calendar.SECOND));
            animator = ValueAnimator.ofFloat(secondAngle, secondAngle + delta);
            animator.setDuration(180);
            animator.addUpdateListener(value -> { secondAngle = (Float) value.getAnimatedValue(); invalidate(); });
            animator.start();
            updateDescription();
            handler.postDelayed(this, 1000 - wallTime % 1000);
        }
    };

    LegacyClockView(Context context, boolean is24Hour) {
        super(context);
        setTag("clock");
        setFocusable(true);
        this.is24Hour = is24Hour;
        updateDescription();
    }

    void start() {
        stop();
        secondAngle = Float.NaN;
        active = true;
        handler.post(ticker);
    }

    void stop() {
        active = false;
        handler.removeCallbacks(ticker);
        if (animator != null) animator.cancel();
    }

    void set24Hour(boolean enabled) { is24Hour = enabled; updateDescription(); invalidate(); }

    static float secondHandAdvance(float previous, int second) {
        return (second * 6f - 90f - previous % 360f + 720f) % 360f;
    }

    private void updateDescription() {
        displayedTime = new SimpleDateFormat(is24Hour ? "HH:mm:ss" : "hh:mm:ss a", Locale.US).format(new Date(wallTime));
        setContentDescription(getContext().getString(R.string.clock_readout, displayedTime));
    }

    private ClockAngles angles() {
        return ClockAngles.at(calendar.get(Calendar.HOUR), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.BLACK);
        float cx = getWidth() / 2f, cy = getHeight() * 0.44f;
        float radius = Math.min(getWidth(), getHeight()) * 0.34f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, radius, paint);
        for (int i = 0; i < 12; i++) hand(canvas, cx, cy, i * 30f - 90f, radius * .91f, radius, Color.WHITE, 3);
        ClockAngles angles = angles();
        hand(canvas, cx, cy, angles.hourDegrees, 0, radius * .52f, Color.WHITE, 7);
        hand(canvas, cx, cy, angles.minuteDegrees, 0, radius * .78f, Color.WHITE, 4);
        hand(canvas, cx, cy, Float.isNaN(secondAngle) ? angles.secondDegrees : secondAngle, 0, radius * .86f, 0xFF81C7D4, 2);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(22 * getResources().getDisplayMetrics().scaledDensity);
        canvas.drawText(displayedTime, cx, getHeight() * .92f, paint);
    }

    private void hand(Canvas canvas, float x, float y, float degrees, float from, float to, int color, float width) {
        double radians = Math.toRadians(degrees);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        canvas.drawLine(x + (float) Math.cos(radians) * from, y + (float) Math.sin(radians) * from,
            x + (float) Math.cos(radians) * to, y + (float) Math.sin(radians) * to, paint);
    }
}
