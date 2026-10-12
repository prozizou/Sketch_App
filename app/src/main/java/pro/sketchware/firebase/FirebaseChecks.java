package pro.sketchware.firebase;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Connection checks for a Firebase configuration, one per service, kept apart so each can pass or fail alone.
 *
 * <p>They never try to get around Firebase's security rules: the requests are anonymous and read-only (a read of
 * the database root asking for keys only, a listing of one object), and a "permission denied" answer is the
 * <em>expected</em> good outcome. No response body is ever shown or kept, and neither the API key nor the URLs are logged.
 *
 * <p>The answers are interpreted from Google's documented error format ({@code error.status}, {@code error.message},
 * {@code error.details[].reason}); an answer that is not one of the known signals is reported as unexpected and never as
 * a success.
 */
public final class FirebaseChecks {
    public enum Status {
        PASS, WARN, FAIL,
        /** The service could not be reached (offline, DNS, timeout, server error): the configuration is not judged. */
        UNREACHABLE,
        SKIPPED
    }

    public static final class Result {
        public final Status status;
        public final String detail;

        public Result(Status status, String detail) {
            this.status = status;
            this.detail = detail;
        }
    }

    public static final class Response {
        public final int code;
        public final String body;

        public Response(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }

    /** The network, injected so the interpretation can be tested with canned answers. */
    public interface Transport {
        /** Sends one HTTPS request and does not follow redirects; throws {@link IOException} when the network fails. */
        Response send(String method, String url, Map<String, String> headers, String body) throws IOException;
    }

    private FirebaseChecks() {
    }

    // ---- authentication

    /**
     * Asks Firebase Authentication to look up a deliberately invalid token with the app's API key. That creates nothing
     * and signs nobody in; the answer tells whether the key is known, and whether the Authentication API is reachable
     * with it.
     */
    public static Result authentication(GoogleServicesConfig config, Transport transport) {
        if (config.apiKey().isEmpty()) return new Result(Status.SKIPPED, "No API key in the configuration.");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Goog-Api-Key", config.apiKey());
        Response r;
        try {
            r = transport.send("POST", "https://identitytoolkit.googleapis.com/v1/accounts:lookup", headers, "{\"idToken\":\"nws-configuration-check\"}");
        } catch (IOException e) {
            return unreachable(e);
        }
        return interpretAuthentication(r);
    }

    static Result interpretAuthentication(Response r) {
        if (r.code >= 500) return new Result(Status.UNREACHABLE, "The service answered with an error (HTTP " + r.code + "). Try again later.");
        String signal = errorSignal(r.body);
        if (signal.contains("api_key_invalid") || signal.contains("api key not valid") || signal.contains("api key expired")) {
            return new Result(Status.FAIL, "Firebase does not recognise this API key.");
        }
        if (r.code == 400 && (signal.contains("invalid_id_token") || signal.contains("missing_id_token") || signal.contains("credential_too_old"))) {
            return new Result(Status.PASS, "The API key is accepted by Firebase Authentication.");
        }
        if (r.code == 403) {
            if (signal.contains("service_disabled") || signal.contains("has not been used") || signal.contains("is disabled")) {
                return new Result(Status.FAIL, "The Identity Toolkit API is disabled for this project. Enable Authentication in the Firebase console.");
            }
            if (signal.contains("blocked") || signal.contains("restrict")) {
                return new Result(Status.WARN, "The key is restricted (to Android apps, for example), so it cannot be checked from this device. This is normal for a restricted key.");
            }
        }
        return new Result(Status.WARN, "Unexpected answer from Firebase Authentication (HTTP " + r.code + "); the key could not be checked.");
    }

    // ---- authorised access to the services

    /** An anonymous, keys-only read of the database root: "permission denied" means the rules are doing their job. */
    public static Result database(GoogleServicesConfig config, Transport transport) {
        if (config.databaseHost().isEmpty()) return new Result(Status.SKIPPED, "No Realtime Database URL in the configuration.");
        Response r;
        try {
            r = transport.send("GET", "https://" + config.databaseHost() + "/.json?shallow=true", new LinkedHashMap<>(), null);
        } catch (IOException e) {
            return unreachable(e);
        }
        return interpretAccess(r, "database");
    }

    /** An anonymous listing of at most one object of the bucket. */
    public static Result storage(GoogleServicesConfig config, Transport transport) {
        if (config.storageBucket().isEmpty()) return new Result(Status.SKIPPED, "No storage bucket in the configuration.");
        Response r;
        try {
            String bucket = URLEncoder.encode(config.storageBucket(), StandardCharsets.UTF_8);
            r = transport.send("GET", "https://firebasestorage.googleapis.com/v0/b/" + bucket + "/o?maxResults=1", new LinkedHashMap<>(), null);
        } catch (IOException e) {
            return unreachable(e);
        }
        return interpretAccess(r, "bucket");
    }

    static Result interpretAccess(Response r, String what) {
        int code = r.code;
        if (code >= 500) return new Result(Status.UNREACHABLE, "The service answered with an error (HTTP " + code + "). Try again later.");
        if (code == 200) {
            return new Result(Status.WARN, "Reachable, but the security rules let anyone read the " + what + " without signing in. Check that this is intended.");
        }
        if (code == 401 || code == 403) {
            return new Result(Status.PASS, "Reachable. The security rules refuse anonymous access, as expected for an app that signs users in.");
        }
        if (code == 404) {
            return new Result(Status.FAIL, "No " + what + " was found at this address. Check the URL or name in the file, and that the service is set up in the Firebase console.");
        }
        if (code >= 300 && code < 400) {
            return new Result(Status.FAIL, "The address redirects elsewhere (HTTP " + code + "): the " + what + " is probably in another region than the one in the file.");
        }
        if (code == 423) {
            return new Result(Status.WARN, "The " + what + " is locked or disabled.");
        }
        return new Result(Status.WARN, "Unexpected answer (HTTP " + code + "); access could not be judged.");
    }

    // ---- helpers

    private static Result unreachable(IOException e) {
        // Only the exception type: its message may contain the address.
        String kind = e.getClass().getSimpleName();
        return new Result(Status.UNREACHABLE, "Could not reach the service (" + (kind.isEmpty() ? "network error" : kind)
                + "). Check the connection and try again.");
    }

    /** Lower-cased text of the documented error fields, or the raw start of the body when it is not JSON. */
    static String errorSignal(String body) {
        StringBuilder sb = new StringBuilder();
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonObject() && root.getAsJsonObject().has("error")) {
                JsonElement error = root.getAsJsonObject().get("error");
                if (error.isJsonObject()) {
                    JsonObject o = error.getAsJsonObject();
                    append(sb, o, "message");
                    append(sb, o, "status");
                    if (o.has("details") && o.get("details").isJsonArray()) {
                        JsonArray details = o.getAsJsonArray("details");
                        for (JsonElement d : details) {
                            if (d.isJsonObject()) {
                                append(sb, d.getAsJsonObject(), "reason");
                            }
                        }
                    }
                    if (o.has("errors") && o.get("errors").isJsonArray()) {
                        for (JsonElement d : o.getAsJsonArray("errors")) {
                            if (d.isJsonObject()) append(sb, d.getAsJsonObject(), "reason");
                        }
                    }
                } else if (error.isJsonPrimitive()) {
                    sb.append(error.getAsString());
                }
            }
        } catch (RuntimeException ignored) {
            // not JSON: no signal
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder sb, JsonObject o, String key) {
        if (o.has(key) && o.get(key).isJsonPrimitive()) sb.append(' ').append(o.get(key).getAsString());
    }
}
