package mod.jbk.code.completion;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.Comparator;

import io.github.rosemoe.sora.lang.completion.CompletionCancelledException;
import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.CodeEditor;
import mod.jbk.code.completion.JavaCompletionEngine.ImportEdit;
import mod.jbk.code.completion.JavaCompletionEngine.Result;
import mod.jbk.code.completion.JavaCompletionEngine.Suggestion;
import pro.sketchware.settings.AppLog;
import pro.sketchware.utility.FilePathUtil;

/**
 * Sora's Java language with smarter completion on top: members after a dot, class names that add their
 * own import, and qualified names in import lines. The stock keyword and word completion still runs
 * wherever the new suggestions don't apply.
 */
public class SketchJavaLanguage extends JavaLanguage {
    private static final String TAG = "JavaCompletion";

    private final Context context;
    private final String scId;
    private JavaCompletionEngine engine;

    /**
     * @param scId the project the file belongs to, to also suggest that project's own classes; may be null
     */
    public SketchJavaLanguage(Context context, @Nullable String scId) {
        this.context = context.getApplicationContext();
        this.scId = scId;
    }

    private synchronized JavaCompletionEngine engine() {
        if (engine == null) {
            ClassIndex index = AndroidClassIndex.get(context);
            if (scId != null) {
                File javaDir = new File(new FilePathUtil().getPathJava(scId));
                index = ClassIndex.merge(index, ClassIndex.fromSourceDir(javaDir));
            }
            ClassLoader loader = SketchJavaLanguage.class.getClassLoader();
            engine = new JavaCompletionEngine(index, name -> {
                try {
                    return Class.forName(name, false, loader);
                } catch (ClassNotFoundException | LinkageError e) {
                    return null;
                }
            });
        }
        return engine;
    }

    @Override
    public void requireAutoComplete(ContentReference content, CharPosition position, CompletionPublisher publisher, Bundle extraArguments) throws CompletionCancelledException {
        Result result = null;
        try {
            result = engine().complete(content.getReference().toString(), position.index);
        } catch (RuntimeException e) {
            AppLog.e(TAG, "Completion failed: " + e);
        }
        publisher.checkCancelled();

        if (result != null) {
            for (Suggestion suggestion : result.suggestions()) {
                publisher.addItem(new SuggestionItem(suggestion, result.prefixLength()));
            }
        }
        if (result == null || !result.exclusive()) {
            super.requireAutoComplete(content, position, publisher, extraArguments);
        }
        publisher.setComparator(Comparator.comparing(SketchJavaLanguage::sortKey));
    }

    private static String sortKey(CompletionItem item) {
        // Our suggestions carry a rank; the stock ones (keywords, words in the file) sit between ranks 0 and 2
        return item.sortText != null ? item.sortText : "1" + item.label;
    }

    private static final class SuggestionItem extends CompletionItem {
        private final Suggestion suggestion;

        SuggestionItem(Suggestion suggestion, int prefixLength) {
            super(suggestion.label(), suggestion.detail());
            this.suggestion = suggestion;
            this.prefixLength = prefixLength;
            this.sortText = suggestion.rank() + suggestion.label();
            this.kind = switch (suggestion.type()) {
                case METHOD -> CompletionItemKind.Method;
                case FIELD -> CompletionItemKind.Field;
                case CLASS -> CompletionItemKind.Class;
            };
        }

        @Override
        public void performCompletion(CodeEditor editor, Content text, int line, int column) {
            int start = Math.max(0, column - prefixLength);
            text.beginBatchEdit();
            try {
                text.replace(line, start, line, column, suggestion.insertText());
                int caretColumn = start + suggestion.insertText().length() - suggestion.caretBack();
                int caretLine = line;

                if (suggestion.importFqn() != null) {
                    ImportEdit edit = JavaCompletionEngine.importEdit(text.toString(), suggestion.importFqn());
                    if (edit != null) {
                        int caretIndex = text.getCharIndex(caretLine, caretColumn);
                        CharPosition at = text.getIndexer().getCharPosition(edit.offset());
                        text.insert(at.line, at.column, edit.text());
                        if (edit.offset() <= caretIndex) {
                            caretLine += (int) edit.text().chars().filter(c -> c == '\n').count();
                        }
                    }
                }
                editor.setSelection(caretLine, caretColumn);
            } finally {
                text.endBatchEdit();
            }
        }
    }
}
