package pro.sketchware.logic;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Steps through the result of {@link BlockSimulator}: one statement at a time, with the state of every variable
 * and list, the toasts shown so far, and the block flashed in the editor when it can be found.
 */
public final class BlockDebuggerDialog {
    private BlockDebuggerDialog() {
    }

    /** @param blockFinder finds the editor's view of a block by id, or returns null */
    public static void show(Activity activity, LogicEvent event, Map<String, Integer> variables, List<String> lists,
                            Function<String, View> blockFinder) {
        Map<String, List<Object>> startLists = new java.util.LinkedHashMap<>();
        for (String list : lists) startLists.put(list, new java.util.ArrayList<>());
        BlockSimulator.Result result = BlockSimulator.run(event, BlockSimulator.initialValues(variables), startLists,
                BlockSimulator.DEFAULT_MAX_STEPS);
        if (result.steps().isEmpty()) {
            new MaterialAlertDialogBuilder(activity).setTitle(R.string.block_debugger_title)
                    .setMessage(R.string.block_debugger_empty).setPositiveButton(android.R.string.ok, null).show();
            return;
        }

        int padding = SketchwareUtil.dpToPx(20);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, 0);
        TextView position = new TextView(activity);
        TextView description = new TextView(activity);
        description.setTypeface(Typeface.DEFAULT_BOLD);
        description.setTextSize(15);
        TextView note = new TextView(activity);
        note.setTextSize(12);
        TextView state = new TextView(activity);
        state.setTypeface(Typeface.MONOSPACE);
        state.setTextSize(12);
        state.setTextIsSelectable(true);
        state.setPadding(0, padding / 2, 0, padding / 2);
        TextView disclaimer = new TextView(activity);
        disclaimer.setTextSize(11);
        disclaimer.setText(R.string.block_debugger_disclaimer);
        content.addView(position);
        content.addView(description);
        content.addView(note);
        content.addView(state);
        content.addView(disclaimer);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);

        int[] index = {0};
        Runnable render = () -> {
            BlockSimulator.Step step = result.steps().get(index[0]);
            position.setText(activity.getString(R.string.block_debugger_position, index[0] + 1, result.steps().size()));
            description.setText(step.description());
            StringBuilder notes = new StringBuilder();
            if (!step.simulated()) notes.append(activity.getString(R.string.block_debugger_not_simulated));
            if (index[0] == result.steps().size() - 1) {
                if (notes.length() > 0) notes.append('\n');
                notes.append(result.finished() ? activity.getString(R.string.block_debugger_finished) : result.stopReason());
            }
            note.setText(notes);
            note.setVisibility(notes.length() == 0 ? View.GONE : View.VISIBLE);
            StringBuilder values = new StringBuilder();
            step.state().forEach((name, value) -> values.append(name).append(" = ").append(value).append('\n'));
            if (values.length() == 0) values.append(activity.getString(R.string.block_debugger_no_variables));
            state.setText(values.toString().trim());
            View block = blockFinder.apply(step.blockId());
            if (block != null) {
                block.animate().cancel();
                block.setAlpha(0.25f);
                block.animate().alpha(1f).setDuration(450).start();
            }
        };

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.block_debugger_title)
                .setView(scroll)
                .setPositiveButton(R.string.block_debugger_next, null)
                .setNeutralButton(R.string.block_debugger_previous, null)
                .setNegativeButton(R.string.common_word_close, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button next = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button previous = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            Runnable update = () -> {
                render.run();
                next.setEnabled(index[0] < result.steps().size() - 1);
                previous.setEnabled(index[0] > 0);
            };
            next.setOnClickListener(v -> {
                if (index[0] < result.steps().size() - 1) index[0]++;
                update.run();
            });
            previous.setOnClickListener(v -> {
                if (index[0] > 0) index[0]--;
                update.run();
            });
            update.run();
        });
        dialog.show();
    }
}
