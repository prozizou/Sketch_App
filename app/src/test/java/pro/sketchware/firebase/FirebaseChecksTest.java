package pro.sketchware.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pro.sketchware.firebase.FirebaseChecks.Response;
import pro.sketchware.firebase.FirebaseChecks.Status;

/** The interpretation of the answers, with canned responses: nothing here touches the network. */
public class FirebaseChecksTest {
    private static final GoogleServicesConfig CONFIG = GoogleServicesConfig.parse(GoogleServicesConfigTest.file("com.demo.app"), "com.demo.app");

    private static final class Recorder implements FirebaseChecks.Transport {
        final List<String> calls = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        final Response answer;
        final IOException failure;

        Recorder(Response answer) {
            this.answer = answer;
            this.failure = null;
        }

        Recorder(IOException failure) {
            this.answer = null;
            this.failure = failure;
        }

        @Override
        public Response send(String method, String url, Map<String, String> h, String body) throws IOException {
            calls.add(method + " " + url);
            headers.add(h);
            if (failure != null) throw failure;
            return answer;
        }
    }

    private static Response error(int code, String message, String status, String reason) {
        String details = reason == null ? "" : ",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"" + reason + "\"}]";
        return new Response(code, "{\"error\":{\"code\":" + code + ",\"message\":\"" + message + "\",\"status\":\"" + status + "\"" + details + "}}");
    }

    // ---- authentication

    @Test
    public void anAcceptedKeyPassesWithoutCreatingAnything() {
        Recorder t = new Recorder(error(400, "INVALID_ID_TOKEN", "INVALID_ARGUMENT", null));
        FirebaseChecks.Result r = FirebaseChecks.authentication(CONFIG, t);
        assertEquals(Status.PASS, r.status);
        // One lookup of a made-up token: it is the only request, and it is not a sign-up or a sign-in.
        assertEquals(1, t.calls.size());
        assertEquals("POST https://identitytoolkit.googleapis.com/v1/accounts:lookup", t.calls.get(0));
        assertFalse(t.calls.get(0).contains("signUp"));
        assertFalse(t.calls.get(0).contains("signIn"));
    }

    @Test
    public void theKeyTravelsInAHeaderNotInTheUrl() {
        Recorder t = new Recorder(error(400, "INVALID_ID_TOKEN", "INVALID_ARGUMENT", null));
        FirebaseChecks.authentication(CONFIG, t);
        assertEquals(GoogleServicesConfigTest.KEY, t.headers.get(0).get("X-Goog-Api-Key"));
        assertFalse(t.calls.get(0).contains(GoogleServicesConfigTest.KEY));
    }

    @Test
    public void anUnknownKeyFails() {
        Response r = error(400, "API key not valid. Please pass a valid API key.", "INVALID_ARGUMENT", "API_KEY_INVALID");
        assertEquals(Status.FAIL, FirebaseChecks.authentication(CONFIG, new Recorder(r)).status);
    }

    @Test
    public void aDisabledApiFails() {
        Response r = error(403, "Identity Toolkit API has not been used in project 123 before or it is disabled.", "PERMISSION_DENIED", "SERVICE_DISABLED");
        FirebaseChecks.Result result = FirebaseChecks.authentication(CONFIG, new Recorder(r));
        assertEquals(Status.FAIL, result.status);
        assertTrue(result.detail.contains("Authentication"));
    }

    @Test
    public void aRestrictedKeyIsOnlyAWarningBecauseItCannotBeCheckedHere() {
        Response r = error(403, "Requests from this Android client application <empty> are blocked.", "PERMISSION_DENIED", "API_KEY_ANDROID_APP_BLOCKED");
        assertEquals(Status.WARN, FirebaseChecks.authentication(CONFIG, new Recorder(r)).status);
    }

    @Test
    public void anAnswerThatIsNotAKnownSignalIsNeverASuccess() {
        assertEquals(Status.WARN, FirebaseChecks.authentication(CONFIG, new Recorder(new Response(200, "{}"))).status);
        assertEquals(Status.WARN, FirebaseChecks.authentication(CONFIG, new Recorder(new Response(418, "teapot"))).status);
        assertEquals(Status.WARN, FirebaseChecks.authentication(CONFIG, new Recorder(error(400, "SOMETHING_NEW", "INVALID_ARGUMENT", null))).status);
    }

    @Test
    public void serverErrorsAndNetworkFailuresAreUnreachableNotFailures() {
        assertEquals(Status.UNREACHABLE, FirebaseChecks.authentication(CONFIG, new Recorder(new Response(503, ""))).status);
        FirebaseChecks.Result offline = FirebaseChecks.authentication(CONFIG, new Recorder(new UnknownHostException("identitytoolkit.googleapis.com")));
        assertEquals(Status.UNREACHABLE, offline.status);
        // Only the exception type is reported: its message could hold an address.
        assertTrue(offline.detail.contains("UnknownHostException"));
        assertFalse(offline.detail.contains("googleapis"));
    }

    // ---- database

    @Test
    public void aDatabaseThatRefusesAnonymousReadsPasses() {
        Recorder t = new Recorder(new Response(401, "{\"error\":\"Permission denied\"}"));
        FirebaseChecks.Result r = FirebaseChecks.database(CONFIG, t);
        assertEquals(Status.PASS, r.status);
        assertTrue(r.detail.contains("refuse"));
        // A read of the root asking for keys only, anonymous, no key, no write.
        assertEquals("GET https://demo-app-12345-default-rtdb.firebaseio.com/.json?shallow=true", t.calls.get(0));
        assertTrue(t.headers.get(0).isEmpty());
    }

    @Test
    public void aDatabaseOpenToEveryoneIsAWarningAndItsContentIsNeverShown() {
        FirebaseChecks.Result r = FirebaseChecks.database(CONFIG, new Recorder(new Response(200, "{\"secret-node\":true}")));
        assertEquals(Status.WARN, r.status);
        assertFalse(r.detail.contains("secret-node"));
    }

    @Test
    public void aMissingOrMovedDatabaseFails() {
        assertEquals(Status.FAIL, FirebaseChecks.database(CONFIG, new Recorder(new Response(404, "{\"error\":\"no such database\"}"))).status);
        assertEquals(Status.FAIL, FirebaseChecks.database(CONFIG, new Recorder(new Response(307, ""))).status);
    }

    @Test
    public void noDatabaseInTheFileSkipsTheTest() {
        String json = GoogleServicesConfigTest.file("com.demo.app").replaceAll("\"firebase_url\": \"[^\"]*\",", "");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        Recorder t = new Recorder(new Response(200, ""));
        assertEquals(Status.SKIPPED, FirebaseChecks.database(c, t).status);
        assertTrue(t.calls.isEmpty());
    }

    // ---- storage

    @Test
    public void aBucketThatRefusesAnonymousListingPasses() {
        Recorder t = new Recorder(error(403, "Permission denied.", "PERMISSION_DENIED", null));
        assertEquals(Status.PASS, FirebaseChecks.storage(CONFIG, t).status);
        assertEquals("GET https://firebasestorage.googleapis.com/v0/b/demo-app-12345.appspot.com/o?maxResults=1", t.calls.get(0));
    }

    @Test
    public void aMissingBucketFailsAndAnOpenOneWarns() {
        assertEquals(Status.FAIL, FirebaseChecks.storage(CONFIG, new Recorder(new Response(404, "{}"))).status);
        assertEquals(Status.WARN, FirebaseChecks.storage(CONFIG, new Recorder(new Response(200, "{\"items\":[]}"))).status);
    }

    @Test
    public void noBucketSkipsTheTest() {
        String json = GoogleServicesConfigTest.file("com.demo.app").replaceAll(",\\s*\"storage_bucket\": \"[^\"]*\"", "");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        assertEquals("", c.storageBucket());
        assertEquals(Status.SKIPPED, FirebaseChecks.storage(c, new Recorder(new Response(200, ""))).status);
    }

    @Test
    public void errorSignalReadsOnlyTheDocumentedFields() {
        assertEquals("", FirebaseChecks.errorSignal("not json"));
        assertEquals("", FirebaseChecks.errorSignal("{}"));
        assertTrue(FirebaseChecks.errorSignal("{\"error\":{\"message\":\"Boom\",\"status\":\"X\"}}").contains("boom"));
    }
}
