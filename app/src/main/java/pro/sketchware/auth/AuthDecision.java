package pro.sketchware.auth;

/**
 * Whether the sign-in page must be shown. Kept free of Android and Firebase so it can be tested directly.
 */
public final class AuthDecision {

    public enum Result {
        /** Google sign-in is set up in this build and nobody is signed in: the page blocks the app. */
        REQUIRED,
        /** Somebody is signed in: the app opens. */
        SIGNED_IN,
        /**
         * This build has no Firebase configuration (a fork or CI build without the google-services secret), so
         * nobody could ever sign in. Blocking would lock the app forever, so it opens.
         */
        NOT_CONFIGURED
    }

    private AuthDecision() {
    }

    /**
     * @param firebaseReady      whether a Firebase app was initialised from this build's google-services.json
     * @param webClientIdPresent whether that file gave this build a web OAuth client id
     * @param signedIn           whether Firebase already holds a signed-in user
     */
    public static Result decide(boolean firebaseReady, boolean webClientIdPresent, boolean signedIn) {
        if (!firebaseReady || !webClientIdPresent) {
            return Result.NOT_CONFIGURED;
        }
        return signedIn ? Result.SIGNED_IN : Result.REQUIRED;
    }
}
