package pro.sketchware.settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pro.sketchware.utility.FileUtil;

/**
 * Small persistent log for events worth keeping across sessions (crashes, builds, backups).
 * Lives in /Internal storage/.sketch_nws/logs/app.log and rotates at {@link #MAX_BYTES}.
 */
public final class AppLog {
    private static final long MAX_BYTES = 512 * 1024;

    private AppLog() {
    }

    public static File getFile() {
        return new File(FileUtil.getExternalStorageDir(), ".sketch_nws/logs/app.log");
    }

    public static void i(String tag, String message) {
        write('I', tag, message);
    }

    public static void w(String tag, String message) {
        write('W', tag, message);
    }

    public static void e(String tag, String message) {
        write('E', tag, message);
    }

    private static synchronized void write(char level, String tag, String message) {
        try {
            File file = getFile();
            File dir = file.getParentFile();
            if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                return;
            }
            if (file.length() > MAX_BYTES) {
                File rotated = new File(dir, "app.log.1");
                //noinspection ResultOfMethodCallIgnored
                rotated.delete();
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(rotated);
            }
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                writer.write(time + " " + level + "/" + tag + ": " + message + "\n");
            }
        } catch (Throwable ignored) {
            // Logging must never break the caller.
        }
    }

    /**
     * @return the last {@code maxChars} characters of the log, or an empty string.
     */
    public static synchronized String readTail(int maxChars) {
        File file = getFile();
        if (!file.isFile()) {
            return "";
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), MAX_BYTES * 2)];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
            String text = new String(data, 0, read, StandardCharsets.UTF_8);
            return text.length() > maxChars ? "…" + text.substring(text.length() - maxChars) : text;
        } catch (Throwable t) {
            return "";
        }
    }

    public static synchronized void clear() {
        File file = getFile();
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        //noinspection ResultOfMethodCallIgnored
        new File(file.getParentFile(), "app.log.1").delete();
    }
}
