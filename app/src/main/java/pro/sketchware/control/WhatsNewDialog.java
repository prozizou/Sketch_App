package pro.sketchware.control;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.project.ProjectTracker;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.activities.onboarding.OnboardingActivity;
import pro.sketchware.databinding.ItemWhatsNewBinding;
import pro.sketchware.utility.SketchwareUtil;

/**
 * "What's new" dialog: shown once after the app was updated to a newer versionCode, and any time later from the
 * home menu (What's new). Each entry opens the feature it describes; a feature that lives in a project first asks
 * which project. The entries live in the {@code whats_new_entries} string array, rewritten for each release
 * (see {@link WhatsNewCatalog} for the format). On a fresh install the first-launch guide is shown instead, so the
 * current version is simply marked as seen.
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
        List<WhatsNewCatalog.Item> items = WhatsNewCatalog.parse(activity.getResources().getStringArray(R.array.whats_new_entries));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, SketchwareUtil.dpToPx(8), 0, 0);
        NestedScrollView scroll = new NestedScrollView(activity);
        scroll.addView(list);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.whats_new_title, BuildConfig.VERSION_NAME))
                .setView(scroll)
                .setPositiveButton(R.string.whats_new_got_it, null)
                .setNeutralButton(R.string.community_telegram, (d, which) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OnboardingActivity.TELEGRAM_URL)));
                    } catch (Exception ignored) {
                    }
                })
                .create();

        for (WhatsNewCatalog.Item item : items) {
            ItemWhatsNewBinding row = ItemWhatsNewBinding.inflate(activity.getLayoutInflater(), list, false);
            row.icon.setImageResource(icon(item.target()));
            row.title.setText(item.title());
            row.description.setText(item.description());
            row.description.setVisibility(item.description().isEmpty() ? View.GONE : View.VISIBLE);
            if (item.target() == WhatsNewCatalog.Target.NONE) {
                row.chevron.setVisibility(View.GONE);
                row.getRoot().setBackground(null);
            } else {
                row.getRoot().setOnClickListener(v -> {
                    dialog.dismiss();
                    open(activity, item);
                });
            }
            list.addView(row.getRoot());
        }
        dialog.show();
    }

    private static int icon(WhatsNewCatalog.Target target) {
        return switch (target) {
            case APP_SETTINGS -> R.drawable.ic_mtrl_settings;
            case PROJECT_HEALTH -> R.drawable.ic_mtrl_shield_check;
            case AUTO_FIX -> R.drawable.ic_mtrl_done;
            case NAVIGATION_GRAPH -> R.drawable.ic_mtrl_link;
            case DATA_DESIGNER -> R.drawable.ic_mtrl_database_edit;
            case DEVICE_PREVIEW -> R.drawable.ic_mtrl_devices;
            case BLOCK_DEBUGGER -> R.drawable.ic_mtrl_bug_report;
            case NONE -> R.drawable.ic_mtrl_info;
        };
    }

    /** Opens what an entry is about; a feature inside a project opens that project on it. */
    private static void open(AppCompatActivity activity, WhatsNewCatalog.Item item) {
        if (item.target() == WhatsNewCatalog.Target.APP_SETTINGS) {
            activity.startActivity(new Intent(activity, ConfigActivity.class));
        } else if (item.target().needsProject) {
            pickProject(activity, scId -> {
                ProjectTracker.setScId(scId);
                Intent intent = new Intent(activity, DesignActivity.class);
                intent.putExtra("sc_id", scId);
                intent.putExtra(DesignActivity.EXTRA_OPEN_FEATURE, item.target().key);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                activity.startActivity(intent);
            });
        }
    }

    /** Lists the projects by name and gives back the chosen one's id. */
    private static void pickProject(AppCompatActivity activity, java.util.function.Consumer<String> onPicked) {
        new Thread(() -> {
            List<HashMap<String, Object>> projects;
            try {
                projects = lC.a();
            } catch (Exception e) {
                projects = new ArrayList<>();
            }
            List<HashMap<String, Object>> found = projects;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (found.isEmpty()) {
                    SketchwareUtil.toast(activity.getString(R.string.whats_new_no_project));
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < names.length; i++) {
                    String name = yB.c(found.get(i), "my_ws_name");
                    String app = yB.c(found.get(i), "my_app_name");
                    names[i] = app.isEmpty() || app.equals(name) ? name : name + " · " + app;
                }
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.whats_new_open_in_project)
                        .setItems(names, (d, which) -> onPicked.accept(yB.c(found.get(which), "sc_id")))
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .show();
            });
        }).start();
    }
}
