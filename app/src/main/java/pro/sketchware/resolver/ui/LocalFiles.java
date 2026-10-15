package pro.sketchware.resolver.ui;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads the files the person picked, with a size limit so a wrong pick can't fill the memory. */
final class LocalFiles {
    /** More than any icon or image a project needs; bigger files are refused, not read. */
    static final long MAX_BYTES = 8L * 1024 * 1024;

    private LocalFiles() {
    }

    /** @return the name the file has, or "file" when the provider doesn't say */
    static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (RuntimeException ignored) {
            // fall through to the last path segment
        }
        String last = uri.getLastPathSegment();
        if (last != null) {
            int slash = last.lastIndexOf('/');
            return slash >= 0 ? last.substring(slash + 1) : last;
        }
        return "file";
    }

    /** @throws IOException when the file can't be opened or is larger than {@link #MAX_BYTES} */
    static byte[] read(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("Can't open the file");
            return readCapped(in, MAX_BYTES);
        } catch (SecurityException e) {
            throw new IOException("No permission to read the file");
        }
    }

    static byte[] readCapped(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > max) throw new IOException("The file is larger than " + (max / (1024 * 1024)) + " MB");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Nullable
    static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
