package pro.sketchware.auth;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import pro.sketchware.auth.AuthDecision.Result;

public class AuthDecisionTest {

    @Test
    public void configuredBuildWithNobodySignedInBlocksTheApp() {
        assertEquals(Result.REQUIRED, AuthDecision.decide(true, true, false));
    }

    @Test
    public void aSignedInUserOpensTheApp() {
        assertEquals(Result.SIGNED_IN, AuthDecision.decide(true, true, true));
    }

    @Test
    public void buildsWithoutFirebaseNeverBlockBecauseNobodyCouldSignIn() {
        assertEquals(Result.NOT_CONFIGURED, AuthDecision.decide(false, false, false));
        assertEquals(Result.NOT_CONFIGURED, AuthDecision.decide(false, true, false));
    }

    @Test
    public void firebaseWithoutAWebClientIdIsNotUsableForGoogleSignIn() {
        assertEquals(Result.NOT_CONFIGURED, AuthDecision.decide(true, false, false));
        assertEquals(Result.NOT_CONFIGURED, AuthDecision.decide(true, false, true));
    }
}
