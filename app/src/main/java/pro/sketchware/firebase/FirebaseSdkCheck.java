package pro.sketchware.firebase;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

/**
 * Initialises a throw-away {@link FirebaseApp} with the configuration, using the Firebase SDK bundled in NWS, then
 * deletes it. It needs no network and changes nothing: it shows whether the SDK accepts the app id, API key, project id,
 * database URL and storage bucket.
 */
public final class FirebaseSdkCheck {
    private FirebaseSdkCheck() {
    }

    public static FirebaseChecks.Result run(Context context, GoogleServicesConfig config) {
        if (!config.isValid()) return new FirebaseChecks.Result(FirebaseChecks.Status.SKIPPED, "Fix the errors in the configuration first.");
        String name = "nws-check-" + System.nanoTime();
        FirebaseApp app = null;
        try {
            FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                    .setApplicationId(config.appId())
                    .setApiKey(config.apiKey())
                    .setProjectId(config.projectId());
            if (!config.databaseUrl().isEmpty()) builder.setDatabaseUrl(config.databaseUrl());
            if (!config.storageBucket().isEmpty()) builder.setStorageBucket(config.storageBucket());
            app = FirebaseApp.initializeApp(context.getApplicationContext(), builder.build(), name);
            FirebaseOptions options = app.getOptions();
            if (!config.appId().equals(options.getApplicationId()) || !config.apiKey().equals(options.getApiKey())) {
                return new FirebaseChecks.Result(FirebaseChecks.Status.FAIL, "The SDK did not keep the values it was given.");
            }
            return new FirebaseChecks.Result(FirebaseChecks.Status.PASS, "The Firebase SDK accepts this configuration.");
        } catch (RuntimeException e) {
            return new FirebaseChecks.Result(FirebaseChecks.Status.FAIL, "The Firebase SDK refused the configuration: " + hide(config, e));
        } finally {
            if (app != null) {
                try {
                    app.delete();
                } catch (RuntimeException ignored) {
                    // the throw-away instance is only a check
                }
            }
        }
    }

    /** The exception's text without the credentials, in case the SDK echoes them. */
    static String hide(GoogleServicesConfig config, Throwable e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        for (String secret : new String[]{config.apiKey(), config.appId()}) {
            if (!secret.isEmpty()) message = message.replace(secret, GoogleServicesConfig.mask(secret));
        }
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }
}
