package pro.sketchware.blocks.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the Java of a custom block, with the line each problem is on.
 *
 * <p>The Logic editor builds a block's code with {@code String.format(code, input1, input2, …, blocksInside,
 * blocksInElse)}, every argument a string. Problems found here without compiling are {@link Kind#STATIC}; what only
 * the Java compiler can tell (types, method names, imports of the file the code ends up in) is
 * {@link Kind#NEEDS_COMPILATION}: it is reported as a hint, never as a certainty.
 */
public final class CodeDiagnostics {
    public enum Severity {ERROR, WARNING, INFO}

    public enum Kind {STATIC, NEEDS_COMPILATION}

    /** @param line 1-based, or 0 for the block as a whole; @param fixImport a class that would be imported, or null */
    public record Diagnostic(int line, Severity severity, Kind kind, String message, String fixImport) {
        public Diagnostic(int line, Severity severity, Kind kind, String message) {
            this(line, severity, kind, message, null);
        }
    }

    private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?[-#+ 0,(<]*\\d*(?:\\.\\d+)?([a-zA-Z%])");

    private static final String[][] DEPRECATED = {
            {"AsyncTask", "AsyncTask is deprecated: use an Executor and a Handler"},
            {"ProgressDialog", "ProgressDialog is deprecated: use a progress indicator in the layout"},
            {"startActivityForResult(", "startActivityForResult is deprecated: use registerForActivityResult"},
            {"onBackPressed()", "onBackPressed is deprecated: use the OnBackPressedDispatcher"},
            {"new Handler()", "new Handler() without a Looper is deprecated: use new Handler(Looper.getMainLooper())"},
            {"android.preference.", "android.preference is deprecated: use androidx.preference"},
            {"getExternalStorageDirectory(", "getExternalStorageDirectory is deprecated since Android 10"},
            {"getDefaultDisplay()", "getDefaultDisplay is deprecated since Android 11"},
            {"Build.SERIAL", "Build.SERIAL is deprecated and needs a permission since Android 8"}};

    private static final String[][] PERMISSIONS = {
            {"VIBRATOR_SERVICE", "android.permission.VIBRATE"}, {"Vibrator", "android.permission.VIBRATE"},
            {"HttpURLConnection", "android.permission.INTERNET"}, {"openConnection(", "android.permission.INTERNET"},
            {"OkHttpClient", "android.permission.INTERNET"}, {"LocationManager", "android.permission.ACCESS_FINE_LOCATION"},
            {"Camera.open(", "android.permission.CAMERA"}, {"CameraManager", "android.permission.CAMERA"},
            {"MediaRecorder", "android.permission.RECORD_AUDIO"}, {"BluetoothAdapter", "android.permission.BLUETOOTH_CONNECT"},
            {"setWallpaper", "android.permission.SET_WALLPAPER"}};

    private static final String[][] LIBRARIES = {
            {"com.google.firebase.auth", "Firebase Authentication"}, {"FirebaseAuth", "Firebase Authentication"},
            {"FirebaseDatabase", "Firebase Realtime Database"}, {"FirebaseStorage", "Firebase Storage"},
            {"com.google.android.gms.ads", "AdMob"}, {"MobileAds", "AdMob"}, {"AdView", "AdMob"}};

    private CodeDiagnostics() {
    }

    public static List<Diagnostic> analyze(String code, String type, int paramCount, String imports) {
        List<Diagnostic> found = new ArrayList<>();
        String source = code == null ? "" : code;
        String shape = type == null || type.trim().isEmpty() || type.equals("regular") ? " " : type.trim();
        boolean substacks = shape.equals("c") || shape.equals("e");
        boolean value = shape.equals("s") || shape.equals("b") || shape.equals("d") || shape.equals("v")
                || shape.equals("a") || shape.equals("l") || shape.equals("p");
        if (source.trim().isEmpty()) {
            found.add(new Diagnostic(0, Severity.WARNING, Kind.STATIC, "The block has no code: it adds nothing to the app"));
            return found;
        }
        String[] lines = source.split("\n", -1);

        checkPlaceholders(lines, source, paramCount, substacks, found);
        checkBrackets(ImportList.stripStringsAndComments(source), found);
        checkEnding(lines, shape, value, found);

        Set<String> missingImportsReported = new HashSet<>();
        Set<String> imported = ImportList.classes(imports);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (String[] api : DEPRECATED) {
                if (line.contains(api[0])) found.add(new Diagnostic(i + 1, Severity.WARNING, Kind.STATIC, api[1]));
            }
            for (String[] use : PERMISSIONS) {
                if (line.contains(use[0])) {
                    found.add(new Diagnostic(i + 1, Severity.INFO, Kind.STATIC,
                            "Projects using this block need " + use[1] + " (Permission manager)"));
                    break;
                }
            }
            for (String[] library : LIBRARIES) {
                if (line.contains(library[0])) {
                    found.add(new Diagnostic(i + 1, Severity.INFO, Kind.NEEDS_COMPILATION,
                            "Needs the " + library[1] + " library in the project; the build fails without it"));
                    break;
                }
            }
        }
        // Simple class names that aren't imported: the generated file may import them already, only compiling tells.
        String[] stripped = ImportList.stripStringsAndComments(source).split("\n", -1);
        for (int i = 0; i < stripped.length; i++) {
            Matcher m = Pattern.compile("(?<![.\\w$\"])([A-Z][A-Za-z0-9_]*)\\b").matcher(stripped[i]);
            while (m.find()) {
                String full = ImportList.known(m.group(1));
                if (full != null && !imported.contains(full) && missingImportsReported.add(full)) {
                    found.add(new Diagnostic(i + 1, Severity.WARNING, Kind.NEEDS_COMPILATION,
                            m.group(1) + " is used without an import: write " + full + " in the code, or import it. "
                                    + "The generated file may import it already; only compiling can tell", full));
                }
            }
        }
        found.add(new Diagnostic(0, Severity.INFO, Kind.NEEDS_COMPILATION,
                "Types, method names and imports of the generated file are only checked when the app is compiled"));
        found.sort((a, b) -> a.line() != b.line() ? Integer.compare(a.line(), b.line())
                : a.severity() != b.severity() ? a.severity().compareTo(b.severity()) : 0);
        return found;
    }

    private static void checkPlaceholders(String[] lines, String source, int paramCount, boolean substacks,
                                          List<Diagnostic> found) {
        int sequential = 0;
        Set<Integer> numbered = new HashSet<>();
        int slots = paramCount + 2;
        boolean mixed = false;
        boolean anyError = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = FORMAT.matcher(line);
            int covered = 0;
            boolean[] isCovered = new boolean[line.length()];
            while (m.find()) {
                for (int k = m.start(); k < m.end(); k++) isCovered[k] = true;
                String conversion = m.group(2);
                if (conversion.equals("%") || conversion.equals("n")) continue;
                String number = m.group(1);
                int index;
                if (number != null) {
                    index = Integer.parseInt(number.substring(0, number.length() - 1));
                    numbered.add(index);
                    mixed |= sequential > 0;
                    if (index == 0) {
                        found.add(new Diagnostic(i + 1, Severity.ERROR, Kind.STATIC, "Inputs are numbered from 1: " + m.group() + " is invalid"));
                        anyError = true;
                        continue;
                    }
                } else {
                    index = ++sequential;
                    mixed |= !numbered.isEmpty();
                }
                if (!conversion.equalsIgnoreCase("s")) {
                    if (conversion.equalsIgnoreCase("b")) {
                        found.add(new Diagnostic(i + 1, Severity.WARNING, Kind.STATIC,
                                m.group() + " is always true for text inputs: use %s and compare in Java"));
                    } else {
                        found.add(new Diagnostic(i + 1, Severity.ERROR, Kind.STATIC, "Inputs are passed as text: " + m.group()
                                + " fails when the code is generated. Use %s (and Integer.parseInt(...) if a number is needed)"));
                        anyError = true;
                    }
                }
                if (index > slots) {
                    found.add(new Diagnostic(i + 1, Severity.ERROR, Kind.STATIC, "Refers to input " + index + " but the block has "
                            + paramCount + (paramCount == 1 ? " input" : " inputs") + (slots > paramCount ? " (and two block slots)" : "")));
                    anyError = true;
                } else if (index > paramCount && !substacks) {
                    found.add(new Diagnostic(i + 1, Severity.WARNING, Kind.STATIC,
                            "Input " + index + " is a block slot, which only if / if-else blocks have: here it is always empty"));
                }
            }
            for (int k = 0; k < line.length(); k++) {
                if (line.charAt(k) == '%' && !isCovered[k]) {
                    found.add(new Diagnostic(i + 1, Severity.ERROR, Kind.STATIC,
                            "A lone % can't be formatted: write %% for a percent sign"));
                    anyError = true;
                    break;
                }
            }
        }
        if (mixed) {
            found.add(new Diagnostic(0, Severity.WARNING, Kind.STATIC,
                    "The code mixes numbered (%1$s) and plain (%s) inputs: plain ones count in order, which is easy to get wrong"));
        }
        if (!anyError) {
            try {
                Object[] values = new Object[slots];
                for (int k = 0; k < slots; k++) values[k] = "x";
                String.format(Locale.ROOT, source, values);
            } catch (RuntimeException e) {
                found.add(new Diagnostic(1, Severity.ERROR, Kind.STATIC, "The code can't be turned into Java (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")"));
            }
        }
        // Inputs the code never uses.
        Set<Integer> used = new HashSet<>(numbered);
        for (int k = 1; k <= sequential; k++) used.add(k);
        for (int k = 1; k <= paramCount; k++) {
            if (!used.contains(k)) {
                found.add(new Diagnostic(0, Severity.WARNING, Kind.STATIC, "Input " + k + " isn't used by the code"));
            }
        }
    }

    private static void checkBrackets(String stripped, List<Diagnostic> found) {
        String[] lines = stripped.split("\n", -1);
        List<int[]> stack = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean openString = false;
            char quote = 0;
            for (int k = 0; k < line.length(); k++) {
                char c = line.charAt(k);
                if (openString) {
                    if (c == quote) openString = false;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    openString = true;
                    quote = c;
                } else if (c == '(' || c == '[' || c == '{') {
                    stack.add(new int[]{c, i + 1});
                } else if (c == ')' || c == ']' || c == '}') {
                    char open = c == ')' ? '(' : c == ']' ? '[' : '{';
                    if (!stack.isEmpty() && stack.get(stack.size() - 1)[0] == open) {
                        stack.remove(stack.size() - 1);
                    } else {
                        found.add(new Diagnostic(i + 1, Severity.WARNING, Kind.STATIC, "'" + c + "' has no matching '" + open + "'"));
                    }
                }
            }
            if (openString) {
                found.add(new Diagnostic(i + 1, Severity.ERROR, Kind.STATIC, "Unterminated " + (quote == '"' ? "string" : "character") + " literal"));
            }
        }
        for (int[] open : stack) {
            char close = open[0] == '(' ? ')' : open[0] == '[' ? ']' : '}';
            found.add(new Diagnostic(open[1], Severity.WARNING, Kind.STATIC,
                    "'" + (char) open[0] + "' is never closed with '" + close + "' (blocks put around other blocks may close it elsewhere)"));
        }
    }

    private static void checkEnding(String[] lines, String shape, boolean value, List<Diagnostic> found) {
        int last = -1;
        for (int i = lines.length - 1; i >= 0; i--) {
            String t = lines[i].trim();
            if (!t.isEmpty() && !t.startsWith("//")) {
                last = i;
                break;
            }
        }
        if (last < 0) return;
        String t = lines[last].trim();
        int comment = t.indexOf("//");
        if (comment > 0 && !t.substring(0, comment).contains("\"")) t = t.substring(0, comment).trim();
        if (value && t.endsWith(";")) {
            found.add(new Diagnostic(last + 1, Severity.WARNING, Kind.STATIC,
                    "A block that returns a value is put inside an expression: its code shouldn't end with ';'"));
        } else if (!value && !t.endsWith(";") && !t.endsWith("}") && !t.endsWith("{") && !t.matches("%(\\d+\\$)?s")) {
            found.add(new Diagnostic(last + 1, Severity.WARNING, Kind.STATIC, "A statement should end with ';'"));
        }
    }

    public static boolean hasErrors(List<Diagnostic> diagnostics) {
        for (Diagnostic d : diagnostics) {
            if (d.severity() == Severity.ERROR) return true;
        }
        return false;
    }

    /** @return the 0-based references to inputs the code makes: the numbers of {@code %N$s} and the plain ones in order */
    public static Set<Integer> referencedInputs(String code) {
        Set<Integer> refs = new HashSet<>();
        Matcher m = FORMAT.matcher(code == null ? "" : code);
        int sequential = 0;
        while (m.find()) {
            String conversion = m.group(2);
            if (conversion.equals("%") || conversion.equals("n")) continue;
            if (m.group(1) != null) {
                refs.add(Integer.parseInt(m.group(1).substring(0, m.group(1).length() - 1)));
            } else {
                refs.add(++sequential);
            }
        }
        return refs;
    }

    /**
     * Rewrites the numbered inputs ({@code %N$s}) after parameters were moved or removed.
     *
     * @param newNumber the new number of old input N (index N-1; 0 when the input no longer exists); inputs past the
     *                  end of the array (block slots) keep their number shifted by {@code slotShift}
     * @return the code with the numbers replaced; plain {@code %s} are left alone (their meaning is their order)
     */
    public static String renumber(String code, int[] newNumber, int slotShift) {
        Matcher m = FORMAT.matcher(code);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(code, last, m.start());
            String number = m.group(1);
            if (number == null || m.group(2).equals("%") || m.group(2).equals("n")) {
                out.append(m.group());
            } else {
                int old = Integer.parseInt(number.substring(0, number.length() - 1));
                int replacement = old >= 1 && old <= newNumber.length ? newNumber[old - 1] : old + slotShift;
                String rest = m.group().substring(1 + number.length());
                out.append('%').append(Math.max(replacement, 0)).append('$').append(rest);
            }
            last = m.end();
        }
        out.append(code.substring(last));
        return out.toString();
    }
}
