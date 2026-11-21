package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.res.Resources;
import java.util.Locale;

final class LocaleChecks {
    static void run(Instrumentation test, Activity activity, String tag, Locale processLocale, Locale applicationLocale, String applicationTitle) {
        Locale expected = "ja".equals(tag) ? Locale.JAPANESE : "zh-TW".equals(tag) ? Locale.TRADITIONAL_CHINESE
            : "zh-CN".equals(tag) ? Locale.SIMPLIFIED_CHINESE : "en".equals(tag) ? Locale.ENGLISH
            : Resources.getSystem().getConfiguration().locale;
        verify(activity, expected);
        RotationChecks.run(test, activity);
        verify(activity, expected);
        if (!Locale.getDefault().equals(processLocale)) throw new AssertionError("App language must not change the process locale");
        if (!test.getTargetContext().getResources().getConfiguration().locale.equals(applicationLocale))
            throw new AssertionError("App language must not mutate application resources");
        int id = test.getTargetContext().getResources().getIdentifier("settings_title", "string", activity.getPackageName());
        if (!applicationTitle.equals(test.getTargetContext().getString(id)))
            throw new AssertionError("App language must not mutate shared application assets");
    }
    private static void verify(Activity activity, Locale expected) {
        Locale actual = activity.getResources().getConfiguration().locale;
        if (!actual.getLanguage().equals(expected.getLanguage()) || !actual.getCountry().equals(expected.getCountry()))
            throw new AssertionError("Requested Activity locale " + expected + ", got " + actual);
    }
}
