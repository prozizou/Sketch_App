package pro.sketchware.evolve;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

/** Actions on a post that every screen of the community page offers the same way: share, report, confirm a delete. */
public final class EvolveActions {

    private EvolveActions() {
    }

    /** Sends the post's title, kind and author to another app (a text, never the code itself). */
    public static void share(Context context, SharedBlock post) {
        String text = context.getString(R.string.evolve_share_text, post.name(), context.getString(EvolvePostCard.kindBadge(post.kind())),
                EvolveCard.authorName(context, post));
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(send, context.getString(R.string.evolve_menu_share)));
    }

    /** Asks why, then files the report; the console is where reports are read. */
    public static void report(Context context, String uid, SharedBlock post) {
        String[] reasons = {
                context.getString(R.string.evolve_report_inappropriate),
                context.getString(R.string.evolve_report_malicious),
                context.getString(R.string.evolve_report_spam),
                context.getString(R.string.evolve_report_other)
        };
        String[] keys = {"inappropriate", "malicious", "spam", "other"};
        Context app = context.getApplicationContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.evolve_report_title)
                .setItems(reasons, (d, which) -> EvolveRepository.report(post.id(), uid, keys[which], app.getString(R.string.evolve_timeout),
                        new EvolveRepository.Callback<>() {
                            @Override
                            public void onSuccess(Void value) {
                                SketchwareUtil.toast(app.getString(R.string.evolve_report_dialog_done));
                            }

                            @Override
                            public void onError(@NonNull String message) {
                                SketchwareUtil.toastError(app.getString(R.string.evolve_report_failed, message));
                            }
                        }))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    /** Always asks before a post goes: it takes its comments with it. */
    public static void confirmDelete(Context context, SharedBlock post, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.evolve_delete_title)
                .setMessage(context.getString(R.string.evolve_delete_message, post.name()))
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.evolve_delete, (d, w) -> onConfirm.run())
                .show();
    }
}
