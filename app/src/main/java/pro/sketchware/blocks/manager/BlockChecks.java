package pro.sketchware.blocks.manager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds problems in a custom block before it is saved. The Logic editor turns a block into Java with
 * {@code String.format(code, inputs…, substack1, substack2)}, every argument a string; code that can't be formatted
 * that way ends up as a "Failed to resolve" comment in the app, so it is reported here first.
 */
public final class BlockChecks {
    public enum Severity {ERROR, WARNING, INFO}

    public record Issue(Severity severity, String message) {
    }

    private static final Pattern SPEC_INPUT = Pattern.compile("%(?:[sdb](?:\\.[\\w]+)?|m\\.[\\w]+(?:\\.[\\w]+)?)(?=\\s|$)");
    private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?([a-zA-Z%])");
    private static final Pattern IMPORT = Pattern.compile("(?:import\\s+)?(?:static\\s+)?[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$*][\\w$]*)+\\s*;?");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final java.util.Set<String> REPORTER_TYPES = java.util.Set.of("s", "d", "b", "v", "a", "l", "p");

    /** Known replacements for APIs that are deprecated or removed on recent Android versions. */
    private static final String[][] DEPRECATED = {
            {"AsyncTask", "AsyncTask is deprecated: use an Executor and a Handler"},
            {"ProgressDialog", "ProgressDialog is deprecated: use a progress indicator in the layout"},
            {"startActivityForResult(", "startActivityForResult is deprecated: use registerForActivityResult"},
            {"onBackPressed()", "onBackPressed is deprecated: use the OnBackPressedDispatcher"},
            {"new Handler()", "new Handler() without a Looper is deprecated: use new Handler(Looper.getMainLooper())"},
            {"android.preference.", "android.preference is deprecated: use androidx.preference"},
            {"getExternalStorageDirectory(", "getExternalStorageDirectory is deprecated since Android 10"},
    };

    /** Code that needs a permission the project must declare. */
    private static final String[][] PERMISSIONS = {
            {"VIBRATOR_SERVICE", "android.permission.VIBRATE"},
            {"Vibrator", "android.permission.VIBRATE"},
            {"HttpURLConnection", "android.permission.INTERNET"},
            {"openConnection(", "android.permission.INTERNET"},
            {"OkHttpClient", "android.permission.INTERNET"},
            {"LocationManager", "android.permission.ACCESS_FINE_LOCATION"},
            {"Camera.open(", "android.permission.CAMERA"},
            {"CameraManager", "android.permission.CAMERA"},
            {"MediaRecorder", "android.permission.RECORD_AUDIO"},
            {"BluetoothAdapter", "android.permission.BLUETOOTH_CONNECT"},
            {"setWallpaper", "android.permission.SET_WALLPAPER"},
    };

    private BlockChecks() {
    }

    /** @return the inputs of a spec, in order: {@code %s}, {@code %d}, {@code %b} and {@code %m.something} */
    public static List<String> inputs(String spec) {
        List<String> inputs = new ArrayList<>();
        Matcher m = SPEC_INPUT.matcher(spec == null ? "" : spec);
        while (m.find()) inputs.add(m.group());
        return inputs;
    }

    /**
     * @param otherNames names of the other custom blocks (a name is the block's opcode)
     */
    public static List<Issue> check(String name, String type, String spec, String code, String imports, Collection<String> otherNames) {
        List<Issue> issues = new ArrayList<>();
        String safeName = name == null ? "" : name.trim();
        String safeType = type == null || type.trim().isEmpty() || type.equals("regular") ? " " : type.trim();
        String safeCode = code == null ? "" : code;

        if (safeName.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "The block needs a name"));
        } else if (!NAME.matcher(safeName).matches()) {
            issues.add(new Issue(Severity.ERROR, "The name may only contain letters, digits, _ and $, and not start with a digit"));
        } else if (otherNames.contains(safeName)) {
            issues.add(new Issue(Severity.ERROR, "Another block already uses the name (opcode) \"" + safeName + "\""));
        }
        if (!BlockTypes.isKnown(safeType)) {
            issues.add(new Issue(Severity.ERROR, "Unknown block type \"" + type + "\""));
        }
        if (spec == null || spec.trim().isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "The block needs a spec"));
        }

        int inputs = inputs(spec).size();
        int arguments = inputs + 2;
        int highest = highestReference(safeCode);
        try {
            Object[] values = new Object[arguments];
            for (int i = 0; i < arguments; i++) values[i] = "x";
            String.format(Locale.ROOT, safeCode, values);
        } catch (RuntimeException e) {
            issues.add(new Issue(Severity.ERROR, "The code can't be turned into Java (" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()) + "). Inputs are text: use %s, and %% for a percent sign"));
        }
        if (highest > arguments) {
            issues.add(new Issue(Severity.ERROR, "The code refers to input " + highest + " but the spec has " + inputs
                    + (inputs == 1 ? " input" : " inputs")));
        } else if (highest > inputs && !safeType.equals("c") && !safeType.equals("e")) {
            issues.add(new Issue(Severity.WARNING, "The code refers to input " + highest
                    + ", which is a block slot only \"if\" blocks (c, e) have: here it is always empty"));
        }
        if (highest < inputs && inputs > 0) {
            issues.add(new Issue(Severity.WARNING, "The code doesn't use every input of the spec (" + inputs + " inputs, "
                    + highest + " used)"));
        }

        String trimmedCode = safeCode.trim();
        if (REPORTER_TYPES.contains(safeType) && trimmedCode.endsWith(";")) {
            issues.add(new Issue(Severity.WARNING, "A block that returns a value is put inside an expression: its code shouldn't end with ';'"));
        }
        if (trimmedCode.isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "The block has no code: it will add nothing to the app"));
        }

        if (imports != null) {
            for (String line : imports.split("\\R")) {
                String importLine = line.trim();
                if (!importLine.isEmpty() && !IMPORT.matcher(importLine).matches()) {
                    issues.add(new Issue(Severity.ERROR, "Invalid import: " + importLine));
                }
            }
        }
        for (String[] api : DEPRECATED) {
            if (safeCode.contains(api[0])) issues.add(new Issue(Severity.WARNING, api[1]));
        }
        java.util.Set<String> permissions = new java.util.LinkedHashSet<>();
        for (String[] use : PERMISSIONS) {
            if (safeCode.contains(use[0])) permissions.add(use[1]);
        }
        for (String permission : permissions) {
            issues.add(new Issue(Severity.INFO, "Projects using this block need " + permission + " (Permission manager)"));
        }
        return issues;
    }

    /** @return the highest argument the code refers to (counting plain %s in order and %N$s by number) */
    public static int highestReference(String code) {
        Matcher m = FORMAT.matcher(code == null ? "" : code);
        int sequential = 0;
        int highest = 0;
        while (m.find()) {
            String conversion = m.group(2);
            if (conversion.equals("%") || conversion.equals("n")) continue;
            if (m.group(1) != null) {
                highest = Math.max(highest, Integer.parseInt(m.group(1).substring(0, m.group(1).length() - 1)));
            } else if (!m.group().contains("<")) {
                sequential++;
            }
        }
        return Math.max(highest, sequential);
    }

    /** The Java a use of the block gives, inputs shown by their spec name ({@code %s.text} → {@code text}). */
    public static String javaPreview(String spec, String code, String imports) {
        List<String> inputs = inputs(spec);
        String[] specWords = spec == null ? new String[0] : spec.trim().split("\\s+");
        List<String> labels = new ArrayList<>();
        for (String word : specWords) {
            if (word.startsWith("%")) {
                int dot = word.lastIndexOf('.');
                labels.add(dot > 0 && dot < word.length() - 1 ? word.substring(dot + 1) : "input" + (labels.size() + 1));
            }
        }
        Object[] values = new Object[inputs.size() + 2];
        for (int i = 0; i < inputs.size(); i++) values[i] = i < labels.size() ? labels.get(i) : "input" + (i + 1);
        values[inputs.size()] = "/* blocks */";
        values[inputs.size() + 1] = "/* else blocks */";
        StringBuilder preview = new StringBuilder();
        if (imports != null && !imports.trim().isEmpty()) {
            for (String line : imports.split("\\R")) {
                String importLine = line.trim();
                if (importLine.isEmpty()) continue;
                if (!importLine.startsWith("import ")) importLine = "import " + importLine;
                if (!importLine.endsWith(";")) importLine += ";";
                preview.append(importLine).append('\n');
            }
            preview.append('\n');
        }
        try {
            preview.append(String.format(Locale.ROOT, code == null ? "" : code, values));
        } catch (RuntimeException e) {
            preview.append("/* Failed to resolve Custom Block's code: ").append(e).append(" */");
        }
        return preview.toString();
    }
}
