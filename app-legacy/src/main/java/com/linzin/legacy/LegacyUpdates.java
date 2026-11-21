package com.linzin.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONObject;

final class LegacyUpdates {
    static final String RELEASES = "https://github.com/linzin86/LinzinDeskClock/releases";
    private final Activity activity;
    private final Preference preference;
    private final Handler main = new Handler();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile LegacyHttps client;
    private Future<?> task;
    private boolean active;
    private int generation;

    LegacyUpdates(Activity activity, Preference preference) {
        this.activity = activity; this.preference = preference;
        preference.setOnPreferenceClickListener(value -> { check(); return true; });
    }
    void resume() { active = true; }
    private void check() {
        if (!active || task != null) return;
        final int ticket = ++generation;
        preference.setEnabled(false); preference.setSummary(R.string.update_checking);
        task = worker.submit(() -> {
            String version = null;
            try {
                LegacyHttps request = new LegacyHttps(LegacyTls.factory(activity.getApplicationContext()));
                client = request;
                JSONObject result = new JSONObject(request.get(new URL("https://api.github.com/repos/linzin86/LinzinDeskClock/releases/latest")));
                String tag = result.getString("tag_name").trim();
                if (tag.length() == 0 || tag.length() > 100) throw new IllegalArgumentException("Invalid release tag");
                for (int i = 0; i < tag.length(); i++)
                    if (Character.isISOControl(tag.charAt(i))) throw new IllegalArgumentException("Invalid release tag");
                version = tag;
            } catch (Exception unavailable) {
                android.util.Log.w("LegacyClock", "Update query failed: " + unavailable.getClass().getName());
            } finally { client = null; }
            final String result = version;
            main.post(() -> {
                if (!active || ticket != generation) return;
                task = null; preference.setEnabled(true); preference.setSummary(null);
                if (activity.getFragmentManager().findFragmentByTag("update_result") != null) return;
                ResultDialog dialog = new ResultDialog();
                Bundle values = new Bundle(); values.putString("version", result); dialog.setArguments(values);
                dialog.show(activity.getFragmentManager(), "update_result");
            });
        });
    }
    void pause() {
        active = false; generation++;
        if (task != null) task.cancel(true);
        task = null;
        LegacyHttps request = client; if (request != null) request.cancel();
        preference.setEnabled(true); preference.setSummary(null);
    }
    void close() { pause(); worker.shutdownNow(); main.removeCallbacksAndMessages(null); }

    static boolean open(Context context) {
        try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES))); return true; }
        catch (ActivityNotFoundException | SecurityException unavailable) { return false; }
    }
    public static final class ResultDialog extends DialogFragment {
        private String information() {
            String version = getArguments().getString("version");
            return (version == null ? getString(R.string.update_failed) : getString(R.string.update_latest_upstream, version))
                + "\n\n" + getString(R.string.update_legacy_limit) + "\n\n" + RELEASES;
        }
        @Override public Dialog onCreateDialog(Bundle state) {
            return new AlertDialog.Builder(getActivity()).setTitle(R.string.update_check).setMessage(information())
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(android.R.string.copy, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.update_check), information()));
                }).setPositiveButton(R.string.update_open, null).create();
        }
        @Override public void onStart() {
            super.onStart();
            AlertDialog dialog = (AlertDialog) getDialog();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (open(getActivity())) dismiss();
                else dialog.setMessage(information() + "\n\n" + getString(R.string.update_browser_unavailable));
            });
        }
    }
}
