package pro.sketchware.activities.editor.view.xml;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.text.Editable;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.PublishSearchResultEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.event.SubscriptionReceipt;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import pro.sketchware.R;
import pro.sketchware.databinding.ItemXmlSymbolBinding;
import pro.sketchware.databinding.ViewCodeEditorBinding;
import pro.sketchware.databinding.XmlFindPanelBinding;
import pro.sketchware.lib.base.BaseTextWatcher;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * Everything around the text of the XML editor: the status bar (is the XML valid, where the caret is), the squiggle
 * under the first error, the quick-insert keys, find and replace, and Format. The screen owns the file, saving and
 * the menu; this owns what the user sees and does while typing. Nothing here needs the project, so it can be built
 * on any {@link ViewCodeEditorBinding}.
 */
public final class XmlEditorUi {
    /** What {@link #format()} did. */
    public enum FormatResult {DONE, UNCHANGED, INVALID}

    /** Validation waits for a pause in typing, then runs off the main thread. */
    private static final long VALIDATE_DELAY_MS = 350;
    /** A selection longer than this is not copied into the Find field. */
    private static final int MAX_PREFILL = 120;

    private final ViewCodeEditorBinding binding;
    private final XmlFindPanelBinding find;
    private final CodeEditor editor;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private final List<SubscriptionReceipt<?>> receipts = new ArrayList<>();
    private ExecutorService executor;
    private XmlDiagnostics.Result result = XmlDiagnostics.Result.ok(0);
    private Runnable onStateChanged = () -> {
    };
    /** After a "Replace", the next match is selected once the refreshed search has published. */
    private boolean advanceAfterSearch;
    private final Runnable validateLater = this::validateAsync;

    public XmlEditorUi(@NonNull ViewCodeEditorBinding binding) {
        this.binding = binding;
        this.find = binding.findPanel;
        this.editor = binding.editor;
        this.context = binding.getRoot().getContext();
    }

    /**
     * Starts listening to the editor and fills the bars.
     *
     * @param onStateChanged   called whenever the text changed (the screen refreshes undo, redo, Save and its subtitle)
     * @param onFormatRequested the Format button was tapped (the screen runs {@link #format()} and tells the user)
     */
    public void attach(@NonNull Runnable onStateChanged, @NonNull Runnable onFormatRequested) {
        this.onStateChanged = onStateChanged;
        executor = Executors.newSingleThreadExecutor();
        buildSymbols();
        wireFind();
        binding.statusValidity.setOnClickListener(v -> goToError());
        binding.quickFind.setOnClickListener(v -> showFind());
        binding.quickFormat.setOnClickListener(v -> onFormatRequested.run());
        receipts.add(editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> {
            updatePosition();
            if (event.getCause() == SelectionChangeEvent.CAUSE_SEARCH) updateFindCount();
        }));
        receipts.add(editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            handler.removeCallbacks(validateLater);
            handler.postDelayed(validateLater, VALIDATE_DELAY_MS);
            this.onStateChanged.run();
        }));
        receipts.add(editor.subscribeEvent(PublishSearchResultEvent.class, (event, unsubscribe) -> {
            if (advanceAfterSearch) {
                advanceAfterSearch = false;
                editor.getSearcher().gotoNext();
            }
            updateFindCount();
        }));
        updatePosition();
        checkNow();
    }

    /** Stops the listeners and the background work; the screen calls it when it is destroyed. */
    public void detach() {
        for (SubscriptionReceipt<?> receipt : receipts) {
            receipt.unsubscribe();
        }
        receipts.clear();
        handler.removeCallbacks(validateLater);
        generation.incrementAndGet();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        editor.getSearcher().stopSearch();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Validation and status
    // ---------------------------------------------------------------------------------------------------------

    /** @return the last check of the text (the one the status bar shows) */
    @NonNull
    public XmlDiagnostics.Result lastResult() {
        return result;
    }

    /** Checks the text now, on this thread, and shows the outcome. Used when saving, where an old answer won't do. */
    @NonNull
    public XmlDiagnostics.Result checkNow() {
        handler.removeCallbacks(validateLater);
        generation.incrementAndGet();
        show(XmlDiagnostics.check(editor.getText().toString()));
        return result;
    }

    private void validateAsync() {
        ExecutorService worker = executor;
        if (worker == null) return;
        String xml = editor.getText().toString();
        int mine = generation.incrementAndGet();
        try {
            worker.execute(() -> {
                XmlDiagnostics.Result checked = XmlDiagnostics.check(xml);
                handler.post(() -> {
                    if (mine == generation.get()) show(checked);
                });
            });
        } catch (RejectedExecutionException ignored) {
            // Detached meanwhile.
        }
    }

    private void show(XmlDiagnostics.Result checked) {
        result = checked;
        boolean valid = checked.valid();
        int green = ThemeUtils.getColor(binding.statusBar, R.attr.colorCoolGreen);
        int red = ThemeUtils.getColor(binding.statusBar, R.attr.colorError);
        int neutral = ThemeUtils.getColor(binding.statusBar, R.attr.colorOnSurfaceVariant);
        binding.statusIcon.setImageResource(valid ? R.drawable.ic_mtrl_check : R.drawable.ic_evolve_notice);
        binding.statusIcon.setImageTintList(ColorStateList.valueOf(valid ? green : red));
        TextView text = binding.statusText;
        if (valid) {
            text.setText(context.getResources().getQuantityString(R.plurals.xml_status_valid, checked.elements(), checked.elements()));
            text.setTextColor(neutral);
            binding.statusValidity.setContentDescription(null);
        } else {
            String message = context.getString(R.string.xml_status_error, checked.line() + 1, checked.message());
            text.setText(message);
            text.setTextColor(red);
            binding.statusValidity.setContentDescription(message + ". " + context.getString(R.string.xml_status_go_to_error));
        }
        binding.statusValidity.setClickable(!valid);
        showDiagnostic(checked);
    }

    /** A squiggle from the error to the end of its line. */
    private void showDiagnostic(XmlDiagnostics.Result checked) {
        DiagnosticsContainer container = new DiagnosticsContainer();
        Content text = editor.getText();
        if (!checked.valid() && text.length() > 0) {
            int line = Math.min(checked.line(), text.getLineCount() - 1);
            int column = Math.min(checked.column(), text.getColumnCount(line));
            int start = text.getCharIndex(line, column);
            int end = text.getCharIndex(line, text.getColumnCount(line));
            if (end <= start) {
                start = Math.max(0, Math.min(start, text.length() - 1));
                end = Math.min(text.length(), start + 1);
            }
            DiagnosticDetail detail = new DiagnosticDetail(checked.message(), checked.message(), Collections.emptyList(), null);
            container.addDiagnostic(new DiagnosticRegion(start, end, DiagnosticRegion.SEVERITY_ERROR, 0L, detail));
        }
        editor.setDiagnostics(container);
    }

    /** Puts the caret on the first error and scrolls to it. */
    public void goToError() {
        if (result.valid()) return;
        Content text = editor.getText();
        int line = Math.max(0, Math.min(result.line(), text.getLineCount() - 1));
        int column = Math.max(0, Math.min(result.column(), text.getColumnCount(line)));
        editor.setSelection(line, column);
        editor.ensurePositionVisible(line, column);
        editor.requestFocus();
    }

    private void updatePosition() {
        Cursor cursor = editor.getCursor();
        int line = cursor.getLeftLine() + 1;
        int column = cursor.getLeftColumn() + 1;
        int selected = cursor.getRight() - cursor.getLeft();
        String position = cursor.isSelected()
                ? context.getResources().getQuantityString(R.plurals.xml_status_position_selected, selected, line, column, selected)
                : context.getString(R.string.xml_status_position, line, column);
        binding.statusPosition.setText(position);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Quick-insert keys
    // ---------------------------------------------------------------------------------------------------------

    private void buildSymbols() {
        binding.symbolBar.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (XmlSymbols.Symbol symbol : XmlSymbols.ALL) {
            ItemXmlSymbolBinding key = ItemXmlSymbolBinding.inflate(inflater, binding.symbolBar, false);
            TextView view = key.getRoot();
            view.setText(symbol.label());
            view.setContentDescription(context.getString(symbol.describe()));
            view.setOnClickListener(v -> insert(symbol));
            binding.symbolBar.addView(view);
        }
    }

    /** Types a key into the editor at the caret, replacing (or, for quotes, wrapping) the selection. */
    public void insert(@NonNull XmlSymbols.Symbol symbol) {
        Cursor cursor = editor.getCursor();
        Content text = editor.getText();
        int startLine = cursor.getLeftLine();
        int startColumn = cursor.getLeftColumn();
        int endLine = cursor.getRightLine();
        int endColumn = cursor.getRightColumn();
        String selected = cursor.isSelected() ? text.substring(cursor.getLeft(), cursor.getRight()) : "";
        XmlSymbols.Edit edit = XmlSymbols.edit(symbol, selected);
        text.replace(startLine, startColumn, endLine, endColumn, edit.text());
        // Every key is typed on one line, but a wrapped selection may span several: count from the end then.
        int newlines = 0;
        int lastBreak = -1;
        for (int i = 0; i < edit.caret(); i++) {
            if (edit.text().charAt(i) == '\n') {
                newlines++;
                lastBreak = i;
            }
        }
        int line = startLine + newlines;
        int column = newlines == 0 ? startColumn + edit.caret() : edit.caret() - lastBreak - 1;
        editor.setSelection(line, column);
    }

    private void updateSymbolBar() {
        boolean searching = find.getRoot().getVisibility() == View.VISIBLE && (find.findQuery.hasFocus() || find.replaceText.hasFocus());
        binding.symbolRow.setVisibility(searching ? View.GONE : View.VISIBLE);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Find and replace
    // ---------------------------------------------------------------------------------------------------------

    private void wireFind() {
        View.OnFocusChangeListener focus = (v, hasFocus) -> updateSymbolBar();
        find.findQuery.setOnFocusChangeListener(focus);
        find.replaceText.setOnFocusChangeListener(focus);
        find.findQuery.addTextChangedListener(new BaseTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                runSearch();
            }
        });
        find.findQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                editor.getSearcher().gotoNext();
                return true;
            }
            return false;
        });
        find.findNext.setOnClickListener(v -> editor.getSearcher().gotoNext());
        find.findPrev.setOnClickListener(v -> editor.getSearcher().gotoPrevious());
        find.findClose.setOnClickListener(v -> hideFind());
        find.findCase.setOnCheckedChangeListener((button, checked) -> runSearch());
        find.findRegex.setOnCheckedChangeListener((button, checked) -> runSearch());
        find.replaceOne.setOnClickListener(v -> replaceOne());
        find.replaceAll.setOnClickListener(v -> replaceAll());
    }

    public boolean isFindVisible() {
        return find.getRoot().getVisibility() == View.VISIBLE;
    }

    /** Opens find and replace; a selection on one line becomes the query. */
    public void showFind() {
        find.getRoot().setVisibility(View.VISIBLE);
        Cursor cursor = editor.getCursor();
        if (cursor.isSelected() && cursor.getLeftLine() == cursor.getRightLine() && cursor.getRight() - cursor.getLeft() <= MAX_PREFILL) {
            find.findQuery.setText(editor.getText().substring(cursor.getLeft(), cursor.getRight()));
        }
        find.findQuery.requestFocus();
        find.findQuery.selectAll();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(find.findQuery, InputMethodManager.SHOW_IMPLICIT);
        updateSymbolBar();
        runSearch();
    }

    /** @return whether the panel was open (and is now closed) */
    public boolean hideFind() {
        if (!isFindVisible()) return false;
        find.getRoot().setVisibility(View.GONE);
        editor.getSearcher().stopSearch();
        advanceAfterSearch = false;
        editor.requestFocus();
        updateSymbolBar();
        return true;
    }

    private void runSearch() {
        EditorSearcher searcher = editor.getSearcher();
        String query = find.findQuery.getText().toString();
        advanceAfterSearch = false;
        if (query.isEmpty()) {
            searcher.stopSearch();
            find.findCount.setText("");
            return;
        }
        boolean regex = find.findRegex.isChecked();
        if (regex) {
            try {
                Pattern.compile(query);
            } catch (PatternSyntaxException e) {
                searcher.stopSearch();
                find.findCount.setText(R.string.xml_find_bad_regex);
                return;
            }
        }
        find.findCount.setText("");
        searcher.search(query, new EditorSearcher.SearchOptions(
                regex ? EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION : EditorSearcher.SearchOptions.TYPE_NORMAL,
                !find.findCase.isChecked()));
    }

    private void updateFindCount() {
        EditorSearcher searcher = editor.getSearcher();
        if (!searcher.hasQuery() || !isFindVisible()) {
            return;
        }
        int count = searcher.getMatchedPositionCount();
        int index = searcher.getCurrentMatchedPositionIndex();
        if (count == 0) {
            find.findCount.setText(R.string.xml_find_none);
        } else if (index >= 0) {
            find.findCount.setText(context.getString(R.string.xml_find_count, index + 1, count));
        } else {
            find.findCount.setText(context.getResources().getQuantityString(R.plurals.xml_find_matches, count, count));
        }
    }

    /** Replaces the selected match and moves on; with no match selected, it only selects the next one. */
    private void replaceOne() {
        EditorSearcher searcher = editor.getSearcher();
        if (!searcher.hasQuery()) return;
        try {
            if (searcher.isMatchedPositionSelected()) {
                advanceAfterSearch = true;
                searcher.replaceCurrentMatch(find.replaceText.getText().toString());
            } else {
                searcher.gotoNext();
            }
        } catch (IllegalStateException e) {
            // The search was still running: nothing to replace yet.
            advanceAfterSearch = false;
        }
    }

    private void replaceAll() {
        EditorSearcher searcher = editor.getSearcher();
        if (!searcher.hasQuery()) return;
        int count = searcher.getMatchedPositionCount();
        if (count == 0) return;
        try {
            searcher.replaceAll(find.replaceText.getText().toString(), () -> binding.getRoot().post(() ->
                    SketchwareUtil.toast(context.getResources().getQuantityString(R.plurals.xml_find_replaced, count, count))));
        } catch (IllegalStateException ignored) {
            // Still searching.
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Format
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Re-indents the layout (see {@link XmlFormatter}) as one undoable edit. A layout that isn't well-formed is
     * left alone.
     */
    @NonNull
    public FormatResult format() {
        String xml = editor.getText().toString();
        String formatted = XmlFormatter.format(xml);
        if (formatted == null) {
            return FormatResult.INVALID;
        }
        if (formatted.equals(xml)) {
            return FormatResult.UNCHANGED;
        }
        Content text = editor.getText();
        int line = editor.getCursor().getLeftLine();
        int lastLine = text.getLineCount() - 1;
        text.replace(0, 0, lastLine, text.getColumnCount(lastLine), formatted);
        int newLine = Math.min(line, editor.getText().getLineCount() - 1);
        editor.setSelection(newLine, 0);
        return FormatResult.DONE;
    }
}
