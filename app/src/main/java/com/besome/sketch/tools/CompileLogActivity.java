package com.besome.sketch.tools;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.text.method.LinkMovementMethod;
import android.widget.PopupMenu;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.R;
import pro.sketchware.analysis.BuildDoctor;
import pro.sketchware.analysis.FindingsDialog;
import pro.sketchware.flags.FeatureFlag;
import pro.sketchware.flags.FeatureFlags;
import mod.hey.studios.util.CompileLogHelper;
import mod.hey.studios.util.Helper;
import mod.jbk.diagnostic.CompileDiagnosticParser;
import mod.jbk.diagnostic.CompileDiagnosticParser.Diagnostic;
import mod.jbk.diagnostic.CompileDiagnosticParser.Severity;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.util.AddMarginOnApplyWindowInsetsListener;
import pro.sketchware.databinding.CompileLogBinding;
import pro.sketchware.resolver.IconLibrary;
import pro.sketchware.resolver.MissingResource;
import pro.sketchware.resolver.MissingResourceParser;
import pro.sketchware.resolver.RebuildReport;
import pro.sketchware.resolver.RebuildRequest;
import pro.sketchware.resolver.ResolverFlow;
import pro.sketchware.resolver.ResolverSession;
import pro.sketchware.resolver.ui.ResolverEnvironment;
import pro.sketchware.resolver.ui.ResourceResolverSheet;
import pro.sketchware.utility.SketchwareUtil;

public class CompileLogActivity extends BaseAppCompatActivity implements ResourceResolverSheet.Host {

    private static final String PREFERENCE_WRAPPED_TEXT = "wrapped_text";
    private static final String PREFERENCE_USE_MONOSPACED_FONT = "use_monospaced_font";
    private static final String PREFERENCE_FONT_SIZE = "font_size";
    private CompileErrorSaver compileErrorSaver;
    private SharedPreferences logViewerPreferences;

    private CompileLogBinding binding;

    private String scId;
    private final ExecutorService resolverWork = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private List<MissingResource> missing = new ArrayList<>();
    private ResolverFlow resolverFlow;
    private IconLibrary resolverIcons;
    /** Bumped for each log read, so a slow preparation of an older log is ignored. */
    private int resolverGeneration;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = CompileLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.optionsLayout,
                new AddMarginOnApplyWindowInsetsListener(WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.CONSUMED));

        logViewerPreferences = getPreferences(Context.MODE_PRIVATE);

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        if (getIntent().getBooleanExtra("showingLastError", false)) {
            binding.topAppBar.setTitle("Last compile log");
        } else {
            binding.topAppBar.setTitle("Compile log");
        }

        String sc_id = getIntent().getStringExtra("sc_id");
        if (sc_id == null) {
            finish();
            return;
        }

        scId = sc_id;
        compileErrorSaver = new CompileErrorSaver(sc_id);

        if (compileErrorSaver.logFileExists()) {
            binding.clearButton.setOnClickListener(v -> {
                if (compileErrorSaver.logFileExists()) {
                    compileErrorSaver.deleteSavedLogs();
                    SketchwareUtil.toast("Compile logs have been cleared.");
                } else {
                    SketchwareUtil.toast("No compile logs found.");
                }

                setErrorText();
            });
        }

        final String wrapTextLabel = "Wrap text";
        final String monospacedFontLabel = "Monospaced font";
        final String fontSizeLabel = "Font size";
        final String copyLogLabel = "Copy log";
        final String buildDoctorLabel = getString(R.string.build_doctor_menu);

        PopupMenu options = new PopupMenu(this, binding.formatButton);
        options.getMenu().add(wrapTextLabel).setCheckable(true).setChecked(getWrappedTextPreference());
        options.getMenu().add(monospacedFontLabel).setCheckable(true).setChecked(getMonospacedFontPreference());
        options.getMenu().add(fontSizeLabel);
        options.getMenu().add(copyLogLabel);
        if (FeatureFlags.isEnabled(FeatureFlag.BUILD_DOCTOR)) options.getMenu().add(buildDoctorLabel);

        options.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getTitle().toString().equals(buildDoctorLabel)) {
                showBuildDoctor();
                return true;
            }
            switch (menuItem.getTitle().toString()) {
                case wrapTextLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleWrapText(menuItem.isChecked());
                }
                case monospacedFontLabel -> {
                    menuItem.setChecked(!menuItem.isChecked());
                    toggleMonospacedText(menuItem.isChecked());
                }
                case fontSizeLabel -> changeFontSizeDialog();
                case copyLogLabel -> copyLog();
                default -> {
                    return false;
                }
            }

            return true;
        });

        binding.formatButton.setOnClickListener(v -> options.show());

        applyLogViewerPreferences();

        setErrorText();
    }

    private void setErrorText() {
        String error = compileErrorSaver.getLogsFromFile();
        if (error == null) {
            binding.noContentLayout.setVisibility(View.VISIBLE);
            binding.optionsLayout.setVisibility(View.GONE);
            return;
        }

        binding.optionsLayout.setVisibility(View.VISIBLE);
        binding.noContentLayout.setVisibility(View.GONE);

        List<Diagnostic> diagnostics = CompileDiagnosticParser.parse(error);
        int errors = CompileDiagnosticParser.count(diagnostics, Severity.ERROR);
        int warnings = CompileDiagnosticParser.count(diagnostics, Severity.WARNING);
        binding.topAppBar.setSubtitle(diagnostics.isEmpty() ? null : errors + " errors, " + warnings + " warnings");

        missing = MissingResourceParser.parse(error);
        binding.tvCompileLog.setText(CompileLogHelper.getColoredLogs(this, error, diagnostics, this::openDiagnostic, missing, this::openResolver));
        // Selecting text and tapping links can't both be on, so "Copy log" is in the options menu.
        binding.tvCompileLog.setMovementMethod(LinkMovementMethod.getInstance());
        prepareResolver();
    }

    // ---- the resource fixer ----

    /** Builds the resolver for the missing resources in the log, off the main thread, and shows the banner. */
    private void prepareResolver() {
        resolverFlow = null;
        int generation = ++resolverGeneration;
        List<MissingResource> found = missing;
        if (found.isEmpty()) {
            binding.resolverBanner.setVisibility(View.GONE);
            return;
        }
        binding.resolverBanner.setVisibility(View.VISIBLE);
        binding.resolverBannerText.setText(R.string.resolver_banner_preparing);
        binding.resolverBannerAction.setEnabled(false);
        resolverWork.execute(() -> {
            IconLibrary icons = ResolverEnvironment.openIcons(getApplicationContext());
            ResolverSession session = ResolverEnvironment.open(getApplicationContext(), scId, found, icons);
            mainHandler.post(() -> {
                if (isDestroyed() || generation != resolverGeneration) return;
                resolverIcons = icons;
                resolverFlow = new ResolverFlow(session, resolverWork, mainHandler::post);
                showResolverBanner();
            });
        });
    }

    private void showResolverBanner() {
        int fixable = resolverFlow.session().fixable().size();
        RebuildReport report = rebuildReport();
        if (report != null) {
            binding.resolverBannerText.setText(report.allGone() ? getString(R.string.resolver_report_clean)
                    : getString(R.string.resolver_report, report.fixed().size(), report.stillMissing().size(), report.newlyMissing().size()));
        } else if (fixable > 0) {
            binding.resolverBannerText.setText(getResources().getQuantityString(R.plurals.resolver_banner, fixable, fixable));
        } else {
            binding.resolverBannerText.setText(R.string.resolver_unavailable);
        }
        binding.resolverBannerAction.setEnabled(true);
        binding.resolverBannerAction.setOnClickListener(v -> openResolver(null));
    }

    /** What the build the resolver asked for changed, when this log follows one. */
    private RebuildReport rebuildReport() {
        if (!RebuildRequest.isAfterRebuild(getIntent())) return null;
        return RebuildReport.compare(RebuildRequest.keysBefore(getIntent()), missing);
    }

    private void openResolver(MissingResource focus) {
        if (resolverFlow == null) {
            SketchwareUtil.toast(getString(R.string.resolver_banner_preparing));
            return;
        }
        if (getSupportFragmentManager().findFragmentByTag("resolver") != null) return;
        ResourceResolverSheet.newInstance(focus == null ? null : focus.key()).show(getSupportFragmentManager(), "resolver");
    }

    @Override
    public ResolverFlow resolverFlow() {
        return resolverFlow;
    }

    @Override
    public IconLibrary resolverIcons() {
        return resolverIcons;
    }

    @Override
    public void onResolverFinished(boolean rebuild, List<String> keysBefore) {
        if (!rebuild) return;
        setResult(RESULT_OK, RebuildRequest.result(keysBefore));
        finish();
    }

    @Override
    public void onResolverOpenLocation(MissingResource problem, MissingResource.Occurrence occurrence) {
        openDiagnostic(new Diagnostic(Severity.ERROR, occurrence.path(), occurrence.line(), occurrence.column(), occurrence.message(), occurrence.start(), occurrence.end()));
    }

    @Override
    public void onDestroy() {
        resolverGeneration++;
        resolverWork.shutdown();
        super.onDestroy();
    }

    private void openDiagnostic(Diagnostic diagnostic) {
        File file = new File(diagnostic.path());
        if (!file.isFile()) {
            SketchwareUtil.toast("File not found: " + diagnostic.path());
            return;
        }

        Intent intent = new Intent(this, SrcCodeEditor.class);
        intent.putExtra("title", file.getName());
        intent.putExtra("content", file.getAbsolutePath());
        intent.putExtra(SrcCodeEditor.EXTRA_LINE, diagnostic.line());
        intent.putExtra(SrcCodeEditor.EXTRA_COLUMN, diagnostic.column());
        startActivity(intent);
    }

    private void showBuildDoctor() {
        String log = compileErrorSaver.getLogsFromFile();
        FindingsDialog.show(this, getString(R.string.build_doctor_title), BuildDoctor.diagnose(log), getString(R.string.build_doctor_none));
    }

    private void copyLog() {
        String log = compileErrorSaver.getLogsFromFile();
        if (log == null) return;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Compile log", log));
        SketchwareUtil.toast("Compile log copied.");
    }

    private void applyLogViewerPreferences() {
        toggleWrapText(getWrappedTextPreference());
        toggleMonospacedText(getMonospacedFontPreference());
        binding.tvCompileLog.setTextSize(getFontSizePreference());
    }

    private boolean getWrappedTextPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_WRAPPED_TEXT, false);
    }

    private boolean getMonospacedFontPreference() {
        return logViewerPreferences.getBoolean(PREFERENCE_USE_MONOSPACED_FONT, true);
    }

    private int getFontSizePreference() {
        return logViewerPreferences.getInt(PREFERENCE_FONT_SIZE, 11);
    }

    private void toggleWrapText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_WRAPPED_TEXT, isChecked).apply();

        if (isChecked) {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errVScroll.addView(binding.tvCompileLog);
        } else {
            binding.errVScroll.removeAllViews();
            if (binding.tvCompileLog.getParent() != null) {
                ((ViewGroup) binding.tvCompileLog.getParent()).removeView(binding.tvCompileLog);
            }
            binding.errHScroll.removeAllViews();
            binding.errHScroll.addView(binding.tvCompileLog);
            binding.errVScroll.addView(binding.errHScroll);
        }
    }

    private void toggleMonospacedText(boolean isChecked) {
        logViewerPreferences.edit().putBoolean(PREFERENCE_USE_MONOSPACED_FONT, isChecked).apply();

        if (isChecked) {
            binding.tvCompileLog.setTypeface(Typeface.MONOSPACE);
        } else {
            binding.tvCompileLog.setTypeface(Typeface.DEFAULT);
        }
    }

    private void changeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(10); //Must not be less than setValue(), which is currently 11 in compile_log.xml
        picker.setMaxValue(70);
        picker.setWrapSelectorWheel(false);
        picker.setValue(getFontSizePreference());

        LinearLayout layout = new LinearLayout(this);
        layout.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select font size")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    logViewerPreferences.edit().putInt(PREFERENCE_FONT_SIZE, picker.getValue()).apply();

                    binding.tvCompileLog.setTextSize((float) picker.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
