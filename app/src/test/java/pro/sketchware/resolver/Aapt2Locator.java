package pro.sketchware.resolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;

/** Finds the AAPT2 of the Android SDK on the machine running the tests; the tests that need it skip when there is none. */
final class Aapt2Locator {
    private Aapt2Locator() {
    }

    /** @return the aapt2 binary of the newest build-tools, or null */
    static File find() {
        for (String root : new String[]{System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"), sdkDirFromLocalProperties()}) {
            if (root == null) continue;
            File[] tools = new File(root, "build-tools").listFiles(File::isDirectory);
            if (tools == null) continue;
            Arrays.sort(tools, Comparator.comparing(File::getName).reversed());
            for (File dir : tools) {
                File aapt2 = new File(dir, "aapt2");
                if (aapt2.isFile() && aapt2.canExecute()) return aapt2;
            }
        }
        return null;
    }

    private static String sdkDirFromLocalProperties() {
        for (String path : new String[]{"../local.properties", "local.properties"}) {
            File file = new File(path);
            if (!file.isFile()) continue;
            Properties properties = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                properties.load(in);
                return properties.getProperty("sdk.dir");
            } catch (IOException ignored) {
                // try the next
            }
        }
        return null;
    }

    /** @return the android.jar of the newest platform of the SDK, or null */
    static File androidJar() {
        for (String root : new String[]{System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"), sdkDirFromLocalProperties()}) {
            if (root == null) continue;
            File[] platforms = new File(root, "platforms").listFiles(File::isDirectory);
            if (platforms == null) continue;
            Arrays.sort(platforms, Comparator.comparing(File::getName).reversed());
            for (File dir : platforms) {
                File jar = new File(dir, "android.jar");
                if (jar.isFile()) return jar;
            }
        }
        return null;
    }
}
