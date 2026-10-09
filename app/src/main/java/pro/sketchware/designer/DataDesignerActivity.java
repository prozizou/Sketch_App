package pro.sketchware.designer;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.jC;
import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.util.Helper;
import pro.sketchware.settings.AppLog;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

/**
 * Generates Java for the project from a short description: a REST client, a SQLite database with typed models
 * (in place of Room, which needs annotation processing that this IDE's builds do not run) or Firebase models.
 * The files go to the project's own Java files (Java manager), in the project's package.
 */
public class DataDesignerActivity extends BaseAppCompatActivity {
    private enum Kind {REST, SQLITE, FIREBASE}

    private static final String REST_EXAMPLE = """
            # METHOD name /path [body]; {x} becomes a parameter
            GET users /users
            GET user /users/{id}
            POST addUser /users body
            DELETE removeUser /users/{id}
            """;
    private static final String TABLE_EXAMPLE = """
            # table Name, then one 'name type' per line
            # types: text, int, real, bool, blob
            table Note
              title text
              done bool
              created int
            """;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private String scId;
    private Kind kind = Kind.REST;
    private EditText packageField;
    private EditText classField;
    private EditText optionField;
    private EditText versionField;
    private EditText specField;
    private TextView hint;
    private final String[] specs = {REST_EXAMPLE, TABLE_EXAMPLE, TABLE_EXAMPLE};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scId = getIntent().getStringExtra("sc_id");
        if (scId == null) {
            finish();
            return;
        }
        int pad = SketchwareUtil.dpToPx(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Data designer");
        android.content.res.TypedArray up = obtainStyledAttributes(new int[]{androidx.appcompat.R.attr.homeAsUpIndicator});
        toolbar.setNavigationIcon(up.getDrawable(0));
        up.recycle();
        toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        root.addView(toolbar);
        UI.addSystemWindowInsetToPadding(toolbar, false, true, false, false);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        UI.addSystemWindowInsetToPadding(scroll, false, false, false, true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, 0, pad, pad);
        scroll.addView(content);

        ChipGroup kinds = new ChipGroup(this);
        kinds.setSingleSelection(true);
        kinds.setSelectionRequired(true);
        addKind(kinds, "REST client", Kind.REST, true);
        addKind(kinds, "SQLite database", Kind.SQLITE, false);
        addKind(kinds, "Firebase models", Kind.FIREBASE, false);
        content.addView(kinds);

        hint = new TextView(this);
        hint.setTextSize(12);
        hint.setPadding(0, pad / 2, 0, pad / 2);
        content.addView(hint);

        packageField = field(content, "Package");
        packageField.setText(projectPackage());
        classField = field(content, "Class name");
        optionField = field(content, "");
        versionField = field(content, "Database version");
        versionField.setInputType(InputType.TYPE_CLASS_NUMBER);
        versionField.setText("1");

        specField = new EditText(this);
        specField.setTypeface(Typeface.MONOSPACE);
        specField.setTextSize(13);
        specField.setGravity(Gravity.TOP | Gravity.START);
        specField.setMinLines(8);
        specField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        specField.setHorizontallyScrolling(false);
        content.addView(specField);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        MaterialButton preview = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        preview.setText("Preview");
        preview.setOnClickListener(v -> generate(false));
        MaterialButton save = new MaterialButton(this);
        save.setText("Add to project");
        save.setOnClickListener(v -> generate(true));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.setMarginEnd(pad / 2);
        buttons.addView(preview, gap);
        buttons.addView(save);
        content.addView(buttons);

        setContentView(root);
        applyKind();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void addKind(ChipGroup group, String label, Kind value, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setOnClickListener(v -> {
            specs[kind.ordinal()] = specField.getText().toString();
            kind = value;
            applyKind();
        });
        group.addView(chip);
    }

    private EditText field(LinearLayout parent, String label) {
        EditText edit = new EditText(this);
        edit.setHint(label);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        parent.addView(edit);
        return edit;
    }

    private void applyKind() {
        specField.setText(specs[kind.ordinal()]);
        switch (kind) {
            case REST -> {
                hint.setText("A class with one method per request. Requests run in the background and the answer comes back on the main thread. The app needs the INTERNET permission (Permission manager).");
                classField.setText("Api");
                optionField.setHint("Base address, like https://api.example.com");
                optionField.setVisibility(View.VISIBLE);
                versionField.setVisibility(View.GONE);
            }
            case SQLITE -> {
                hint.setText("A database with one model class and insert, update, delete, get and list methods per table. Room is not offered: it needs annotation processing, which the builds of this IDE do not run.");
                classField.setText("AppDatabase");
                optionField.setHint("Database file, like app.db");
                optionField.setText("app.db");
                optionField.setVisibility(View.VISIBLE);
                versionField.setVisibility(View.VISIBLE);
            }
            case FIREBASE -> {
                hint.setText("One model class per table, with toMap() for the Firebase blocks and fromMap() for what they return. It uses no Firebase class.");
                classField.setText("");
                optionField.setVisibility(View.GONE);
                versionField.setVisibility(View.GONE);
            }
        }
        classField.setVisibility(kind == Kind.FIREBASE ? View.GONE : View.VISIBLE);
    }

    private String projectPackage() {
        try {
            String name = yB.c(lC.b(scId), "my_sc_pkg_name");
            return name == null ? "" : name;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Builds the files, or shows what is wrong; then shows the code or writes it. */
    private void generate(boolean save) {
        String pkg = packageField.getText().toString().trim();
        String className = classField.getText().toString().trim();
        String option = optionField.getText().toString().trim();
        String spec = specField.getText().toString();
        List<String> errors = new ArrayList<>();
        List<Generated> files = new ArrayList<>();
        switch (kind) {
            case REST -> {
                DesignSpec.Parsed<DesignSpec.Endpoint> parsed = DesignSpec.parseEndpoints(spec);
                errors.addAll(parsed.errors());
                String problem = errors.isEmpty() ? RestClientGenerator.check(pkg, className, option, parsed.items()) : null;
                if (problem != null) errors.add(problem);
                if (errors.isEmpty()) files.add(RestClientGenerator.generate(pkg, className, option, parsed.items()));
            }
            case SQLITE -> {
                DesignSpec.Parsed<DesignSpec.Table> parsed = DesignSpec.parseTables(spec);
                errors.addAll(parsed.errors());
                int version;
                try {
                    version = Integer.parseInt(versionField.getText().toString().trim());
                } catch (NumberFormatException e) {
                    version = 0;
                }
                String problem = errors.isEmpty() ? SqliteGenerator.check(pkg, className, option, version, parsed.items()) : null;
                if (problem != null) errors.add(problem);
                if (errors.isEmpty()) files.add(SqliteGenerator.generate(pkg, className, option, version, parsed.items()));
            }
            case FIREBASE -> {
                DesignSpec.Parsed<DesignSpec.Table> parsed = DesignSpec.parseTables(spec);
                errors.addAll(parsed.errors());
                String problem = errors.isEmpty() ? FirebaseModelGenerator.check(pkg, parsed.items()) : null;
                if (problem != null) errors.add(problem);
                if (errors.isEmpty()) files.addAll(FirebaseModelGenerator.generate(pkg, parsed.items()));
            }
        }
        if (!errors.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle("Fix these first").setMessage(String.join("\n", errors))
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        for (Generated file : files) {
            String simple = file.fileName().replace(".java", "");
            for (ProjectFileBean screen : jC.b(scId).b()) {
                if (screen.getJavaName().equals(file.fileName())) {
                    new MaterialAlertDialogBuilder(this).setTitle("Name already used")
                            .setMessage(simple + " is the name of a screen. A Java file with that name would define the class twice and the build would fail.")
                            .setPositiveButton(android.R.string.ok, null).show();
                    return;
                }
            }
        }
        if (save) {
            save(pkg, files);
        } else {
            showCode(files);
        }
    }

    private void showCode(List<Generated> files) {
        StringBuilder text = new StringBuilder();
        for (Generated file : files) {
            if (files.size() > 1) text.append("// ---- ").append(file.fileName()).append("\n\n");
            text.append(file.source()).append('\n');
        }
        TextView view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(11);
        view.setTextIsSelectable(true);
        view.setText(text);
        int pad = SketchwareUtil.dpToPx(16);
        view.setPadding(pad, pad / 2, pad, pad / 2);
        android.widget.HorizontalScrollView horizontal = new android.widget.HorizontalScrollView(this);
        horizontal.addView(view);
        ScrollView vertical = new ScrollView(this);
        vertical.addView(horizontal);
        new MaterialAlertDialogBuilder(this).setTitle("Generated code").setView(vertical)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void save(String pkg, List<Generated> files) {
        File dir = new File(new FilePathUtil().getPathJava(scId), pkg.replace('.', File.separatorChar));
        List<String> existing = new ArrayList<>();
        for (Generated file : files) {
            if (new File(dir, file.fileName()).exists()) existing.add(file.fileName());
        }
        Runnable write = () -> executor.execute(() -> {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Couldn't create " + dir);
                for (Generated file : files) {
                    Files.write(new File(dir, file.fileName()).toPath(), file.source().getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> SketchwareUtil.toast("Added " + files.size() + " file(s) to the project's Java files"));
            } catch (IOException e) {
                AppLog.e("DataDesigner", "Couldn't write: " + e);
                runOnUiThread(() -> SketchwareUtil.toastError("Couldn't write the files: " + e.getMessage()));
            }
        });
        if (existing.isEmpty()) {
            write.run();
        } else {
            new MaterialAlertDialogBuilder(this).setTitle("Replace files?")
                    .setMessage("These files already exist and will be replaced: " + String.join(", ", existing))
                    .setPositiveButton("Replace", (d, w) -> write.run())
                    .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
}
