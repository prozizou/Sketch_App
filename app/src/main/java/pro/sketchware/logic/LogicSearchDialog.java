package pro.sketchware.logic;

import android.app.Activity;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.editor.LogicEditorActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.analysis.Location;
import pro.sketchware.databinding.DialogSettingsSearchBinding;

/**
 * Searches the blocks and events of the screen being edited (a variable, a value, any word of a block) and opens
 * the event of a result in the Logic editor, with the block found flashing.
 */
public final class LogicSearchDialog {

    private static final int MAX_RESULTS = 200;

    private LogicSearchDialog() {
    }

    public static void show(Activity activity, String scId, ProjectFileBean file) {
        LogicScreen screen = LogicFactsLoader.loadScreen(scId, file);
        String screenName = file.getJavaName().replace(".java", "");

        DialogSettingsSearchBinding binding = DialogSettingsSearchBinding.inflate(activity.getLayoutInflater());
        binding.searchInputLayout.setHint(activity.getString(R.string.logic_search_hint, screenName));
        List<LogicSearch.Hit> shown = new ArrayList<>();
        ArrayAdapter<LogicSearch.Hit> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_2,
                android.R.id.text1, shown) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                LogicSearch.Hit hit = getItem(position);
                TextView title = row.findViewById(android.R.id.text1);
                TextView detail = row.findViewById(android.R.id.text2);
                title.setText(hit.text());
                detail.setText(hit.isEvent() ? activity.getString(R.string.logic_search_event)
                        : LogicSearch.eventLabel(hit.eventKey()));
                return row;
            }
        };
        binding.searchResults.setAdapter(adapter);
        binding.searchResults.setEmptyView(binding.searchEmpty);
        binding.searchEmpty.setText(R.string.logic_search_intro);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(binding.getRoot())
                .setNegativeButton(R.string.common_word_close, null)
                .create();
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                String query = text.toString();
                shown.clear();
                shown.addAll(LogicSearch.search(screen, query, MAX_RESULTS));
                binding.searchEmpty.setText(query.trim().isEmpty() ? activity.getString(R.string.logic_search_intro)
                        : activity.getString(R.string.logic_search_nothing, query.trim()));
                adapter.notifyDataSetChanged();
            }
        });
        binding.searchResults.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            open(activity, scId, file, shown.get(position));
        });
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            binding.searchInput.requestFocus();
        });
        dialog.show();
    }

    /** Opens the event in the Logic editor, the same way the project health screen does. */
    private static void open(Activity activity, String scId, ProjectFileBean file, LogicSearch.Hit hit) {
        Location location = Location.event(file.getJavaName(), hit.eventKey(), hit.blockId());
        Intent intent = new Intent(activity, LogicEditorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sc_id", scId);
        intent.putExtra("id", location.eventTarget());
        intent.putExtra("event", location.eventName());
        intent.putExtra("project_file", file);
        intent.putExtra("event_text", location.eventName());
        if (!hit.isEvent()) {
            intent.putExtra(LogicEditorActivity.EXTRA_HIGHLIGHT_BLOCK, hit.blockId());
        }
        activity.startActivity(intent);
    }
}
