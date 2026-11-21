package com.linzin.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.cert.Certificate;
import javax.net.ssl.HttpsURLConnection;

final class UpdateChecks {
    private static int requests;
    private static String mode;
    private static final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
    private static final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    static void install(String selectedMode) {
        mode = selectedMode;
        URL.setURLStreamHandlerFactory(scheme -> !"https".equals(scheme) && !"http".equals(scheme) ? null : new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws java.io.IOException {
                if (!"https".equals(scheme) || !"api.github.com".equals(url.getHost())
                    || !"/repos/linzin86/LinzinDeskClock/releases/latest".equals(url.getPath()))
                    throw new java.io.IOException("Unexpected update URL");
                requests++;
                return new HttpsURLConnection(url) {
                    public void connect() { }
                    public void disconnect() { }
                    public boolean usingProxy() { return false; }
                    public String getCipherSuite() { return "fixture"; }
                    public Certificate[] getLocalCertificates() { return null; }
                    public Certificate[] getServerCertificates() { return null; }
                    public int getResponseCode() { return 200; }
                    public InputStream getInputStream() throws java.io.IOException {
                        if ("updateCancel".equals(mode)) {
                            entered.countDown();
                            long deadline = SystemClock.elapsedRealtime() + 10000;
                            while (release.getCount() != 0 && SystemClock.elapsedRealtime() < deadline) {
                                try { release.await(100, java.util.concurrent.TimeUnit.MILLISECONDS); }
                                catch (InterruptedException ignored) { }
                            }
                        }
                        if ("updateInvalid".equals(mode)) return new ByteArrayInputStream("{\"tag_name\":\"\"}".getBytes("UTF-8"));
                        return new ByteArrayInputStream("{\"tag_name\":\"v999.1\",\"html_url\":\"http://untrusted.invalid/app.apk\"}".getBytes("UTF-8"));
                    }
                };
            }
        });
    }
    static void run(LegacyInstrumentation test, Activity clock, boolean live) {
        PreferenceActivity settings = (PreferenceActivity) test.openSettings(clock);
        Preference update = settings.findPreference("check_update");
        if (update == null) throw new AssertionError("Settings must expose an HTTPS update check and copyable release information");
        SettingsChecks.click(test, settings, update);
        if ("updateCancel".equals(mode)) {
            try {
                if (!entered.await(45, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("The update must reach the pending response");
                test.runOnMainSync(settings::finish);
                test.awaitResumed(clock.getComponentName().getClassName(), null);
            } catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            finally { release.countDown(); }
            SystemClock.sleep(500); test.waitForIdleSync();
            if (settings.getFragmentManager().findFragmentByTag("update_result") != null)
                throw new AssertionError("A cancelled update must not publish a late result");
            return;
        }
        AlertDialog[] dialog = new AlertDialog[1];
        long deadline = SystemClock.elapsedRealtime() + 45000;
        do {
            SystemClock.sleep(100);
            test.runOnMainSync(() -> {
                android.app.Fragment result = settings.getFragmentManager().findFragmentByTag("update_result");
                if (result instanceof DialogFragment) dialog[0] = (AlertDialog) ((DialogFragment) result).getDialog();
            });
        } while (dialog[0] == null && SystemClock.elapsedRealtime() < deadline);
        if (dialog[0] == null || !live && requests != 1) throw new AssertionError("The explicit update query must complete exactly once");
        test.runOnMainSync(() -> {
            android.widget.TextView message = dialog[0].findViewById(android.R.id.message);
            String expected = live ? "Latest upstream release:" : "updateInvalid".equals(mode) ? "Could not check" : "v999.1";
            if (message == null || !message.getText().toString().contains(expected)
                || !message.getText().toString().contains("API 21"))
                throw new AssertionError("Release information must identify the upstream release and Legacy compatibility limit");
            dialog[0].getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        });
        test.waitForIdleSync();
        test.runOnMainSync(() -> {
            ClipboardManager clipboard = (ClipboardManager) settings.getSystemService(Context.CLIPBOARD_SERVICE);
            String copied = clipboard.getPrimaryClip().getItemAt(0).coerceToText(settings).toString();
            String expected = live ? "Latest upstream release:" : "updateInvalid".equals(mode) ? "Could not check" : "v999.1";
            if (!copied.contains(expected) || !copied.contains("https://github.com/linzin86/LinzinDeskClock/releases")
                || copied.contains("untrusted.invalid")) throw new AssertionError("Copy must retain safe release information without trusting server download URLs");
        });
        test.runOnMainSync(settings::finish);
    }
    static void browser(Context clock) {
        for (boolean denied : new boolean[] { false, true }) {
            Context unavailable = new android.content.ContextWrapper(clock) {
                @Override public void startActivity(android.content.Intent intent) {
                    if (!"https://github.com/linzin86/LinzinDeskClock/releases".equals(intent.getDataString()))
                        throw new AssertionError("Browser intent must remain on the trusted release page");
                    if (denied) throw new SecurityException("Fixture denial");
                    throw new android.content.ActivityNotFoundException("Fixture unavailable browser");
                }
            };
            if (LegacyUpdates.open(unavailable)) throw new AssertionError("Unavailable browser must preserve the copy fallback");
        }
    }
}
