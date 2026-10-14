package pro.sketchware.evolve;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.text.Editable;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

import pro.sketchware.BuildConfig;
import pro.sketchware.R;
import pro.sketchware.auth.AuthGate;
import pro.sketchware.databinding.EvolveShareDialogBinding;
import pro.sketchware.evolve.SharedBlockRules.Problem;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Publishing to Evolve from anywhere in the app: the name and description form, the checks, the upload. Used by the
 * Evolve sheet and by the places that offer to share right after the user saved something.
 */
public final class EvolveShare {

    private EvolveShare() {
    }

    /** @return the activity behind {@code context}, or null */
    @Nullable
    public static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper wrapper) {
            if (context instanceof Activity activity) {
                return activity;
            }
            context = wrapper.getBaseContext();
        }
        return null;
    }

    /** @return whether Evolve can be used: a build with Firebase and a signed-in user */
    public static boolean available(Context context) {
        return AuthGate.isFirebaseReady(context) && AuthGate.currentUser(context) != null;
    }

    /**
     * Right after the user saved {@code source} somewhere, asks whether to share it on Evolve too. Says nothing when
     * Evolve isn't available (no Firebase in this build, not signed in): the save already happened and is enough.
     */
    public static void offer(Context context, ShareSource source) {
        Activity activity = activityOf(context);
        if (activity == null || activity.isFinishing() || !available(activity)) {
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.evolve_offer_title)
                .setMessage(activity.getString(R.string.evolve_offer_message, source.name()))
                .setNegativeButton(R.string.evolve_offer_no, null)
                .setPositiveButton(R.string.evolve_offer_yes, (d, w) -> share(activity, source, null))
                .show();
    }

    @StringRes
    static int problemText(Problem problem) {
        return switch (problem) {
            case NAME_EMPTY -> R.string.evolve_problem_name_empty;
            case NAME_TOO_LONG -> R.string.evolve_problem_name_too_long;
            case NAME_INVALID -> R.string.evolve_problem_name_invalid;
            case DESCRIPTION_TOO_LONG -> R.string.evolve_problem_description_too_long;
            case NO_BLOCKS -> R.string.evolve_problem_no_blocks;
            case TOO_MANY_BLOCKS -> R.string.evolve_problem_too_many_blocks;
            case XML_EMPTY -> R.string.evolve_problem_xml_empty;
            case XML_INVALID -> R.string.evolve_problem_xml_invalid;
            case TOO_LARGE, NONE -> R.string.evolve_problem_too_large;
        };
    }

    /** Asks for a name and a description, then publishes {@code source}; {@code onDone} runs after a successful upload. */
    public static void share(Activity activity, ShareSource source, @Nullable Runnable onDone) {
        if (activity.isFinishing()) return;
        EvolveShareDialogBinding form = EvolveShareDialogBinding.inflate(activity.getLayoutInflater());
        form.shareName.setText(source.name());
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.evolve_share_title)
                .setView(form.getRoot())
                .setPositiveButton(R.string.evolve_share_publish, null)
                .setNegativeButton(R.string.common_word_cancel, null)
                .create();
        dialog.show();
        Button publish = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Runnable validate = () -> {
            Problem name = SharedBlockRules.checkName(String.valueOf(form.shareName.getText()));
            Problem description = SharedBlockRules.checkDescription(String.valueOf(form.shareDescription.getText()));
            form.shareNameLayout.setError(name == Problem.NONE ? null : activity.getString(problemText(name)));
            form.shareDescriptionLayout.setError(description == Problem.NONE ? null : activity.getString(problemText(description)));
            publish.setEnabled(name == Problem.NONE && description == Problem.NONE);
        };
        BaseTextWatcher watcher = new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                validate.run();
            }
        };
        form.shareName.addTextChangedListener(watcher);
        form.shareDescription.addTextChangedListener(watcher);
        validate.run();
        publish.setOnClickListener(v -> upload(activity, dialog, publish, source,
                String.valueOf(form.shareName.getText()).trim(), String.valueOf(form.shareDescription.getText()).trim(), onDone));
    }

    /** @return the entry to publish for {@code source}, or null after telling the user why it can't be shared */
    @Nullable
    private static SharedBlock build(Context context, ShareSource source, String name, String description, FirebaseUser user) {
        String author = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? context.getString(R.string.evolve_anonymous) : user.getDisplayName();
        if (source.kind() == SharedKind.VIEW) {
            SharedXml.Result result = SharedXml.check(source.xml());
            if (result.problem() != Problem.NONE) {
                SketchwareUtil.toastError(context.getString(problemText(result.problem())));
                return null;
            }
            return new SharedBlock("", name, description, user.getUid(), author, 0, BuildConfig.VERSION_CODE,
                    result.elements(), List.of(), false, 0, "", SharedKind.VIEW, "", source.xml());
        }
        var blocks = source.blocks();
        String json = SharedBlockCodec.encode(blocks);
        Problem problem = SharedBlockRules.checkStack(blocks.size(), json);
        if (problem != Problem.NONE) {
            SketchwareUtil.toastError(context.getString(problemText(problem)));
            return null;
        }
        List<String> opCodes = SharedBlockRules.distinctOpCodes(SharedBlockCodec.opCodes(blocks));
        return new SharedBlock("", name, description, user.getUid(), author, 0, BuildConfig.VERSION_CODE,
                blocks.size(), opCodes, SharedBlockRules.usesRawCode(opCodes), 0, json, source.kind(), source.spec(), "");
    }

    private static void upload(Activity activity, AlertDialog dialog, Button publishButton, ShareSource source, String name,
                               String description, @Nullable Runnable onDone) {
        FirebaseUser user = AuthGate.currentUser(activity);
        if (user == null) {
            SketchwareUtil.toastError(activity.getString(R.string.evolve_unavailable));
            return;
        }
        SharedBlock block = build(activity, source, name, description, user);
        if (block == null) {
            return;
        }
        Context app = activity.getApplicationContext();
        publishButton.setEnabled(false);
        EvolveRepository.publish(block, app.getString(R.string.evolve_timeout), new EvolveRepository.Callback<>() {
            @Override
            public void onSuccess(String id) {
                if (dialog.isShowing()) dialog.dismiss();
                EvolveCache.clear();
                SketchwareUtil.toast(app.getString(R.string.evolve_share_done, name));
                if (onDone != null) onDone.run();
            }

            @Override
            public void onError(@NonNull String message) {
                if (dialog.isShowing()) {
                    publishButton.setEnabled(true);
                }
                SketchwareUtil.toastError(app.getString(R.string.evolve_share_failed, message));
            }
        });
    }
}
