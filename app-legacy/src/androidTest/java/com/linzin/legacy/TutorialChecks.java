package com.linzin.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Instrumentation;
import android.preference.PreferenceActivity;
import android.widget.CheckBox;
import android.widget.TextView;

final class TutorialChecks {
    static void first(Instrumentation test, Activity activity) {
        AlertDialog guide = dialog(activity);
        if (guide == null) throw new AssertionError("First launch must show the gesture guide");
        TextView text = guide.getWindow().getDecorView().findViewWithTag("tutorial_text");
        if (text == null || text.getText().length() == 0) throw new AssertionError("Guide must explain accessible clock controls");
        CheckBox dismiss = guide.getWindow().getDecorView().findViewWithTag("tutorial_dismiss");
        if (dismiss == null) throw new AssertionError("Guide must offer a persistent do-not-show-again choice");
        test.runOnMainSync(() -> { dismiss.performClick(); guide.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); });
        test.waitForIdleSync();
        if (!activity.getSharedPreferences("desk_clock_prefs", 0).getBoolean("pref_dismiss_gesture_tutorial", false))
            throw new AssertionError("Guide dismissal must persist");
    }
    static void reopen(Instrumentation test, Activity activity) {
        if (dialog(activity) != null) throw new AssertionError("Dismissed first-run guide must stay dismissed after reopening");
        PreferenceActivity settings = (PreferenceActivity) ((LegacyInstrumentation) test).openSettings(activity);
        SettingsChecks.click(test, settings, settings.findPreference("tutorial"));
        AlertDialog guide = dialog(settings);
        if (guide == null) throw new AssertionError("Settings must reopen the guide even after dismissal");
        test.runOnMainSync(() -> guide.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        test.runOnMainSync(settings::finish); test.waitForIdleSync();
    }
    private static AlertDialog dialog(Activity activity) {
        android.app.Fragment fragment = activity.getFragmentManager().findFragmentByTag("gesture_guide");
        if (!(fragment instanceof DialogFragment)) return null;
        android.app.Dialog dialog = ((DialogFragment) fragment).getDialog();
        return dialog instanceof AlertDialog && dialog.isShowing() ? (AlertDialog) dialog : null;
    }
}
