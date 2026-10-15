package pro.sketchware.resolver.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import pro.sketchware.resolver.FileInspector;
import pro.sketchware.resolver.IconLibrary;
import pro.sketchware.resolver.PlannedChange;
import pro.sketchware.resolver.ResourceInventory;
import pro.sketchware.resolver.Suggestion;
import pro.sketchware.resolver.SvgToVector;
import pro.sketchware.resolver.VectorModel;
import pro.sketchware.resolver.VectorXml;

/** Fills a {@link VectorPreviewView} with what a file, a suggestion or a planned change looks like. Never throws. */
public final class Previews {
    private static final int MAX_BITMAP_SIDE = 160;
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private Previews() {
    }

    public static void show(VectorPreviewView view, @Nullable IconLibrary library, String colour, Suggestion suggestion) {
        view.clear();
        try {
            if (suggestion.kind() == Suggestion.Kind.MATERIAL_ICON && library != null && suggestion.iconName() != null && suggestion.iconStyle() != null) {
                view.showVector(SvgToVector.convert(library.svg(suggestion.iconName(), suggestion.iconStyle()), colour));
            } else if (suggestion.source() != null && suggestion.source().kind() == ResourceInventory.Kind.FILE) {
                showFile(view, new File(suggestion.source().path()));
            } else if (suggestion.source() != null && suggestion.source().value() != null) {
                showValue(view, suggestion.source().type(), suggestion.source().value());
            }
        } catch (IOException | SvgToVector.Unsupported | RuntimeException e) {
            view.clear();
        }
    }

    public static void show(VectorPreviewView view, PlannedChange change) {
        view.clear();
        if (change.kind() == PlannedChange.Kind.VALUE && change.entry() != null) {
            showValue(view, change.entry().type(), change.entry().value());
        } else if (change.content() != null) {
            showBytes(view, change.path(), change.content());
        }
    }

    public static void show(VectorPreviewView view, FileInspector.Inspection inspection) {
        view.clear();
        if (inspection.preview() != null) {
            view.showVector(inspection.preview());
        } else if (inspection.format() == FileInspector.Format.PNG || inspection.format() == FileInspector.Format.WEBP) {
            view.showBitmap(decode(inspection.content()));
        }
    }

    private static void showValue(VectorPreviewView view, String type, String value) {
        if (type.equals("color") && value.matches("#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{8}")) {
            long parsed = Long.parseLong(value.substring(1), 16);
            view.showSwatch(value.length() == 7 ? (int) (0xFF000000L | parsed) : (int) parsed);
        }
    }

    private static void showFile(VectorPreviewView view, File file) throws IOException {
        if (!file.isFile() || file.length() > MAX_FILE_BYTES) return;
        showBytes(view, file.getName(), Files.readAllBytes(file.toPath()));
    }

    private static void showBytes(VectorPreviewView view, String name, byte[] bytes) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".xml")) {
            VectorModel model = VectorXml.read(new String(bytes, StandardCharsets.UTF_8));
            if (model != null) view.showVector(model);
        } else {
            view.showBitmap(decode(bytes));
        }
    }

    /** Decodes an image no larger than the tile needs, so a big picture doesn't fill memory for a thumbnail. */
    @Nullable
    static Bitmap decode(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / MAX_BITMAP_SIDE);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
