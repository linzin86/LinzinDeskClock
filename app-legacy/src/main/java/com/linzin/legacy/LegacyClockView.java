package com.linzin.legacy;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.content.SharedPreferences;
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
    private final SharedPreferences prefs;
    private final ClockArtwork artwork;
    private final DigitalClockFace digital;
    private final AnalogClockFace analog;
    private ClockAppearance appearance;
    private boolean active;
    private boolean is24Hour;
    private boolean showSeconds;
    private boolean showDate;
    private long wallTime = System.currentTimeMillis();
    private float secondAngle = Float.NaN;
    private String displayedTime;
    private String displayedDate;
    private String previousDigits = "";
    private String digits = "";
    private float flipProgress = 1;
    private ValueAnimator animator;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!active) return;
            updateTime(System.currentTimeMillis());
            ClockAngles angles = angles();
            if (animator != null) animator.cancel();
            if (appearance.family == 1 && showSeconds) {
                if (Float.isNaN(secondAngle)) secondAngle = angles.secondDegrees;
                secondAngle %= 360f;
                float delta = secondHandAdvance(secondAngle, calendar.get(Calendar.SECOND));
                animator = ValueAnimator.ofFloat(secondAngle, secondAngle + delta);
                animator.setDuration(180);
                animator.addUpdateListener(value -> { secondAngle = (Float) value.getAnimatedValue(); invalidate(); });
                animator.start();
            } else if (appearance.family == 2 && !previousDigits.equals(digits)) {
                flipProgress = 0;
                animator = ValueAnimator.ofFloat(0, 1);
                animator.setDuration(300);
                animator.addUpdateListener(value -> { flipProgress = (Float) value.getAnimatedValue(); invalidate(); });
                animator.start();
            }
            invalidate();
            long step = showSeconds ? 1000 : 60000;
            handler.postDelayed(this, step - wallTime % step);
        }
    };

    LegacyClockView(Context context, boolean is24Hour) {
        super(context);
        setTag("clock");
        setFocusable(true);
        prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE);
        artwork = new ClockArtwork(context);
        digital = new DigitalClockFace(artwork);
        analog = new AnalogClockFace(artwork, getResources().getDisplayMetrics().density);
        this.is24Hour = is24Hour;
        reloadSettings();
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

    void set24Hour(boolean enabled) { is24Hour = enabled; updateTime(wallTime); previousDigits = digits; invalidate(); }

    void reloadSettings() {
        ClockAppearance selected = ClockAppearance.read(prefs);
        if (appearance != selected) artwork.clear();
        appearance = selected;
        showSeconds = appearance.seconds(prefs);
        showDate = prefs.getBoolean("pref_show_corner_date", true);
        updateTime(wallTime);
        previousDigits = digits;
        flipProgress = 1;
        if (active) start();
        invalidate();
    }

    void updateTime(long instant) {
        previousDigits = digits;
        wallTime = instant;
        calendar.setTimeZone(java.util.TimeZone.getDefault());
        calendar.setTimeInMillis(instant);
        String pattern = (is24Hour ? "HH:mm" : "hh:mm") + (showSeconds ? ":ss" : "");
        Date moment = new Date(wallTime);
        digits = new SimpleDateFormat(pattern, Locale.US).format(moment);
        displayedDate = new SimpleDateFormat("M/d EEE", getResources().getConfiguration().locale).format(moment);
        displayedTime = digits + (is24Hour ? "" : calendar.get(Calendar.AM_PM) == Calendar.AM ? " AM" : " PM");
        setContentDescription(getContext().getString(R.string.clock_readout,
            showDate ? displayedTime + " · " + displayedDate : displayedTime));
    }

    static float secondHandAdvance(float previous, int second) {
        return (second * 6f - 90f - previous % 360f + 720f) % 360f;
    }

    private ClockAngles angles() {
        return ClockAngles.at(calendar.get(Calendar.HOUR), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (appearance.family == 1) {
            ClockAngles angles = angles();
            analog.draw(canvas, getWidth(), getHeight(), appearance, angles,
                Float.isNaN(secondAngle) ? angles.secondDegrees : secondAngle, showSeconds);
        } else digital.draw(canvas, getWidth(), getHeight(), appearance, digits, previousDigits,
            is24Hour, calendar.get(Calendar.AM_PM) == Calendar.AM, flipProgress);
        if (showDate) {
            paint.setColor(appearance == ClockAppearance.ABW ? 0xff222222 : 0xffa0a0aa);
            paint.setTextSize(Math.min(14 * getResources().getDisplayMetrics().scaledDensity, getHeight() * .08f));
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(displayedDate, 12 * getResources().getDisplayMetrics().density,
                getHeight() - 8 * getResources().getDisplayMetrics().density, paint);
        }
    }
    @Override public boolean performClick() { return super.performClick(); }
}
