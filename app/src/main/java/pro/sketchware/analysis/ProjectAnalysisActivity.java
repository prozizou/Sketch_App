package pro.sketchware.analysis;

import android.content.Intent;
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

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.jC;
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
    /** Result extras: the screen to show in the editor, and the widget to select on it. */
    public static final String EXTRA_PROJECT_FILE = "project_file";
    public static final String EXTRA_SELECT_WIDGET = "select_widget";
    /** Result extra: the auto-fix changed the project's data, so the editor must reload the screen and save. */
    public static final String EXTRA_PROJECT_CHANGED = "project_changed";

    private boolean projectChanged;

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
        if (pro.sketchware.flags.FeatureFlags.isEnabled(pro.sketchware.flags.FeatureFlag.AUTO_FIX)) {
            menu.add(R.string.analysis_autofix).setShowAsActionFlags(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                    .setOnMenuItemClickListener(item -> {
                        showAutoFix();
                        return true;
                    });
        }
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

    /**
     * Lists what can be fixed without asking, one switch per kind of fix, and applies the chosen ones after taking a
     * snapshot. What needs a decision is counted and left alone.
     */
    private void showAutoFix() {
        if (loaded == null || report == null) return;
        pro.sketchware.analysis.fix.AutoFix.Plan plan = pro.sketchware.analysis.fix.AutoFix.plan(
                loaded.facts().views(), loaded.logicScreens());
        int needYou = pro.sketchware.analysis.fix.AutoFix.notFixable(report.findings()).size();
        if (plan.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.analysis_autofix)
                    .setMessage(getString(R.string.analysis_autofix_nothing, needYou))
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        int pad = SketchwareUtil.dpToPx(20);
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, 0);
        TextView intro = new TextView(this);
        intro.setText(getString(R.string.analysis_autofix_intro, needYou));
        content.addView(intro);
        java.util.Map<pro.sketchware.analysis.fix.AutoFix.Rule, android.widget.CheckBox> boxes = new java.util.EnumMap<>(pro.sketchware.analysis.fix.AutoFix.Rule.class);
        for (pro.sketchware.analysis.fix.AutoFix.Rule rule : pro.sketchware.analysis.fix.AutoFix.Rule.values()) {
            int count = plan.count(rule);
            if (count == 0) continue;
            android.widget.CheckBox box = new android.widget.CheckBox(this);
            box.setText(getString(autoFixLabel(rule), count));
            box.setChecked(rule.onByDefault);
            boxes.put(rule, box);
            content.addView(box);
        }
        TextView details = new TextView(this);
        details.setText(R.string.analysis_autofix_details);
        details.setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0));
        details.setPadding(0, pad / 2, 0, 0);
        details.setOnClickListener(v -> showAutoFixDetails(plan));
        content.addView(details);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.analysis_autofix).setView(scroll)
                .setPositiveButton(R.string.analysis_autofix_apply, (dialog, which) -> {
                    java.util.Set<pro.sketchware.analysis.fix.AutoFix.Rule> chosen = java.util.EnumSet.noneOf(pro.sketchware.analysis.fix.AutoFix.Rule.class);
                    boxes.forEach((rule, box) -> {
                        if (box.isChecked()) chosen.add(rule);
                    });
                    if (!chosen.isEmpty()) applyAutoFix(plan.only(chosen), needYou);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private int autoFixLabel(pro.sketchware.analysis.fix.AutoFix.Rule rule) {
        return switch (rule) {
            case CONTENT_DESCRIPTION -> R.string.analysis_autofix_content_description;
            case TOUCH_TARGET -> R.string.analysis_autofix_touch_target;
            case SMALL_TEXT -> R.string.analysis_autofix_small_text;
            case CONTRAST -> R.string.analysis_autofix_contrast;
            case SPACING -> R.string.analysis_autofix_spacing;
            case TEXT_SCALE -> R.string.analysis_autofix_text_scale;
            case UNCONNECTED_BLOCKS -> R.string.analysis_autofix_unconnected_blocks;
        };
    }

    /** Every change the plan would make, one per line. */
    private void showAutoFixDetails(pro.sketchware.analysis.fix.AutoFix.Plan plan) {
        StringBuilder text = new StringBuilder();
        for (pro.sketchware.analysis.fix.AutoFix.WidgetChange change : plan.widgetChanges()) {
            text.append(change.describe()).append('\n');
        }
        for (pro.sketchware.analysis.fix.AutoFix.BlockRemoval removal : plan.blockRemovals()) {
            text.append(getString(R.string.analysis_autofix_remove_blocks, removal.javaName().replace(".java", ""),
                    removal.eventKey(), removal.blockIds().size())).append('\n');
        }
        TextView view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(11);
        view.setTextIsSelectable(true);
        view.setText(text);
        int padding = SketchwareUtil.dpToPx(16);
        view.setPadding(padding, padding / 2, padding, padding / 2);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(view);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.analysis_autofix_details).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void applyAutoFix(pro.sketchware.analysis.fix.AutoFix.Plan plan, int needYou) {
        binding.progress.setVisibility(View.VISIBLE);
        try {
            executor.execute(() -> {
                try {
                    pro.sketchware.analysis.fix.AutoFixApplier.Result result = pro.sketchware.analysis.fix.AutoFixApplier.apply(scId, plan);
                    runOnUiThread(() -> {
                        projectChanged = true;
                        setResult(RESULT_OK, new Intent().putExtra(EXTRA_PROJECT_CHANGED, true));
                        new MaterialAlertDialogBuilder(this).setTitle(R.string.analysis_autofix)
                                .setMessage(getString(R.string.analysis_autofix_done, result.applied(), needYou)
                                        + (result.missing() > 0 ? "\n\n" + getString(R.string.analysis_autofix_missing, result.missing()) : ""))
                                .setPositiveButton(android.R.string.ok, null).show();
                        analyse();
                    });
                } catch (Throwable t) {
                    AppLog.e("ProjectAnalysis", "Auto-fix failed: " + t);
                    runOnUiThread(() -> {
                        binding.progress.setVisibility(View.INVISIBLE);
                        new MaterialAlertDialogBuilder(this)
                                .setMessage(getString(R.string.analysis_autofix_failed, t.getMessage() != null ? t.getMessage() : t.toString()))
                                .setPositiveButton(android.R.string.ok, null).show();
                    });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            // The screen is gone
        }
    }

    /** One location opens at once; several are offered in a list. */
    private void openFirstOrChoose(Finding finding) {
        List<Location> locations = finding.locations();
        if (locations.size() == 1) {
            open(locations.get(0));
            return;
        }
        String[] labels = new String[locations.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = locations.get(i).label();
        new MaterialAlertDialogBuilder(this).setTitle(R.string.analysis_open_where)
                .setItems(labels, (dialog, which) -> open(locations.get(which)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /**
     * Opens what a finding is about: a widget or a screen goes back to the editor on that screen (and selects the
     * widget), a file opens in the code editor at the line, an event opens in the Logic editor.
     */
    private void open(Location location) {
        switch (location.kind()) {
            case WIDGET -> backToEditor(projectFile(location.file() + ".xml", true), location.target(), location);
            case SCREEN -> backToEditor(projectFile(location.file(), false), null, location);
            case SOURCE -> {
                java.io.File file = new java.io.File(location.file());
                if (!file.isFile()) {
                    SketchwareUtil.toast(getString(R.string.analysis_location_missing, location.label()));
                    return;
                }
                Intent intent = new Intent(this, mod.hey.studios.code.SrcCodeEditor.class);
                intent.putExtra("title", file.getName());
                intent.putExtra("content", file.getAbsolutePath());
                intent.putExtra(mod.hey.studios.code.SrcCodeEditor.EXTRA_LINE, location.line());
                startActivity(intent);
            }
            case EVENT -> {
                ProjectFileBean file = projectFile(location.file(), false);
                if (file == null) {
                    SketchwareUtil.toast(getString(R.string.analysis_location_missing, location.label()));
                    return;
                }
                Intent intent = new Intent(this, com.besome.sketch.editor.LogicEditorActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("sc_id", scId);
                intent.putExtra("id", location.eventTarget());
                intent.putExtra("event", location.eventName());
                intent.putExtra("project_file", file);
                intent.putExtra("event_text", location.eventName());
                if (!location.detail().isEmpty()) intent.putExtra(com.besome.sketch.editor.LogicEditorActivity.EXTRA_HIGHLIGHT_BLOCK, location.detail());
                startActivity(intent);
            }
        }
    }

    /** The screen or custom view whose layout ({@code byXml}) or Java file is {@code name}, or null. */
    private ProjectFileBean projectFile(String name, boolean byXml) {
        List<ProjectFileBean> files = new ArrayList<>();
        if (jC.b(scId).b() != null) files.addAll(jC.b(scId).b());
        if (byXml && jC.b(scId).c() != null) files.addAll(jC.b(scId).c());
        for (ProjectFileBean file : files) {
            if (name.equals(byXml ? file.getXmlName() : file.getJavaName())) return file;
        }
        return null;
    }

    /** Closes this screen and asks the editor to show {@code file}, selecting {@code widgetId} when given. */
    private void backToEditor(ProjectFileBean file, String widgetId, Location location) {
        if (file == null) {
            SketchwareUtil.toast(getString(R.string.analysis_location_missing, location.label()));
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_PROJECT_CHANGED, projectChanged);
        result.putExtra(EXTRA_PROJECT_FILE, file);
        if (widgetId != null) result.putExtra(EXTRA_SELECT_WIDGET, widgetId);
        setResult(RESULT_OK, result);
        finish();
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
            holder.itemView.setOnClickListener(v -> FindingsDialog.show(ProjectAnalysisActivity.this, finding.title(), List.of(finding), "",
                    ProjectAnalysisActivity.this::open));
            holder.item.open.setVisibility(finding.locations().isEmpty() ? View.GONE : View.VISIBLE);
            holder.item.open.setOnClickListener(v -> openFirstOrChoose(finding));
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
