package com.linzin.legacy;

import android.app.Activity;
import android.app.Instrumentation;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import java.util.Locale;

final class LanguageSettingsChecks {
    static Activity run(Instrumentation test, Activity clock) {
        Locale original = Locale.getDefault();
        for (int row : new int[] { 1, 4 }) {
            String tag = row == 1 ? "zh-TW" : "en";
            LegacyInstrumentation runner = (LegacyInstrumentation) test;
            PreferenceActivity settings = (PreferenceActivity) runner.openSettings(clock);
            {
                SettingsChecks.choose(test, settings, (ListPreference) settings.findPreference("pref_app_language"), row);
                Activity translated = runner.awaitResumed(settings.getComponentName().getClassName(), settings);
                if (!matches(translated, tag))
                    throw new AssertionError("Language selection must recreate translated settings: " + tag);
                if (!tag.equals(clock.getSharedPreferences("desk_clock_prefs", 0).getString("pref_app_language", "")))
                    throw new AssertionError("Selected language must persist");
                test.runOnMainSync(translated::finish);
                Activity resumed = runner.awaitResumed(clock.getComponentName().getClassName(), clock);
                if (!matches(resumed, tag)) throw new AssertionError("Returning clock must adopt the selected language: " + tag);
                clock = resumed;
            }
        }
        if (!Locale.getDefault().equals(original)) throw new AssertionError("Language selection must leave the global locale intact");
        return clock;
    }
    private static boolean matches(Activity activity, String tag) {
        Locale locale = activity.getResources().getConfiguration().locale;
        return "en".equals(tag) ? locale.getLanguage().equals("en") : locale.getLanguage().equals("zh") && locale.getCountry().equals("TW");
    }
}
