package pro.sketchware.evolve;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.BlockBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;

import a.a.a.Mp;
import a.a.a.Pp;
import a.a.a.lC;
import a.a.a.yB;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.evolve.ImportPlanner.Issue;
import pro.sketchware.evolve.ImportPlanner.OnConflict;
import pro.sketchware.evolve.ImportPlanner.Plan;
import pro.sketchware.evolve.ImportPlanner.Severity;
import pro.sketchware.evolve.ImportPlanner.Target;
import pro.sketchware.utility.SketchwareUtil;

/**
 * The three ways to take a post: Copy puts its text on the clipboard, Download saves it as a file, and Add to project
 * puts it where it belongs after a preview and a confirmation, with an Undo right after. Nothing shared is ever run:
 * it is text that is written to a file or to a collection, and the user decides when to use it.
 */
public final class EvolveImportFlow {

    /** What undoes the last "Add", kept for the Undo button of the message that follows it. */
    private record Undo(String label, ThrowingRunnable action) {
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private EvolveImportFlow() {
    }

    // ---------------------------------------------------------------------------------------------- copy, download

    /** What Copy and Download give: the code or XML as it is, a stack's blocks as JSON. */
    public static String payload(SharedBlock post) {
        if (post.kind() == SharedKind.MOREBLOCK) {
            JsonObject object = new JsonObject();
            object.addProperty("name", post.name());
            object.addProperty("spec", post.spec());
            object.add("blocks", JsonParser.parseString(post.blocksJson()));
            return object.toString();
        }
        return post.kind().hasBlocks() ? post.blocksJson() : post.payloadText();
    }

    /** The file a download is saved as. */
    public static String downloadName(SharedBlock post) {
        if (post.kind() == SharedKind.JAVA || post.kind() == SharedKind.KOTLIN) {
            if (ImportFiles.isPlainFileName(post.meta().fileName())) return post.meta().fileName();
            return PostRules.suggestFileName(post.kind(), post.name());
        }
        String extension = post.kind() == SharedKind.VIEW ? "xml" : "json";
        return ImportPlanner.layoutName(post.name()) + "." + extension;
    }

    public static void copy(Activity activity, SharedBlock post) {
        String text = payload(post);
        if (text.isEmpty()) {
            SketchwareUtil.toastError(activity.getString(R.string.evolve_copy_failed));
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            SketchwareUtil.toastError(activity.getString(R.string.evolve_copy_failed));
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(post.name(), text));
        SketchwareUtil.toast(activity.getString(R.string.evolve_copy_done));
    }

    public static void download(Activity activity, SharedBlock post) {
        String text = payload(post);
        if (text.isEmpty()) {
            SketchwareUtil.toastError(activity.getString(R.string.evolve_copy_failed));
            return;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NWS Evolve");
        String wanted = downloadName(post);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(dir.getPath());
                String name = ImportFiles.uniqueFileName(dir, wanted);
                Files.writeString(new File(dir, name).toPath(), text, StandardCharsets.UTF_8);
                EvolveRepository.countDownload(post.id());
                activity.runOnUiThread(() -> SketchwareUtil.toast(activity.getString(R.string.evolve_download_done, new File(dir, name).getPath())));
            } catch (IOException | RuntimeException e) {
                activity.runOnUiThread(() -> SketchwareUtil.toastError(activity.getString(R.string.evolve_download_failed, e.getMessage())));
            }
        });
    }

    // ---------------------------------------------------------------------------------------------- add to project

    /** Asks for a project when the post belongs to one, then plans, previews, asks and adds. */
    public static void addToProject(Activity activity, SharedBlock post) {
        Target target = ImportPlanner.targetOf(post.kind());
        if (!ImportPlanner.needsProject(target)) {
            plan(activity, post, null, OnConflict.RENAME);
            return;
        }
        pickProject(activity, scId -> plan(activity, post, scId, OnConflict.RENAME));
    }

    private static void pickProject(Activity activity, java.util.function.Consumer<String> onPicked) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<HashMap<String, Object>> projects;
            try {
                projects = lC.a();
            } catch (Exception e) {
                projects = new ArrayList<>();
            }
            List<HashMap<String, Object>> found = projects;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (found.isEmpty()) {
                    SketchwareUtil.toast(activity.getString(R.string.evolve_import_no_project));
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < names.length; i++) {
                    String name = yB.c(found.get(i), "my_ws_name");
                    String app = yB.c(found.get(i), "my_app_name");
                    names[i] = app.isEmpty() || app.equals(name) ? name : name + " · " + app;
                }
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.evolve_import_choose_project)
                        .setItems(names, (d, which) -> onPicked.accept(yB.c(found.get(which), "sc_id")))
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .show();
            });
        });
    }

    private static void plan(Activity activity, SharedBlock post, @Nullable String scId, OnConflict onConflict) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ImportPlanner.Context context = EvolveProjectContext.load(activity, scId);
            Plan plan = ImportPlanner.plan(post, context, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT, onConflict);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (!plan.canApply() && onConflict == OnConflict.RENAME && isConflictOnly(plan)) {
                    offerReplace(activity, post, scId, plan);
                } else {
                    preview(activity, post, scId, context, plan);
                }
            });
        });
    }

    /** A plan stopped only because the name is taken: the user may choose to replace instead. */
    private static boolean isConflictOnly(Plan plan) {
        boolean conflict = false;
        for (Issue issue : plan.issues()) {
            if (issue.severity() != Severity.BLOCKER) continue;
            if (issue.code() == ImportPlanner.Code.FILE_EXISTS || issue.code() == ImportPlanner.Code.COMPONENT_EXISTS) {
                conflict = true;
            } else {
                return false;
            }
        }
        return conflict;
    }

    private static void offerReplace(Activity activity, SharedBlock post, @Nullable String scId, Plan plan) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.evolve_import_conflict_title)
                .setMessage(activity.getString(R.string.evolve_import_conflict_message, plan.name().isEmpty() ? post.name() : plan.name()))
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.evolve_import_replace, (d, w) -> plan(activity, post, scId, OnConflict.REPLACE))
                .show();
    }

    private static void preview(Activity activity, SharedBlock post, @Nullable String scId, ImportPlanner.Context context, Plan plan) {
        StringBuilder text = new StringBuilder();
        text.append(activity.getString(R.string.evolve_import_where, where(activity, plan, context))).append("\n\n");
        text.append(activity.getString(plan.replaces() ? R.string.evolve_import_replaces_note : R.string.evolve_import_safe)).append("\n\n");
        for (Issue issue : plan.issues()) {
            text.append(issue.severity() == Severity.BLOCKER ? "⛔ " : issue.severity() == Severity.WARNING ? "⚠ " : "• ")
                    .append(describe(activity, issue)).append('\n');
        }
        if (!plan.preview().isEmpty()) {
            text.append('\n').append(activity.getString(R.string.evolve_import_preview)).append('\n');
        }
        TextView body = new TextView(activity);
        body.setText(text);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad / 2, pad, pad / 2);
        body.setTextSize(13);
        body.setTextIsSelectable(true);
        TextView code = new TextView(activity);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(11);
        code.setPadding(pad, 0, pad, pad / 2);
        code.setTextIsSelectable(true);
        CodeHighlighter.Language language = post.kind().hasBlocks() ? CodeHighlighter.Language.PLAIN : CodeHighlighter.languageOf(post.kind());
        code.setText(HighlightedText.of(activity, language, plan.preview(), HighlightedText.CARD_CHARS));
        android.widget.LinearLayout column = new android.widget.LinearLayout(activity);
        column.setOrientation(android.widget.LinearLayout.VERTICAL);
        column.addView(body);
        column.addView(code);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(column);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.evolve_import_title, post.name()))
                .setView(scroll)
                .setNegativeButton(R.string.common_word_cancel, null);
        if (plan.canApply()) {
            dialog.setPositiveButton(plan.needsAttention() ? R.string.evolve_import_confirm_anyway : R.string.evolve_import_confirm,
                    (d, w) -> apply(activity, post, scId, plan));
        }
        dialog.show();
    }

    private static String where(Activity activity, Plan plan, ImportPlanner.Context context) {
        return switch (plan.target()) {
            case BLOCK_COLLECTION -> activity.getString(R.string.evolve_target_blocks);
            case MOREBLOCK_COLLECTION -> activity.getString(R.string.evolve_target_moreblocks);
            case LAYOUT_FILE -> activity.getString(R.string.evolve_target_layout, plan.name(), context.projectName());
            case JAVA_FILE, KOTLIN_FILE -> activity.getString(R.string.evolve_target_file, plan.name(), context.projectName());
            case COMPONENT_LIST -> activity.getString(R.string.evolve_target_components);
        };
    }

    /** One issue in the user's language. */
    public static String describe(Context context, Issue issue) {
        String detail = issue.detail();
        return switch (issue.code()) {
            case DAMAGED -> context.getString(R.string.evolve_issue_damaged);
            case INVALID_CODE -> context.getString(R.string.evolve_issue_invalid_code, detail);
            case NEWER_APP -> context.getString(R.string.evolve_issue_newer_app, detail);
            case NEEDS_NEWER_ANDROID_THAN_DEVICE -> context.getString(R.string.evolve_issue_newer_android, part(detail, 0), part(detail, 1));
            case PROJECT_MIN_SDK_TOO_LOW -> context.getString(R.string.evolve_issue_project_min_sdk, part(detail, 0), part(detail, 1));
            case MISSING_LIBRARIES -> context.getString(R.string.evolve_issue_missing_libraries, detail);
            case UNDECLARED_IMPORTS -> context.getString(R.string.evolve_issue_undeclared_imports, detail);
            case RAW_JAVA -> context.getString(R.string.evolve_issue_raw_java);
            case RENAMED -> context.getString(R.string.evolve_issue_renamed, detail);
            case FILE_EXISTS -> context.getString(R.string.evolve_issue_file_exists, detail);
            case DUPLICATE_CONTENT -> context.getString(R.string.evolve_issue_duplicate);
            case REFERENCES_RESOURCES -> context.getString(R.string.evolve_issue_resources, detail);
            case PACKAGE_CHANGED -> context.getString(R.string.evolve_issue_package, detail);
            case COMPONENT_EXISTS -> context.getString(R.string.evolve_issue_component_exists, detail);
            case REPLACES_EXISTING -> context.getString(R.string.evolve_issue_replaces, detail);
        };
    }

    private static String part(String detail, int index) {
        String[] parts = detail.split("/");
        return index < parts.length ? parts[index] : "";
    }

    private static void apply(Activity activity, SharedBlock post, @Nullable String scId, Plan plan) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Undo undo = write(post, scId, plan);
                EvolveRepository.countDownload(post.id());
                activity.runOnUiThread(() -> announce(activity, post, plan, undo));
            } catch (Exception e) {
                activity.runOnUiThread(() -> SketchwareUtil.toastError(activity.getString(R.string.evolve_import_failed,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
            }
        });
    }

    /** Writes the plan where it goes and returns what undoes it. */
    private static Undo write(SharedBlock post, @Nullable String scId, Plan plan) throws Exception {
        switch (plan.target()) {
            case BLOCK_COLLECTION -> {
                ArrayList<BlockBean> blocks = SharedBlockCodec.decode(post.blocksJson());
                if (blocks == null) throw new IOException("damaged");
                Mp.h().a(plan.name(), blocks, true);
                String name = plan.name();
                return new Undo(name, () -> {
                    Mp.h().a(name, false);
                    Mp.h().e();
                });
            }
            case MOREBLOCK_COLLECTION -> {
                ArrayList<BlockBean> blocks = SharedBlockCodec.decode(post.blocksJson());
                if (blocks == null) throw new IOException("damaged");
                Pp.h().a(plan.name(), post.spec(), blocks, true);
                String name = plan.name();
                return new Undo(name, () -> {
                    Pp.h().a(name, false);
                    Pp.h().e();
                });
            }
            case LAYOUT_FILE -> {
                File dir = EvolveProjectContext.layoutDir(scId);
                String previous = ImportFiles.write(dir, plan.name(), plan.content());
                String name = plan.name();
                return new Undo(name, () -> ImportFiles.restore(dir, name, previous));
            }
            case JAVA_FILE, KOTLIN_FILE -> {
                File dir = EvolveProjectContext.javaDir(scId);
                String previous = ImportFiles.write(dir, plan.name(), plan.content());
                String name = plan.name();
                return new Undo(name, () -> ImportFiles.restore(dir, name, previous));
            }
            default -> {
                File list = EvolveProjectContext.componentList();
                String previous = ImportFiles.mergeComponents(list, plan.content(), plan.replaces());
                return new Undo(plan.name(), () -> ImportFiles.restoreList(list, previous));
            }
        }
    }

    private static void announce(Activity activity, SharedBlock post, Plan plan, Undo undo) {
        View anchor = activity.findViewById(android.R.id.content);
        String message = activity.getString(R.string.evolve_import_done, undo.label());
        if (plan.target() == Target.BLOCK_COLLECTION) message += "\n" + activity.getString(R.string.evolve_import_blocks_hint);
        if (plan.target() == Target.MOREBLOCK_COLLECTION) message += "\n" + activity.getString(R.string.evolve_import_moreblocks_hint);
        Snackbar snackbar = Snackbar.make(anchor, message, 10_000).setTextMaxLines(4);
        snackbar.setAction(R.string.evolve_import_undo, v -> Executors.newSingleThreadExecutor().execute(() -> {
            try {
                undo.action().run();
                activity.runOnUiThread(() -> SketchwareUtil.toast(activity.getString(R.string.evolve_import_undone)));
            } catch (Exception e) {
                activity.runOnUiThread(() -> SketchwareUtil.toastError(activity.getString(R.string.evolve_import_undo_failed, e.getMessage())));
            }
        }));
        snackbar.show();
    }
}
