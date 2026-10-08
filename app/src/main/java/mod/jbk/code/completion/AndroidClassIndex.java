package mod.jbk.code.completion;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.settings.AppLog;

/**
 * The classes a project can use: the Android SDK and the built-in libraries, once they are extracted by
 * the first build, or the SDK read straight from the app's assets until then.
 */
public final class AndroidClassIndex {
    private static final String TAG = "ClassIndex";
    private static ClassIndex cached;
    private static String cachedStamp;

    private AndroidClassIndex() {
    }

    public static synchronized ClassIndex get(Context context) {
        File libs = new File(context.getFilesDir(), "libs");
        File androidJar = new File(libs, "android.jar");
        File builtIns = new File(libs, "libs");
        String stamp = androidJar.lastModified() + ":" + builtIns.lastModified();
        if (cached != null && stamp.equals(cachedStamp)) return cached;

        List<ClassIndex> parts = new ArrayList<>();
        try {
            parts.add(androidJar.isFile() ? ClassIndex.fromJar(androidJar) : fromAssets(context));
        } catch (IOException | RuntimeException e) {
            AppLog.e(TAG, "Couldn't read the Android SDK classes: " + e);
        }
        File[] libraries = builtIns.listFiles();
        if (libraries != null) {
            for (File library : libraries) {
                File jar = new File(library, "classes.jar");
                if (!jar.isFile()) continue;
                try {
                    parts.add(ClassIndex.fromJar(jar));
                } catch (IOException | RuntimeException e) {
                    AppLog.e(TAG, "Couldn't read " + jar + ": " + e);
                }
            }
        }
        cached = ClassIndex.merge(parts.toArray(new ClassIndex[0]));
        cachedStamp = stamp;
        return cached;
    }

    /** Reading the 24 MB archive takes a moment, so the names are kept in a cache file per installed version. */
    private static ClassIndex fromAssets(Context context) throws IOException {
        File cacheFile = new File(context.getCacheDir(), "completion/android-classes-" + new File(context.getApplicationInfo().sourceDir).lastModified() + ".txt");
        if (cacheFile.isFile()) {
            try (BufferedReader reader = Files.newBufferedReader(cacheFile.toPath(), StandardCharsets.UTF_8)) {
                List<String> names = new ArrayList<>();
                for (String line = reader.readLine(); line != null; line = reader.readLine()) names.add(line);
                return ClassIndex.of(names);
            }
        }

        ClassIndex index;
        try (InputStream in = context.getAssets().open("libs/android.jar.zip")) {
            index = ClassIndex.fromArchiveOfJars(in);
        }
        if (index.size() > 0 && cacheFile.getParentFile() != null && (cacheFile.getParentFile().isDirectory() || cacheFile.getParentFile().mkdirs())) {
            try (BufferedWriter writer = Files.newBufferedWriter(cacheFile.toPath(), StandardCharsets.UTF_8)) {
                for (String name : index.all()) {
                    writer.write(name);
                    writer.newLine();
                }
            } catch (IOException e) {
                cacheFile.delete();
            }
        }
        return index;
    }
}
