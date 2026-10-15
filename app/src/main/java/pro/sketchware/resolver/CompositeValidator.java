package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.util.List;

/** Runs validators in order and stops at the first that refuses: the cheap checks first, then AAPT2. */
public final class CompositeValidator implements ResourceValidator {
    private final List<ResourceValidator> validators;

    public CompositeValidator(ResourceValidator... validators) {
        this.validators = List.of(validators);
    }

    /** The built-in checks, plus AAPT2 when its binary can run. */
    public static ResourceValidator standard(Aapt2Validator aapt2) {
        return aapt2 != null && aapt2.isAvailable() ? new CompositeValidator(new BuiltInValidator(), aapt2) : new BuiltInValidator();
    }

    @NonNull
    @Override
    public Result validateFile(@NonNull String relativePath, @NonNull byte[] content) {
        for (ResourceValidator validator : validators) {
            Result result = validator.validateFile(relativePath, content);
            if (!result.ok()) return result;
        }
        return Result.OK;
    }

    @NonNull
    @Override
    public Result validateValues(@NonNull String relativePath, @NonNull String xml) {
        for (ResourceValidator validator : validators) {
            Result result = validator.validateValues(relativePath, xml);
            if (!result.ok()) return result;
        }
        return Result.OK;
    }
}
