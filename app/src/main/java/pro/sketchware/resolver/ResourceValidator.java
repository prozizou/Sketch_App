package pro.sketchware.resolver;

import androidx.annotation.NonNull;

/**
 * Decides whether a resource the resolver is about to write would be accepted by the build. It is asked about the
 * exact bytes that would be written, before anything is.
 */
public interface ResourceValidator {
    /**
     * @param ok      whether the build would accept it
     * @param message why not (one line a person can act on), or "" when ok
     */
    record Result(boolean ok, @NonNull String message) {
        public static final Result OK = new Result(true, "");

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /** @param relativePath the file's place under {@code res}, like {@code drawable-xhdpi/ic_add.png} */
    @NonNull
    Result validateFile(@NonNull String relativePath, @NonNull byte[] content);

    /** @param relativePath a values file's place under {@code res}, like {@code values/colors.xml}; {@code xml} is the whole file after the change */
    @NonNull
    Result validateValues(@NonNull String relativePath, @NonNull String xml);
}
