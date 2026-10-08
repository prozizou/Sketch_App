package mod.jbk.code.diagnostics;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.style.DiagnosticIndicatorStyle;
import mod.jbk.code.completion.AndroidClassIndex;
import mod.jbk.code.diagnostics.JavaDiagnostics.Diagnostic;
import mod.jbk.code.diagnostics.JavaDiagnostics.Severity;
import pro.sketchware.settings.AppLog;

/**
 * Underlines the problems of the Java file in an editor while it is edited. The analysis runs a moment
 * after the last keystroke, off the main thread, and a newer edit discards the result of an older one.
 */
public final class LiveDiagnostics {
    public interface Listener {
        void onDiagnostics(int errors, int warnings);
    }

    private static final String TAG = "LiveDiagnostics";
    private static final long DELAY_MS = 700;

    private final CodeEditor editor;
    private final Context context;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private final Runnable analyzeNow = this::analyze;
    private SubscriptionReceipt<ContentChangeEvent> subscription;
    private List<Diagnostic> current = Collections.emptyList();
    private boolean enabled;

    public LiveDiagnostics(CodeEditor editor, Listener listener) {
        this.editor = editor;
        this.context = editor.getContext().getApplicationContext();
        this.listener = listener;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) {
            editor.setDiagnosticIndicatorStyle(DiagnosticIndicatorStyle.WAVY_LINE);
            subscription = editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> schedule());
            schedule();
        } else {
            stop();
            show(Collections.emptyList());
        }
    }

    /** Stops for good; call when the editor goes away. */
    public void destroy() {
        stop();
        executor.shutdownNow();
    }

    private void stop() {
        generation.incrementAndGet();
        editor.removeCallbacks(analyzeNow);
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    private void schedule() {
        editor.removeCallbacks(analyzeNow);
        editor.postDelayed(analyzeNow, DELAY_MS);
    }

    private void analyze() {
        if (!enabled) return;
        int id = generation.incrementAndGet();
        String text = editor.getText().toString();
        try {
            executor.execute(() -> {
                List<Diagnostic> found;
                try {
                    found = JavaDiagnostics.analyze(text, AndroidClassIndex.get(context));
                } catch (RuntimeException | OutOfMemoryError e) {
                    AppLog.e(TAG, "Analysis failed: " + e);
                    return;
                }
                List<Diagnostic> result = found;
                editor.post(() -> {
                    if (generation.get() == id) show(result);
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException destroyed) {
            // The editor is gone
        }
    }

    private void show(List<Diagnostic> diagnostics) {
        current = diagnostics;
        DiagnosticsContainer container = new DiagnosticsContainer();
        List<DiagnosticRegion> regions = new ArrayList<>();
        int errors = 0;
        int warnings = 0;
        long nextId = 0;
        for (Diagnostic d : diagnostics) {
            boolean error = d.severity() == Severity.ERROR;
            if (error) errors++;
            else warnings++;
            regions.add(new DiagnosticRegion(d.start(), d.end(),
                    error ? DiagnosticRegion.SEVERITY_ERROR : DiagnosticRegion.SEVERITY_WARNING, nextId++,
                    new DiagnosticDetail(d.message(), d.detail(), null, null)));
        }
        container.addDiagnostics(regions);
        editor.setDiagnostics(container);
        listener.onDiagnostics(errors, warnings);
    }

    /** Moves the caret to the next problem, wrapping around. @return false if there are none */
    public boolean goToNextProblem() {
        int caret = editor.getCursor().getLeft();
        Diagnostic next = JavaDiagnostics.nextAfter(current, caret);
        if (next == null) return false;
        CharPosition at = editor.getText().getIndexer().getCharPosition(Math.min(next.start(), editor.getText().length()));
        editor.setSelection(at.line, at.column);
        return true;
    }
}
