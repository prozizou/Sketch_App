package pro.sketchware.code;

import android.app.Activity;
import android.view.Gravity;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.ui.ColorPickerDialog;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Opens the color picker from a code editor and writes the chosen color at the cursor, so a color no longer has to
 * be copied from elsewhere. A color literal under the cursor (or the selection) is replaced and pre-selected.
 *
 * <p>Java gets {@code 0xFFRRGGBB} and {@code getResources().getColor(R.color.name)}; XML gets {@code #RRGGBB},
 * {@code @color/name} and {@code ?attr}.
 */
public final class CodeColorPicker {

    private CodeColorPicker() {
    }

    /**
     * @param java  whether the file is Java (else XML-style literals are written)
     * @param scId  the project, to also offer its colors.xml and Material 3 colors; null for colors only
     */
    public static void show(Activity activity, CodeEditor editor, boolean java, @Nullable String scId) {
        Cursor cursor = editor.getCursor();
        Content text = editor.getText();
        int line = cursor.getLeftLine();
        int start = cursor.getLeftColumn();
        int endLine = cursor.getRightLine();
        int end = cursor.getRightColumn();
        int initialColor = 0xFFFFFFFF;
        if (!cursor.isSelected()) {
            ColorLiteral.Found found = ColorLiteral.at(text.getLineString(line), start);
            if (found != null) {
                start = found.start();
                end = found.end();
                initialColor = found.color();
            }
        } else if (line == endLine) {
            Integer selected = ColorLiteral.parse(text.subSequence(text.getCharIndex(line, start), text.getCharIndex(endLine, end)).toString().trim());
            if (selected != null) initialColor = selected;
        }

        int fromLine = line;
        int fromColumn = start;
        int toLine = endLine;
        int toColumn = end;
        ColorPickerDialog picker = scId != null
                ? new ColorPickerDialog(activity, ColorLiteral.format(initialColor, false), true, false, scId)
                : new ColorPickerDialog(activity, initialColor, true, false);
        picker.a(new ColorPickerDialog.b() {
            @Override
            public void a(int color) {
                write(editor, fromLine, fromColumn, toLine, toColumn, ColorLiteral.format(color, java));
            }

            @Override
            public void a(String name, int color) {
                // A color of the project's colors.xml: refer to it rather than copying its value.
                write(editor, fromLine, fromColumn, toLine, toColumn,
                        java ? "getResources().getColor(R.color." + name + ")" : "@color/" + name);
            }
        });
        picker.materialColorAttr((attr, color) -> write(editor, fromLine, fromColumn, toLine, toColumn,
                java ? ColorLiteral.format(color, true) : "?" + attr));
        picker.showAtLocation(editor, Gravity.CENTER, 0, 0);
    }

    private static void write(CodeEditor editor, int fromLine, int fromColumn, int toLine, int toColumn, String value) {
        Content text = editor.getText();
        text.replace(fromLine, fromColumn, toLine, toColumn, value);
        editor.setSelection(fromLine, fromColumn + value.length());
    }
}
