package pro.sketchware.analysis.fix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text rewrites of the project's own Java files for deprecated APIs that have one mechanical replacement. Each
 * method returns the new content and how many places it changed; content it cannot rewrite safely is left as is.
 */
public final class CodeFixes {
    /** New content and the number of places changed. */
    public record Result(String content, int changes) {
    }

    private CodeFixes() {
    }

    // ---- new Handler()

    private static final Pattern HANDLER = Pattern.compile("new\\s+Handler\\s*\\(\\s*\\)");

    /**
     * {@code new Handler()} becomes {@code new Handler(Looper.getMainLooper())}, which is what it means on the main
     * thread, where the project's code runs. A file that prepares its own loopers (Looper.prepare, HandlerThread)
     * is left alone, since a handler there may belong to another thread.
     */
    public static Result handlerLooper(String content) {
        if (content.contains("Looper.prepare") || content.contains("HandlerThread")) return new Result(content, 0);
        Matcher matcher = HANDLER.matcher(content);
        // StringBuffer: the StringBuilder overloads of appendReplacement need API 34
        StringBuffer out = new StringBuffer();
        int count = 0;
        while (matcher.find()) {
            if (inCommentOrString(content, matcher.start())) continue;
            matcher.appendReplacement(out, "new Handler(Looper.getMainLooper())");
            count++;
        }
        matcher.appendTail(out);
        if (count == 0) return new Result(content, 0);
        return new Result(addImport(out.toString(), "android.os.Looper"), count);
    }

    // ---- PendingIntent flags

    private static final Pattern PENDING_INTENT = Pattern.compile(
            "((?:[A-Za-z_][\\w]*\\.)*PendingIntent)\\s*\\.\\s*get(?:Activity|Activities|Broadcast|Service|ForegroundService)\\s*\\(");

    /**
     * Adds {@code FLAG_IMMUTABLE} to the flags (fourth argument) of PendingIntent factory calls that say neither
     * IMMUTABLE nor MUTABLE, which crash on Android 12 and later. A flags argument of {@code 0} is replaced.
     */
    public static Result pendingIntentFlags(String content) {
        StringBuilder out = new StringBuilder();
        int last = 0;
        int count = 0;
        Matcher matcher = PENDING_INTENT.matcher(content);
        while (matcher.find()) {
            if (inCommentOrString(content, matcher.start())) continue;
            int open = matcher.end() - 1;
            int close = matchingParen(content, open);
            if (close < 0) continue;
            List<int[]> args = topLevelArguments(content, open + 1, close);
            if (args.size() < 4) continue;
            String call = content.substring(open + 1, close);
            if (call.contains("FLAG_IMMUTABLE") || call.contains("FLAG_MUTABLE")) continue;
            int[] flags = args.get(3);
            String value = content.substring(flags[0], flags[1]).strip();
            String flag = matcher.group(1) + ".FLAG_IMMUTABLE";
            String replacement = value.equals("0") ? flag : value + " | " + flag;
            int start = flags[0] + leadingSpace(content, flags[0], flags[1]);
            int end = flags[1] - trailingSpace(content, flags[0], flags[1]);
            out.append(content, last, start).append(replacement);
            last = end;
            count++;
        }
        out.append(content.substring(last));
        return new Result(out.toString(), count);
    }

    // ---- android.support imports

    /** Old Support Library classes and their AndroidX or Material replacement. */
    static final Map<String, String> SUPPORT_TO_ANDROIDX = Map.ofEntries(
            Map.entry("android.support.v7.app.AppCompatActivity", "androidx.appcompat.app.AppCompatActivity"),
            Map.entry("android.support.v7.app.AlertDialog", "androidx.appcompat.app.AlertDialog"),
            Map.entry("android.support.v7.app.ActionBar", "androidx.appcompat.app.ActionBar"),
            Map.entry("android.support.v7.widget.Toolbar", "androidx.appcompat.widget.Toolbar"),
            Map.entry("android.support.v7.widget.RecyclerView", "androidx.recyclerview.widget.RecyclerView"),
            Map.entry("android.support.v7.widget.LinearLayoutManager", "androidx.recyclerview.widget.LinearLayoutManager"),
            Map.entry("android.support.v7.widget.GridLayoutManager", "androidx.recyclerview.widget.GridLayoutManager"),
            Map.entry("android.support.v7.widget.StaggeredGridLayoutManager", "androidx.recyclerview.widget.StaggeredGridLayoutManager"),
            Map.entry("android.support.v7.widget.CardView", "androidx.cardview.widget.CardView"),
            Map.entry("android.support.v4.app.Fragment", "androidx.fragment.app.Fragment"),
            Map.entry("android.support.v4.app.FragmentActivity", "androidx.fragment.app.FragmentActivity"),
            Map.entry("android.support.v4.app.FragmentManager", "androidx.fragment.app.FragmentManager"),
            Map.entry("android.support.v4.app.FragmentTransaction", "androidx.fragment.app.FragmentTransaction"),
            Map.entry("android.support.v4.app.DialogFragment", "androidx.fragment.app.DialogFragment"),
            Map.entry("android.support.v4.app.ActivityCompat", "androidx.core.app.ActivityCompat"),
            Map.entry("android.support.v4.app.NotificationCompat", "androidx.core.app.NotificationCompat"),
            Map.entry("android.support.v4.app.NotificationManagerCompat", "androidx.core.app.NotificationManagerCompat"),
            Map.entry("android.support.v4.content.ContextCompat", "androidx.core.content.ContextCompat"),
            Map.entry("android.support.v4.content.FileProvider", "androidx.core.content.FileProvider"),
            Map.entry("android.support.v4.view.ViewPager", "androidx.viewpager.widget.ViewPager"),
            Map.entry("android.support.v4.view.GravityCompat", "androidx.core.view.GravityCompat"),
            Map.entry("android.support.v4.view.ViewCompat", "androidx.core.view.ViewCompat"),
            Map.entry("android.support.v4.widget.DrawerLayout", "androidx.drawerlayout.widget.DrawerLayout"),
            Map.entry("android.support.v4.widget.SwipeRefreshLayout", "androidx.swiperefreshlayout.widget.SwipeRefreshLayout"),
            Map.entry("android.support.v4.widget.NestedScrollView", "androidx.core.widget.NestedScrollView"),
            Map.entry("android.support.annotation.NonNull", "androidx.annotation.NonNull"),
            Map.entry("android.support.annotation.Nullable", "androidx.annotation.Nullable"),
            Map.entry("android.support.design.widget.CoordinatorLayout", "androidx.coordinatorlayout.widget.CoordinatorLayout"),
            Map.entry("android.support.design.widget.FloatingActionButton", "com.google.android.material.floatingactionbutton.FloatingActionButton"),
            Map.entry("android.support.design.widget.Snackbar", "com.google.android.material.snackbar.Snackbar"),
            Map.entry("android.support.design.widget.TabLayout", "com.google.android.material.tabs.TabLayout"),
            Map.entry("android.support.design.widget.AppBarLayout", "com.google.android.material.appbar.AppBarLayout"),
            Map.entry("android.support.design.widget.CollapsingToolbarLayout", "com.google.android.material.appbar.CollapsingToolbarLayout"),
            Map.entry("android.support.design.widget.BottomNavigationView", "com.google.android.material.bottomnavigation.BottomNavigationView"),
            Map.entry("android.support.design.widget.NavigationView", "com.google.android.material.navigation.NavigationView"),
            Map.entry("android.support.design.widget.TextInputLayout", "com.google.android.material.textfield.TextInputLayout"),
            Map.entry("android.support.design.widget.TextInputEditText", "com.google.android.material.textfield.TextInputEditText"),
            Map.entry("android.support.design.widget.BottomSheetDialog", "com.google.android.material.bottomsheet.BottomSheetDialog"));

    private static final Pattern SUPPORT_IMPORT = Pattern.compile("^(\\s*import\\s+)(android\\.support\\.[\\w.]+)(\\s*;)", Pattern.MULTILINE);

    /**
     * Replaces {@code import android.support...} lines whose class has a known AndroidX or Material replacement.
     * Imports with no known replacement (and wildcard imports) are left, and still reported by the analysis.
     */
    public static Result supportImports(String content) {
        Matcher matcher = SUPPORT_IMPORT.matcher(content);
        StringBuffer out = new StringBuffer();
        int count = 0;
        while (matcher.find()) {
            String replacement = SUPPORT_TO_ANDROIDX.get(matcher.group(2));
            if (replacement == null) continue;
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + replacement + matcher.group(3)));
            count++;
        }
        matcher.appendTail(out);
        return new Result(count == 0 ? content : out.toString(), count);
    }

    // ---- helpers

    /** Adds {@code import name;} after the last import (or the package line) unless the class is already imported. */
    static String addImport(String content, String name) {
        String pkg = name.substring(0, name.lastIndexOf('.'));
        if (Pattern.compile("^\\s*import\\s+(" + Pattern.quote(name) + "|" + Pattern.quote(pkg) + "\\.\\*)\\s*;", Pattern.MULTILINE).matcher(content).find()) {
            return content;
        }
        String line = "import " + name + ";\n";
        Matcher imports = Pattern.compile("^\\s*import\\s+[\\w.*]+\\s*;[^\\n]*\\n?", Pattern.MULTILINE).matcher(content);
        int at = -1;
        while (imports.find()) at = imports.end();
        if (at < 0) {
            Matcher pack = Pattern.compile("^\\s*package\\s+[\\w.]+\\s*;[^\\n]*\\n?", Pattern.MULTILINE).matcher(content);
            if (pack.find()) {
                return content.substring(0, pack.end()) + "\n" + line + content.substring(pack.end());
            }
            return line + content;
        }
        String before = content.substring(0, at);
        if (!before.endsWith("\n")) before += "\n";
        return before + line + content.substring(at);
    }

    /** True when {@code offset} is inside a comment or a string or char literal. */
    static boolean inCommentOrString(String text, int offset) {
        boolean line = false, block = false, string = false, chr = false;
        for (int i = 0; i < offset && i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (line) {
                if (c == '\n') line = false;
            } else if (block) {
                if (c == '*' && next == '/') {
                    block = false;
                    i++;
                }
            } else if (string || chr) {
                if (c == '\\') i++;
                else if (string && c == '"') string = false;
                else if (chr && c == '\'') chr = false;
            } else if (c == '/' && next == '/') {
                line = true;
            } else if (c == '/' && next == '*') {
                block = true;
                i++;
            } else if (c == '"') {
                string = true;
            } else if (c == '\'') {
                chr = true;
            }
        }
        return line || block || string || chr;
    }

    /** Index of the parenthesis closing the one at {@code open}, skipping strings and comments; -1 if none. */
    static int matchingParen(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i);
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                int end = text.indexOf('\n', i);
                if (end < 0) return -1;
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                if (end < 0) return -1;
                i = end + 1;
                continue;
            }
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static int skipLiteral(String text, int start) {
        char quote = text.charAt(start);
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') i++;
            else if (c == quote) return i;
        }
        return text.length() - 1;
    }

    /** Ranges [start, end) of the top-level, comma-separated arguments between {@code from} and {@code to}. */
    static List<int[]> topLevelArguments(String text, int from, int to) {
        List<int[]> args = new ArrayList<>();
        int depth = 0, start = from;
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == '<' && depth >= 0) {
                // generics such as new HashMap<String, Object>() would split here; treat <...> as nesting when it closes
                int close = text.indexOf('>', i);
                if (close > 0 && close < to && text.substring(i + 1, close).matches("[\\w\\s,.?<]*")) i = close;
            } else if (c == ',' && depth == 0) {
                args.add(new int[]{start, i});
                start = i + 1;
            }
        }
        if (to > start || !args.isEmpty()) args.add(new int[]{start, to});
        return args;
    }

    private static int leadingSpace(String text, int from, int to) {
        int n = 0;
        while (from + n < to && Character.isWhitespace(text.charAt(from + n))) n++;
        return n;
    }

    private static int trailingSpace(String text, int from, int to) {
        int n = 0;
        while (to - n - 1 >= from && Character.isWhitespace(text.charAt(to - n - 1))) n++;
        return n;
    }
}
