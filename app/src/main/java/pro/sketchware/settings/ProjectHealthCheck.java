package pro.sketchware.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import a.a.a.GB;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import pro.sketchware.SketchApplication;

/**
 * Read-only consistency check of every project's metadata and folders.
 */
public final class ProjectHealthCheck {
    private static final Pattern PACKAGE_NAME = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");

    private ProjectHealthCheck() {
    }

    public static Report run() {
        Report report = new Report();
        Map<String, String> packageOwners = new HashMap<>();

        for (HashMap<String, Object> project : lC.a()) {
            report.projectsChecked++;
            String id = yB.c(project, "sc_id");
            String name = yB.c(project, "my_ws_name");
            String label = name.isEmpty() ? "#" + id : name;

            if (name.isEmpty()) {
                report.add(label, "Project name is empty.", true);
            }

            String pkg = yB.c(project, "my_sc_pkg_name");
            if (!PACKAGE_NAME.matcher(pkg).matches()) {
                report.add(label, "Invalid package name \"" + pkg + "\".", true);
            } else {
                String other = packageOwners.put(pkg, label);
                if (other != null) {
                    report.add(label, "Same package name as " + other + ".", false);
                }
            }

            String versionCode = yB.c(project, "sc_ver_code");
            try {
                if (Integer.parseInt(versionCode.trim()) <= 0) {
                    report.add(label, "Version code must be above 0.", true);
                }
            } catch (NumberFormatException e) {
                report.add(label, "Version code \"" + versionCode + "\" isn't a number.", true);
            }

            if (yB.c(project, "sc_ver_name").isEmpty()) {
                report.add(label, "Version name is empty.", false);
            }

            if (!new File(wq.b(id)).isDirectory()) {
                report.add(label, "No data folder yet (nothing saved).", false);
            }

            if (yB.a(project, "custom_icon") && !new File(wq.e(), id).isDirectory()) {
                report.add(label, "Custom icon is enabled but its files are missing.", false);
            }
        }

        File[] listFolders = new File(wq.n()).listFiles();
        if (listFolders != null) {
            for (File folder : listFolders) {
                if (folder.isDirectory() && !new File(folder, "project").exists()) {
                    report.add("#" + folder.getName(), "Orphan folder without a project file.", false);
                }
            }
        }

        long freeMegabytes = GB.c();
        if (freeMegabytes > 0L && freeMegabytes < 200L) {
            report.add("Device", "Only " + freeMegabytes + " MB of storage left.", freeMegabytes < 100L);
        }
        return report;
    }

    public static String describeEnvironment() {
        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long max = runtime.maxMemory() / (1024 * 1024);
        String version = "?";
        try {
            var context = SketchApplication.getContext();
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            // Not critical for a diagnostics header.
        }
        return "App " + version + "\nAndroid " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\nDevice " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\nHeap " + used + " / " + max + " MB";
    }

    public static final class Report {
        public int projectsChecked;
        public final List<Issue> issues = new ArrayList<>();

        private void add(String project, String message, boolean error) {
            issues.add(new Issue(project, message, error));
        }

        public int errorCount() {
            int count = 0;
            for (Issue issue : issues) {
                if (issue.error) count++;
            }
            return count;
        }
    }

    public record Issue(String project, String message, boolean error) {
    }
}
