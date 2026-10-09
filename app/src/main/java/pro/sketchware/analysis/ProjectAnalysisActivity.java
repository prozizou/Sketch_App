package pro.sketchware.analysis;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityProjectAnalysisBinding;
import pro.sketchware.databinding.ItemFindingBinding;
import pro.sketchware.settings.AppLog;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * The project's health: a score, the problems found by the compatibility, security and dependency checks,
 * and the dependency tree. Opened from the editor, whose loaded project data it reads.
 */
public class ProjectAnalysisActivity extends BaseAppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final FindingsAdapter adapter = new FindingsAdapter();
    private ActivityProjectAnalysisBinding binding;
    private String scId;
    private AnalysisReport report;
    private ProjectFactsLoader.Loaded loaded;
    private Category filter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ActivityProjectAnalysisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        scId = getIntent().getStringExtra("sc_id");
        if (scId == null) {
            finish();
            return;
        }

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        UI.addSystemWindowInsetToPadding(binding.findings, false, false, false, true);
        binding.findings.setLayoutManager(new LinearLayoutManager(this));
        binding.findings.setAdapter(adapter);

        Menu menu = binding.topAppBar.getMenu();
        menu.add(R.string.analysis_menu_tree).setOnMenuItemClickListener(item -> {
            showDependencyTree();
            return true;
        });
        menu.add(R.string.analysis_menu_navigation).setOnMenuItemClickListener(item -> {
            showNavigation();
            return true;
        });
        menu.add(R.string.analysis_menu_rerun).setOnMenuItemClickListener(item -> {
            analyse();
            return true;
        });

        addFilter(getString(R.string.analysis_filter_all), null, true);
        addFilter(getString(R.string.analysis_cat_compatibility), Category.COMPATIBILITY, false);
        addFilter(getString(R.string.analysis_cat_security), Category.SECURITY, false);
        addFilter(getString(R.string.analysis_cat_dependencies), Category.DEPENDENCIES, false);
        addFilter(getString(R.string.analysis_cat_quality), Category.QUALITY, false);

        analyse();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void addFilter(String label, Category category, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setOnClickListener(v -> {
            filter = category;
            showFindings();
        });
        binding.filters.addView(chip);
    }

    private void analyse() {
        binding.progress.setVisibility(View.VISIBLE);
        try {
            executor.execute(() -> {
                try {
                    ProjectFactsLoader.Loaded result = ProjectFactsLoader.load(scId);
                    AnalysisReport analysed = ProjectAnalyzer.run(result.facts(), result.files(), result.libraryNames(), result.classesByLibrary(),
                            result.logicScreens());
                    runOnUiThread(() -> {
                        loaded = result;
                        report = analysed;
                        binding.progress.setVisibility(View.INVISIBLE);
                        showReport();
                    });
                } catch (Throwable t) {
                    AppLog.e("ProjectAnalysis", "Analysis failed: " + t);
                    runOnUiThread(() -> {
                        binding.progress.setVisibility(View.INVISIBLE);
                        new MaterialAlertDialogBuilder(this)
                                .setMessage(getString(R.string.analysis_failed, t.getMessage() != null ? t.getMessage() : t.toString()))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            // The screen is gone
        }
    }

    private void showReport() {
        binding.score.setText(String.valueOf(report.score()));
        binding.summary.setText(getString(R.string.analysis_summary, report.count(Severity.ERROR), report.count(Severity.WARNING), report.count(Severity.INFO)));

        List<Finding> priority = report.priorityFixes(3);
        if (priority.isEmpty()) {
            binding.priority.setVisibility(View.GONE);
        } else {
            StringBuilder text = new StringBuilder(getString(R.string.analysis_fix_first));
            for (Finding finding : priority) text.append("\n• ").append(finding.title());
            binding.priority.setText(text);
            binding.priority.setVisibility(View.VISIBLE);
        }
        showFindings();
    }

    private void showFindings() {
        List<Finding> shown = filter == null ? report.findings() : report.findings(filter);
        adapter.submit(shown);
        binding.empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showDependencyTree() {
        if (loaded == null) return;
        List<DependencyInspector.Node> tree = DependencyInspector.tree(loaded.builtInGraph(), loaded.builtInRoots());
        if (tree.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.analysis_tree_title).setMessage(R.string.analysis_tree_empty)
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        StringBuilder text = new StringBuilder();
        for (DependencyInspector.Node node : tree) {
            text.append("  ".repeat(node.depth())).append(node.name());
            if (loaded.excludedBuiltIn().contains(node.name())) text.append("  [").append(getString(R.string.analysis_tree_excluded)).append(']');
            if (node.repeated()) text.append("  (").append(getString(R.string.analysis_tree_repeated)).append(')');
            text.append('\n');
        }
        TextView view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(11);
        view.setTextIsSelectable(true);
        view.setText(text);
        int padding = SketchwareUtil.dpToPx(16);
        view.setPadding(padding, padding / 2, padding, padding / 2);
        android.widget.HorizontalScrollView horizontal = new android.widget.HorizontalScrollView(this);
        horizontal.addView(view);
        android.widget.ScrollView vertical = new android.widget.ScrollView(this);
        vertical.addView(horizontal);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.analysis_tree_title).setView(vertical).setPositiveButton(android.R.string.ok, null).show();
    }

    /** Which screen opens which, from the Intent blocks and the project's Java files, with a Mermaid copy. */
    private void showNavigation() {
        if (loaded == null) return;
        java.util.Map<String, String> javaFiles = new java.util.LinkedHashMap<>();
        for (SourceFile source : loaded.facts().sources()) {
            if (source.name().endsWith(".java")) javaFiles.put(source.name(), source.content());
        }
        pro.sketchware.logic.NavigationGraph graph = pro.sketchware.logic.NavigationGraph.build(loaded.logicScreens(), javaFiles);
        StringBuilder text = new StringBuilder();
        java.util.List<String> unreachable = graph.unreachable();
        for (String screen : graph.screens()) {
            text.append(screen);
            if (pro.sketchware.logic.NavigationGraph.isFragment(screen)) text.append("  (").append(getString(R.string.analysis_nav_fragment)).append(')');
            if (unreachable.contains(screen)) text.append("  [").append(getString(R.string.analysis_nav_unreachable)).append(']');
            text.append('\n');
            for (pro.sketchware.logic.NavigationGraph.Edge edge : graph.edgesFrom(screen)) {
                text.append("   -> ").append(edge.to()).append("   ").append(edge.fromJava() ? "Java: " : "").append(edge.via()).append('\n');
            }
        }
        if (graph.screens().isEmpty()) text.append(getString(R.string.analysis_nav_empty));
        TextView view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(11);
        view.setTextIsSelectable(true);
        view.setText(text);
        int padding = SketchwareUtil.dpToPx(16);
        view.setPadding(padding, padding / 2, padding, padding / 2);
        android.widget.HorizontalScrollView horizontal = new android.widget.HorizontalScrollView(this);
        horizontal.addView(view);
        android.widget.ScrollView vertical = new android.widget.ScrollView(this);
        vertical.addView(horizontal);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.analysis_nav_title).setView(vertical)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.analysis_nav_copy_mermaid, (dialog, which) -> {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("navigation", graph.toMermaid()));
                    }
                })
                .show();
    }

    private String categoryLabel(Category category) {
        return getString(switch (category) {
            case COMPATIBILITY -> R.string.analysis_cat_compatibility;
            case SECURITY -> R.string.analysis_cat_security;
            case DEPENDENCIES -> R.string.analysis_cat_dependencies;
            case BUILD -> R.string.analysis_cat_build;
            case QUALITY -> R.string.analysis_cat_quality;
        });
    }

    private int severityColor(Severity severity) {
        return switch (severity) {
            case ERROR -> MaterialColors.getColor(this, R.attr.colorError, 0);
            case WARNING -> MaterialColors.getColor(this, R.attr.colorAmber, 0);
            case INFO -> MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant, 0);
        };
    }

    private class FindingsAdapter extends RecyclerView.Adapter<FindingsAdapter.ViewHolder> {
        private List<Finding> items = new ArrayList<>();

        void submit(List<Finding> findings) {
            items = new ArrayList<>(findings);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemFindingBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Finding finding = items.get(position);
            holder.item.title.setText(finding.title());
            holder.item.title.setTextColor(severityColor(finding.severity()));
            holder.item.subtitle.setText(categoryLabel(finding.category()) + " · " + finding.cause());
            holder.itemView.setOnClickListener(v -> FindingsDialog.show(ProjectAnalysisActivity.this, finding.title(), List.of(finding), ""));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ItemFindingBinding item;

            ViewHolder(ItemFindingBinding item) {
                super(item.getRoot());
                this.item = item;
            }
        }
    }
}
