package com.linzin.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class LegacyTutorial extends DialogFragment {
    static void show(Activity activity) {
        if (!activity.isFinishing() && activity.getFragmentManager().findFragmentByTag("gesture_guide") == null)
            new LegacyTutorial().show(activity.getFragmentManager(), "gesture_guide");
    }
    @Override public Dialog onCreateDialog(Bundle state) {
        Activity activity = getActivity();
        android.content.SharedPreferences prefs = activity.getSharedPreferences("desk_clock_prefs", 0);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density); content.setPadding(pad, pad, pad, pad);
        scroll.addView(content);
        TextView guide = new TextView(activity); guide.setTag("tutorial_text"); guide.setText(R.string.legacy_gesture_guide);
        guide.setTextSize(16); content.addView(guide);
        CheckBox dismiss = new CheckBox(activity); dismiss.setTag("tutorial_dismiss"); dismiss.setText(R.string.tutorial_do_not_show_again);
        dismiss.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
        dismiss.setChecked(prefs.getBoolean("pref_dismiss_gesture_tutorial", false)); content.addView(dismiss);
        return new AlertDialog.Builder(activity).setTitle(R.string.tutorial_title).setView(scroll)
            .setPositiveButton(R.string.tutorial_btn_close, (dialog, which) ->
                prefs.edit().putBoolean("pref_dismiss_gesture_tutorial", dismiss.isChecked()).apply()).create();
    }
}
