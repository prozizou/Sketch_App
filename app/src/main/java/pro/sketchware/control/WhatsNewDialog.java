package pro.sketchware.control;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.activities.onboarding.OnboardingActivity;

/**
 * "What's new" dialog: shown once after the app was updated to a newer versionCode, and on demand from the
 * main menu. The text lives in the {@code whats_new_items} string array, which is rewritten for each release.
 * On a fresh install the first-launch guide is shown instead, so the current version is simply marked as seen.
 */
public final class WhatsNewDialog {

    private static final String PREFS = "whats_new";
    private static final String KEY_LAST_SEEN = "last_seen_version_code";

    private WhatsNewDialog() {
    }

    public static void showIfNeeded(AppCompatActivity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getInt(KEY_LAST_SEEN, 0) >= BuildConfig.VERSION_CODE) {
            return;
        }
        markSeen(activity);
        show(activity);
    }

    /** Records the running version as seen without showing anything (used after the first-launch guide). */
    public static void markSeen(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_LAST_SEEN, BuildConfig.VERSION_CODE).apply();
    }

    public static void show(AppCompatActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        for (String item : activity.getResources().getStringArray(R.array.whats_new_items)) {
            message.append("•  ").append(item).append("\n\n");
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.whats_new_title, BuildConfig.VERSION_NAME))
                .setMessage(message.toString().trim())
                .setPositiveButton(R.string.whats_new_got_it, null)
                .setNeutralButton(R.string.community_telegram, (dialog, which) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OnboardingActivity.TELEGRAM_URL)));
                    } catch (Exception ignored) {
                    }
                })
                .show();
    }
}
