package pro.sketchware.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.IntentCompat;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import pro.sketchware.R;
import pro.sketchware.activities.main.activities.MainActivity;
import pro.sketchware.auth.AuthDecision;
import pro.sketchware.auth.AuthGate;
import pro.sketchware.auth.LoginActivity;

/** The Google sign-in page and the rule that decides when the app shows it. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application.class)
public class LoginScreenTest {

    private static Context app() {
        RuntimeEnvironment.setQualifiers("+notnight");
        try {
            ThemeHarness.prepareApplication();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return ApplicationProvider.getApplicationContext();
    }

    private static LoginActivity login(Context app) {
        Intent intent = LoginActivity.intent(app, new Intent(app, MainActivity.class));
        return Robolectric.buildActivity(LoginActivity.class, intent).setup().get();
    }

    @Test
    public void aBuildWithoutFirebaseOpensTheAppInsteadOfBlockingIt() {
        Context app = app();
        assertEquals(AuthDecision.Result.NOT_CONFIGURED, AuthGate.decision(app));
        assertFalse(AuthGate.isRequired(app));
        assertEquals("", AuthGate.webClientId(app));
    }

    @Test
    public void thePageOffersTheGoogleButton() {
        LoginActivity activity = login(app());
        TextView button = activity.findViewById(R.id.btn_google_sign_in);
        assertNotNull(button);
        assertEquals(activity.getString(R.string.auth_button), button.getText().toString());
        assertTrue(button.isEnabled());
        assertEquals(View.GONE, activity.findViewById(R.id.login_error).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.login_progress).getVisibility());
    }

    @Test
    public void tappingWithoutASetUpBuildExplainsInsteadOfFailingSilently() {
        LoginActivity activity = login(app());
        activity.findViewById(R.id.btn_google_sign_in).performClick();
        TextView error = activity.findViewById(R.id.login_error);
        assertEquals(View.VISIBLE, error.getVisibility());
        assertEquals(activity.getString(R.string.auth_error_not_configured), error.getText().toString());
        assertTrue("the button stays usable", activity.findViewById(R.id.btn_google_sign_in).isEnabled());
        assertFalse(activity.isFinishing());
    }

    @Test
    public void backLeavesTheAppBecauseTheSignInIsMandatory() {
        LoginActivity activity = login(app());
        activity.getOnBackPressedDispatcher().onBackPressed();
        assertTrue(activity.isFinishing());
    }

    @Test
    public void theLaunchIntentIsKeptToOpenAfterSigningIn() {
        Context app = app();
        Intent launch = new Intent(Intent.ACTION_VIEW).setClass(app, MainActivity.class).putExtra("keep", "me");
        Intent login = LoginActivity.intent(app, launch);
        Intent next = IntentCompat.getParcelableExtra(login, "next", Intent.class);
        assertNotNull(next);
        assertEquals(Intent.ACTION_VIEW, next.getAction());
        assertEquals("me", next.getStringExtra("keep"));
    }
}
