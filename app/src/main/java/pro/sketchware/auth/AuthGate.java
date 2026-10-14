package pro.sketchware.auth;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import pro.sketchware.settings.AppLog;

/**
 * Entry point of the Google sign-in requirement: tells MainActivity whether to show {@link LoginActivity}, and
 * signs the user out.
 */
public final class AuthGate {

    private static final String WEB_CLIENT_ID_RESOURCE = "default_web_client_id";

    private AuthGate() {
    }

    /** @return whether the app must show the sign-in page before anything else. */
    public static boolean isRequired(@NonNull Context context) {
        return decision(context) == AuthDecision.Result.REQUIRED;
    }

    public static AuthDecision.Result decision(@NonNull Context context) {
        boolean firebaseReady = isFirebaseReady(context);
        return AuthDecision.decide(firebaseReady, !webClientId(context).isEmpty(), firebaseReady && currentUser(context) != null);
    }

    /**
     * @return the OAuth web client id the google-services plugin generated from this build's google-services.json,
     * or "" if the build has none. It is looked up by name because the resource doesn't exist in builds without
     * that file.
     */
    @NonNull
    public static String webClientId(@NonNull Context context) {
        int id = context.getResources().getIdentifier(WEB_CLIENT_ID_RESOURCE, "string", context.getPackageName());
        return id == 0 ? "" : context.getString(id);
    }

    public static boolean isFirebaseReady(@NonNull Context context) {
        try {
            return !FirebaseApp.getApps(context).isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** @return the signed-in user, or null (also when Firebase isn't set up in this build). */
    @Nullable
    public static FirebaseUser currentUser(@NonNull Context context) {
        if (!isFirebaseReady(context)) {
            return null;
        }
        try {
            return FirebaseAuth.getInstance().getCurrentUser();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Signs out of Firebase and of the Google credential on this device, then shows the sign-in page. The page
     * opens even if clearing the credential fails: the Firebase session, which is what the gate checks, is gone.
     *
     * @param next the intent to open once the user signs in again
     */
    public static void signOut(@NonNull Context context, @NonNull Intent next) {
        if (isFirebaseReady(context)) {
            FirebaseAuth.getInstance().signOut();
        }
        // From the application context and a new task: the activity that asked may be gone by the time this runs,
        // and nothing from the signed-out session should stay in the back stack.
        Context app = context.getApplicationContext();
        Runnable showLogin = () -> app.startActivity(LoginActivity.intent(app, next)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            CredentialManager.create(context).clearCredentialStateAsync(new ClearCredentialStateRequest(), null,
                    ContextCompat.getMainExecutor(context), new CredentialManagerCallback<>() {
                        @Override
                        public void onResult(Void result) {
                            showLogin.run();
                        }

                        @Override
                        public void onError(@NonNull ClearCredentialException e) {
                            AppLog.w("Auth", "Clearing the Google credential failed: " + e.getType());
                            showLogin.run();
                        }
                    });
        } catch (RuntimeException e) {
            AppLog.w("Auth", "Clearing the Google credential failed: " + e);
            showLogin.run();
        }
    }
}
