package pro.sketchware.evolve;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Screenshots of posts, in Firebase Storage under {@code evolve/<uid>/<postId>/<n>.jpg}. A picture is scaled down and
 * re-encoded as JPEG on the phone first, so what is uploaded is always a small image of the app's own making, never
 * the file the user picked. The storage rules (docs/EVOLVE.md) hold the same limits on the server.
 */
public final class EvolveStorage {

    public static final int MAX_SIDE = 1280;

    private EvolveStorage() {
    }

    /** @return the scale a {@code width x height} picture is shown at so that its longest side is at most {@code maxSide} (1 or smaller) */
    public static float scaleFor(int width, int height, int maxSide) {
        int longest = Math.max(width, height);
        return longest <= maxSide || longest <= 0 ? 1f : (float) maxSide / longest;
    }

    /**
     * Reads {@code uri} as a picture, scales it down and encodes it as JPEG of at most {@link PostRules#MAX_SCREENSHOT_BYTES}
     * (lowering the quality until it fits). Call it off the main thread.
     */
    public static byte[] prepare(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("cannot open the picture");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("not a picture");
        float scale = scaleFor(bounds.outWidth, bounds.outHeight, MAX_SIDE);
        BitmapFactory.Options options = new BitmapFactory.Options();
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
        options.inSampleSize = sample;
        Bitmap decoded;
        try (InputStream in = resolver.openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) throw new IOException("not a picture");
        Bitmap scaled = decoded;
        float finalScale = scaleFor(decoded.getWidth(), decoded.getHeight(), MAX_SIDE);
        if (finalScale < 1f) {
            scaled = Bitmap.createScaledBitmap(decoded, Math.round(decoded.getWidth() * finalScale), Math.round(decoded.getHeight() * finalScale), true);
        }
        try {
            for (int quality = 85; quality >= 35; quality -= 10) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out);
                if (out.size() <= PostRules.MAX_SCREENSHOT_BYTES) return out.toByteArray();
            }
            throw new IOException("the picture is too large");
        } finally {
            if (scaled != decoded) scaled.recycle();
            decoded.recycle();
        }
    }

    /** Uploads one picture and gives back its address (with the access token Storage adds to it). */
    public static void upload(String uid, String postId, int index, byte[] jpeg, String timeoutMessage, EvolveRepository.Callback<String> callback) {
        EvolveRepository.Once<String> once = new EvolveRepository.Once<>(callback, timeoutMessage);
        try {
            StorageReference ref = FirebaseStorage.getInstance().getReference().child("evolve").child(uid).child(postId)
                    .child(System.currentTimeMillis() / 1000 + "_" + index + ".jpg");
            StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
            ref.putBytes(jpeg, metadata)
                    .addOnSuccessListener(task -> ref.getDownloadUrl()
                            .addOnSuccessListener(uri -> once.success(uri.toString()))
                            .addOnFailureListener(e -> once.error(message(e))))
                    .addOnFailureListener(e -> once.error(message(e)));
        } catch (RuntimeException e) {
            // No storage bucket in this build's Firebase configuration
            once.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** Removes pictures that are no longer used. Best effort: a picture that can't be removed is just left behind. */
    public static void delete(List<String> urls) {
        for (String url : urls) {
            if (!PostRules.isStorageUrl(url)) continue;
            try {
                FirebaseStorage.getInstance().getReferenceFromUrl(url).delete();
            } catch (RuntimeException ignored) {
                // left behind
            }
        }
    }

    private static String message(@Nullable Exception e) {
        return e == null || e.getMessage() == null ? "upload failed" : e.getMessage();
    }
}
