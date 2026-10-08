package pro.sketchware.activities.search;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.PatternSyntaxException;

import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.search.ProjectSearch.Match;
import pro.sketchware.databinding.ActivityProjectSearchBinding;
import pro.sketchware.databinding.ItemProjectSearchResultBinding;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.UI;

/**
 * Searches all Java, resource and asset files of a project and opens a result in the code editor.
 */
public class ProjectSearchActivity extends BaseAppCompatActivity {
    private static final long SEARCH_DELAY_MS = 300;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final ResultsAdapter adapter = new ResultsAdapter();
    private ActivityProjectSearchBinding binding;
    private File projectFiles;
    private final Runnable startSearch = this::search;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityProjectSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String scId = getIntent().getStringExtra("sc_id");
        if (scId == null) {
            finish();
            return;
        }
        // .../data/<id>/files, which holds java, resource, assets, manifest, ...
        projectFiles = new File(new FilePathUtil().getPathJava(scId)).getParentFile();

        UI.addSystemWindowInsetToPadding(binding.results, false, false, false, true);
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.results.setLayoutManager(new LinearLayoutManager(this));
        binding.results.setAdapter(adapter);

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch();
            }
        });
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.searchInput.removeCallbacks(startSearch);
                search();
                return true;
            }
            return false;
        });
        for (var chip : new com.google.android.material.chip.Chip[]{binding.chipCase, binding.chipWord, binding.chipRegex}) {
            chip.setOnCheckedChangeListener((button, checked) -> scheduleSearch());
        }

        showMessage(getString(R.string.project_search_idle));
        binding.searchInput.requestFocus();
    }

    @Override
    protected void onDestroy() {
        generation.incrementAndGet();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void scheduleSearch() {
        binding.searchInput.removeCallbacks(startSearch);
        binding.searchInput.postDelayed(startSearch, SEARCH_DELAY_MS);
    }

    private void search() {
        String query = String.valueOf(binding.searchInput.getText());
        int id = generation.incrementAndGet();
        binding.searchInputLayout.setError(null);
        if (query.isEmpty()) {
            binding.progress.setVisibility(View.INVISIBLE);
            adapter.submit(new ArrayList<>());
            showMessage(getString(R.string.project_search_idle));
            return;
        }

        ProjectSearch searcher;
        try {
            searcher = new ProjectSearch(query, binding.chipCase.isChecked(), binding.chipRegex.isChecked(), binding.chipWord.isChecked());
        } catch (PatternSyntaxException e) {
            binding.progress.setVisibility(View.INVISIBLE);
            binding.searchInputLayout.setError(getString(R.string.project_search_invalid_regex));
            return;
        }

        binding.progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            ProjectSearch.Result result = searcher.search(projectFiles, () -> generation.get() != id);
            runOnUiThread(() -> {
                if (generation.get() != id) return; // A newer search replaced this one
                binding.progress.setVisibility(View.INVISIBLE);
                adapter.submit(result.matches());
                if (result.matches().isEmpty()) {
                    showMessage(getString(R.string.project_search_no_results, result.filesSearched()));
                } else {
                    long files = result.matches().stream().map(Match::file).distinct().count();
                    String summary = getString(R.string.project_search_summary, result.matches().size(), files);
                    if (result.truncated()) {
                        summary += " · " + getString(R.string.project_search_summary_truncated, ProjectSearch.MAX_RESULTS);
                    }
                    binding.summary.setText(summary);
                }
            });
        });
    }

    private void showMessage(String message) {
        binding.summary.setText(message);
    }

    private String relativePath(File file) {
        String path = file.getAbsolutePath();
        String base = projectFiles.getAbsolutePath() + File.separator;
        return path.startsWith(base) ? path.substring(base.length()) : path;
    }

    private void open(Match match) {
        Intent intent = new Intent(this, SrcCodeEditor.class);
        intent.putExtra("title", match.file().getName());
        intent.putExtra("content", match.file().getAbsolutePath());
        intent.putExtra(SrcCodeEditor.EXTRA_LINE, match.line());
        intent.putExtra(SrcCodeEditor.EXTRA_COLUMN, match.column());
        startActivity(intent);
    }

    private class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.ViewHolder> {
        private List<Match> matches = new ArrayList<>();

        void submit(List<Match> newMatches) {
            matches = newMatches;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemProjectSearchResultBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Match match = matches.get(position);
            holder.item.location.setText(relativePath(match.file()) + ":" + match.line());
            holder.item.preview.setText(match.lineText());
            holder.itemView.setOnClickListener(v -> open(match));
        }

        @Override
        public int getItemCount() {
            return matches.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ItemProjectSearchResultBinding item;

            ViewHolder(ItemProjectSearchResultBinding item) {
                super(item.getRoot());
                this.item = item;
            }
        }
    }
}
