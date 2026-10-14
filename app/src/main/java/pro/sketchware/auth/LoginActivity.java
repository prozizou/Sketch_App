package pro.sketchware.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.GoogleAuthProvider;

import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.settings.AppLog;

/**
 * The page shown at launch until the user signs in with Google. Signing in goes through Credential Manager (the
 * system's Google account sheet) and then Firebase Authentication; the session is kept by Firebase, so the page
 * only comes back after a sign-out.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String EXTRA_NEXT = "next";

    private MaterialButton signInButton;
    private View progress;
    private TextView error;
    private CancellationSignal cancellation;
    private boolean busy;

    /**
     * @param next what to open once signed in (the launch intent, so a project file opened with the app isn't lost)
     */
    public static Intent intent(@NonNull Context context, @NonNull Intent next) {
        return new Intent(context, LoginActivity.class)
                .putExtra(EXTRA_NEXT, new Intent(next))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        View root = findViewById(R.id.login_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        // The sign-in is mandatory: going back leaves the app instead of reaching the screen behind.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
            }
        });

        signInButton = findViewById(R.id.btn_google_sign_in);
        progress = findViewById(R.id.login_progress);
        error = findViewById(R.id.login_error);
        signInButton.setOnClickListener(v -> signIn());

        if (AuthGate.currentUser(this) != null) {
            openApp();
        }
    }

    @Override
    protected void onDestroy() {
        if (cancellation != null) {
            cancellation.cancel();
        }
        super.onDestroy();
    }

    private void signIn() {
        if (busy) {
            return;
        }
        String clientId = AuthGate.webClientId(this);
        if (clientId.isEmpty() || !AuthGate.isFirebaseReady(this)) {
            showError(getString(R.string.auth_error_not_configured));
            return;
        }
        setBusy(true);
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new GetSignInWithGoogleOption.Builder(clientId).build())
                .build();
        cancellation = new CancellationSignal();
        try {
            CredentialManager.create(this).getCredentialAsync(this, request, cancellation,
                    ContextCompat.getMainExecutor(this), new CredentialManagerCallback<>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            onGoogleCredential(result.getCredential());
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException e) {
                            onCredentialError(e);
                        }
                    });
        } catch (RuntimeException e) {
            AppLog.e("Auth", "Credential Manager failed to start: " + e);
            fail(getString(R.string.auth_error_generic, e.getClass().getSimpleName()));
        }
    }

    private void onGoogleCredential(@NonNull Credential credential) {
        if (!(credential instanceof CustomCredential custom)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(custom.getType())) {
            AppLog.e("Auth", "Unexpected credential type " + credential.getType());
            fail(getString(R.string.auth_error_generic, credential.getType()));
            return;
        }
        String idToken;
        try {
            idToken = GoogleIdTokenCredential.createFrom(custom.getData()).getIdToken();
        } catch (Exception e) {
            // createFrom throws GoogleIdTokenParsingException, which Kotlin doesn't declare, so Java can't name it.
            AppLog.e("Auth", "Could not read the Google ID token: " + e.getClass().getSimpleName());
            fail(getString(R.string.auth_error_generic, e.getClass().getSimpleName()));
            return;
        }
        FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        openApp();
                        return;
                    }
                    Exception e = task.getException();
                    if (e instanceof FirebaseNetworkException) {
                        fail(getString(R.string.auth_error_network));
                    } else if (e instanceof FirebaseAuthException auth) {
                        AppLog.e("Auth", "Firebase rejected the sign-in: " + auth.getErrorCode());
                        fail(getString(R.string.auth_error_generic, auth.getErrorCode()));
                    } else {
                        AppLog.e("Auth", "Firebase sign-in failed: " + e);
                        fail(getString(R.string.auth_error_generic, e == null ? "unknown" : e.getClass().getSimpleName()));
                    }
                });
    }

    private void onCredentialError(@NonNull GetCredentialException e) {
        if (e instanceof GetCredentialCancellationException) {
            // The user closed the Google sheet: stay here, no error to explain.
            setBusy(false);
        } else if (e instanceof NoCredentialException) {
            fail(getString(R.string.auth_error_no_account));
        } else if (e instanceof GetCredentialProviderConfigurationException) {
            fail(getString(R.string.auth_error_play_services));
        } else {
            AppLog.e("Auth", "Credential Manager error " + e.getType());
            fail(getString(R.string.auth_error_generic, e.getType()));
        }
    }

    private void openApp() {
        Intent next = IntentCompat.getParcelableExtra(getIntent(), EXTRA_NEXT, Intent.class);
        startActivity(next != null ? next : new Intent(this, MainActivity.class));
        finish();
    }

    private void fail(String message) {
        setBusy(false);
        showError(message);
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        signInButton.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) {
            error.setVisibility(View.GONE);
        }
    }
}
