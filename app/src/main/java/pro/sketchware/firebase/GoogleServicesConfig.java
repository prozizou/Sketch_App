package pro.sketchware.firebase;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed and validated {@code google-services.json}. Pure Java: no Android types, so it is unit-tested on a plain JVM.
 *
 * <p>The build reads four strings from the project's Firebase library entry (database URL or project id, app id, API
 * key, storage bucket); {@link #libraryData()} and friends give exactly those, so a configuration made here builds the
 * same way as one typed in the old four-field screen.
 *
 * <p>Nothing in this class ever puts an app id or an API key into an {@link Issue} message.
 */
public final class GoogleServicesConfig {
    public enum Severity {ERROR, WARNING, INFO}

    /** A problem found in the file; {@code line} is 1-based, or 0 when it is not tied to a line. */
    public static final class Issue {
        public final Severity severity;
        public final String code;
        public final String message;
        public final int line;

        Issue(Severity severity, String code, String message, int line) {
            this.severity = severity;
            this.code = code;
            this.message = message;
            this.line = line;
        }
    }

    public static final class OAuthClient {
        public final String clientId;
        /** 1 = Android, 3 = Web, as in the file; other values are kept as is. */
        public final int type;

        OAuthClient(String clientId, int type) {
            this.clientId = clientId;
            this.type = type;
        }
    }

    /** One entry of the file's {@code client} array. */
    public static final class Client {
        public final String packageName;
        public final String appId;
        public final String apiKey;
        public final List<OAuthClient> oauthClients;

        Client(String packageName, String appId, String apiKey, List<OAuthClient> oauthClients) {
            this.packageName = packageName;
            this.appId = appId;
            this.apiKey = apiKey;
            this.oauthClients = Collections.unmodifiableList(oauthClients);
        }
    }

    /** The build derives the project id from the database host with this expression (see {@code yq}). */
    private static final Pattern DATABASE_HOST_SUFFIX = Pattern.compile("(-default-rtdb)?\\.[a-z](.?)+");
    private static final Pattern PROJECT_ID = Pattern.compile("[a-z][a-z0-9-]{4,28}[a-z0-9]");
    private static final Pattern APP_ID = Pattern.compile("(\\d+):(\\d+):([a-z]+):([0-9a-fA-F]+)");
    private static final Pattern API_KEY = Pattern.compile("AIza[0-9A-Za-z_-]{35}");
    private static final Pattern HOST = Pattern.compile("[a-z0-9][a-z0-9-]*(\\.[a-z0-9][a-z0-9-]*)+");
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9._-]{2,221}");
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final Pattern SYNTAX_POSITION = Pattern.compile("line (\\d+) column (\\d+)");
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(\"(?:current_key|mobilesdk_app_id)\"\\s*:\\s*\")((?:[^\"\\\\]|\\\\.)*)(\")");

    private final String text;
    private final List<Issue> issues;
    private final List<Client> clients;
    private final int selected;
    private final String projectId;
    private final String projectNumber;
    private final String databaseHost;
    private final String storageBucket;
    private final boolean empty;

    private GoogleServicesConfig(String text, List<Issue> issues, List<Client> clients, int selected, String projectId,
                                 String projectNumber, String databaseHost, String storageBucket, boolean empty) {
        this.text = text;
        this.issues = Collections.unmodifiableList(issues);
        this.clients = Collections.unmodifiableList(clients);
        this.selected = selected;
        this.projectId = projectId;
        this.projectNumber = projectNumber;
        this.databaseHost = databaseHost;
        this.storageBucket = storageBucket;
        this.empty = empty;
    }

    // ---- reading

    /** Same as {@link #parse(String, String, String)} without a client the user picked by hand. */
    public static GoogleServicesConfig parse(String json, String projectPackage) {
        return parse(json, projectPackage, null);
    }

    /**
     * @param projectPackage   the package name of the NWS project (may be empty when unknown)
     * @param preferredPackage a client the user chose by hand; wins over {@code projectPackage} when present in the file
     */
    public static GoogleServicesConfig parse(String json, String projectPackage, String preferredPackage) {
        String text = json == null ? "" : json;
        List<Issue> issues = new ArrayList<>();
        if (text.trim().isEmpty()) {
            return new GoogleServicesConfig(text, issues, new ArrayList<>(), -1, "", "", "", "", true);
        }

        JsonElement root;
        try {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setStrictness(Strictness.STRICT);
            root = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new com.google.gson.JsonSyntaxException("Unexpected text after the end of the JSON value");
            }
        } catch (Exception e) {
            issues.add(syntaxIssue(text, e));
            return new GoogleServicesConfig(text, issues, new ArrayList<>(), -1, "", "", "", "", false);
        }
        if (!root.isJsonObject()) {
            issues.add(new Issue(Severity.ERROR, "NOT_OBJECT", "The file must contain a JSON object ({ ... }).", 1));
            return new GoogleServicesConfig(text, issues, new ArrayList<>(), -1, "", "", "", "", false);
        }
        JsonObject obj = root.getAsJsonObject();

        // project_info
        JsonObject info = object(obj, "project_info");
        String projectId = string(info, "project_id");
        String projectNumber = string(info, "project_number");
        if (info == null) {
            issues.add(new Issue(Severity.ERROR, "MISSING_PROJECT_INFO", "\"project_info\" is missing.", 1));
        }
        if (projectId.isEmpty()) {
            if (info != null) {
                issues.add(new Issue(Severity.ERROR, "MISSING_PROJECT_ID", "\"project_id\" is missing or empty.", lineOf(text, "project_info", 0)));
            }
        } else if (!PROJECT_ID.matcher(projectId).matches()) {
            issues.add(new Issue(Severity.WARNING, "PROJECT_ID_FORMAT",
                    "Project ID \"" + projectId + "\" does not look like a Firebase project id (6-30 lowercase letters, digits or hyphens).",
                    lineOf(text, "project_id", 0)));
        }
        if (!projectNumber.isEmpty() && !projectNumber.matches("\\d+")) {
            issues.add(new Issue(Severity.WARNING, "PROJECT_NUMBER_FORMAT", "\"project_number\" should only contain digits.",
                    lineOf(text, "project_number", 0)));
        }

        String databaseHost = "";
        String rawUrl = string(info, "firebase_url");
        if (rawUrl.isEmpty()) {
            if (info != null) {
                issues.add(new Issue(Severity.WARNING, "MISSING_DATABASE_URL",
                        "No Realtime Database URL (\"firebase_url\"). Database blocks will not work; the project id is used instead.",
                        lineOf(text, "project_info", 0)));
            }
        } else {
            databaseHost = normalizeDatabaseUrl(rawUrl, issues, lineOf(text, "firebase_url", 0));
        }

        String storageBucket = "";
        String rawBucket = string(info, "storage_bucket");
        if (rawBucket.isEmpty()) {
            if (info != null) {
                issues.add(new Issue(Severity.WARNING, "MISSING_STORAGE_BUCKET",
                        "No storage bucket (\"storage_bucket\"). Cloud Storage blocks will not work.", lineOf(text, "project_info", 0)));
            }
        } else {
            storageBucket = normalizeBucket(rawBucket, issues, lineOf(text, "storage_bucket", 0));
        }

        // clients
        List<Client> clients = new ArrayList<>();
        JsonArray clientArray = obj.has("client") && obj.get("client").isJsonArray() ? obj.getAsJsonArray("client") : null;
        if (clientArray == null || clientArray.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "MISSING_CLIENT", "\"client\" must list at least one Android app.", lineOf(text, "client", 0)));
        } else {
            for (int i = 0; i < clientArray.size(); i++) {
                clients.add(readClient(text, clientArray.get(i), i, issues));
            }
        }

        int selected = selectClient(clients, projectPackage, preferredPackage);
        checkPackages(text, clients, selected, projectPackage, issues);

        Client chosen = selected >= 0 ? clients.get(selected) : null;
        if (chosen != null) {
            Matcher m = APP_ID.matcher(chosen.appId);
            if (m.matches() && !projectNumber.isEmpty() && !m.group(2).equals(projectNumber)) {
                issues.add(new Issue(Severity.WARNING, "PROJECT_NUMBER_MISMATCH",
                        "The app id belongs to another project number than \"project_number\".", lineOf(text, "mobilesdk_app_id", selected)));
            }
        }

        // consistency with the project id, and with what the build derives from the database host
        if (!databaseHost.isEmpty() && !projectId.isEmpty()) {
            String derived = DATABASE_HOST_SUFFIX.matcher(databaseHost).replaceAll("");
            if (!derived.equals(projectId)) {
                issues.add(new Issue(Severity.WARNING, "DATABASE_PROJECT_MISMATCH",
                        "The build takes the project id from the database host (\"" + derived + "\"), which differs from \"project_id\" (\""
                                + projectId + "\"). Firebase services may reject the app.", lineOf(text, "firebase_url", 0)));
            }
        }
        if (!storageBucket.isEmpty() && !projectId.isEmpty() && !storageBucket.startsWith(projectId)) {
            issues.add(new Issue(Severity.INFO, "BUCKET_PROJECT_MISMATCH",
                    "The storage bucket does not start with the project id; this is normal only for a custom bucket.",
                    lineOf(text, "storage_bucket", 0)));
        }

        return new GoogleServicesConfig(text, issues, clients, selected, projectId, projectNumber, databaseHost, storageBucket, false);
    }

    private static Client readClient(String text, JsonElement element, int index, List<Issue> issues) {
        if (!element.isJsonObject()) {
            issues.add(new Issue(Severity.ERROR, "BAD_CLIENT", "client[" + index + "] must be an object.", lineOf(text, "client", 0)));
            return new Client("", "", "", new ArrayList<>());
        }
        JsonObject client = element.getAsJsonObject();
        JsonObject clientInfo = object(client, "client_info");
        String appId = string(clientInfo, "mobilesdk_app_id");
        JsonObject android = object(clientInfo, "android_client_info");
        String packageName = string(android, "package_name");

        String apiKey = "";
        if (client.has("api_key") && client.get("api_key").isJsonArray()) {
            for (JsonElement key : client.getAsJsonArray("api_key")) {
                if (key.isJsonObject()) {
                    apiKey = string(key.getAsJsonObject(), "current_key");
                    if (!apiKey.isEmpty()) break;
                }
            }
        }

        List<OAuthClient> oauth = new ArrayList<>();
        if (client.has("oauth_client") && client.get("oauth_client").isJsonArray()) {
            for (JsonElement entry : client.getAsJsonArray("oauth_client")) {
                if (!entry.isJsonObject()) continue;
                JsonObject o = entry.getAsJsonObject();
                int type = 0;
                if (o.has("client_type") && o.get("client_type").isJsonPrimitive()) {
                    try {
                        type = o.get("client_type").getAsInt();
                    } catch (NumberFormatException ignored) {
                        // keep 0: an unknown type is shown as such
                    }
                }
                String id = string(o, "client_id");
                if (!id.isEmpty()) oauth.add(new OAuthClient(id, type));
            }
        }

        String where = "client[" + index + "]";
        if (appId.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "MISSING_APP_ID", where + ": \"mobilesdk_app_id\" is missing or empty.", lineOf(text, "client_info", index)));
        } else {
            Matcher m = APP_ID.matcher(appId);
            if (!m.matches()) {
                issues.add(new Issue(Severity.ERROR, "BAD_APP_ID",
                        where + ": the app id is not in the form 1:<project number>:android:<hex>.", lineOf(text, "mobilesdk_app_id", index)));
            } else if (!m.group(3).equals("android")) {
                issues.add(new Issue(Severity.ERROR, "NOT_ANDROID_APP_ID",
                        where + ": the app id is for \"" + m.group(3) + "\", not for an Android app.", lineOf(text, "mobilesdk_app_id", index)));
            }
        }
        if (packageName.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "MISSING_PACKAGE", where + ": \"package_name\" is missing or empty.", lineOf(text, "client_info", index)));
        } else if (!PACKAGE.matcher(packageName).matches()) {
            issues.add(new Issue(Severity.ERROR, "BAD_PACKAGE", where + ": \"" + packageName + "\" is not a valid Android package name.",
                    lineOf(text, "package_name", index)));
        }
        if (apiKey.isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "MISSING_API_KEY", where + ": no \"current_key\" in \"api_key\".", lineOf(text, "client_info", index)));
        } else if (!API_KEY.matcher(apiKey).matches()) {
            issues.add(new Issue(Severity.WARNING, "API_KEY_FORMAT",
                    where + ": the API key does not have the usual form (AIza followed by 35 characters).", lineOf(text, "current_key", index)));
        }
        return new Client(packageName, appId, apiKey, oauth);
    }

    private static int selectClient(List<Client> clients, String projectPackage, String preferredPackage) {
        if (clients.isEmpty()) return -1;
        for (String wanted : new String[]{preferredPackage, projectPackage}) {
            if (wanted == null || wanted.isEmpty()) continue;
            for (int i = 0; i < clients.size(); i++) {
                if (clients.get(i).packageName.equals(wanted)) return i;
            }
        }
        return 0;
    }

    private static void checkPackages(String text, List<Client> clients, int selected, String projectPackage, List<Issue> issues) {
        for (int i = 0; i < clients.size(); i++) {
            for (int j = i + 1; j < clients.size(); j++) {
                String a = clients.get(i).packageName;
                if (!a.isEmpty() && a.equals(clients.get(j).packageName)) {
                    issues.add(new Issue(Severity.WARNING, "DUPLICATE_PACKAGE",
                            "Two clients use the package \"" + a + "\"; the first one is used.", lineOf(text, "package_name", j)));
                }
            }
        }
        if (selected < 0 || projectPackage == null || projectPackage.isEmpty()) return;
        String chosen = clients.get(selected).packageName;
        if (!chosen.equals(projectPackage)) {
            StringBuilder available = new StringBuilder();
            for (Client c : clients) {
                if (c.packageName.isEmpty()) continue;
                if (available.length() > 0) available.append(", ");
                available.append(c.packageName);
            }
            issues.add(new Issue(Severity.WARNING, "PACKAGE_MISMATCH",
                    "No client matches this project's package \"" + projectPackage + "\". The file has: " + available
                            + ". Firebase features tied to the package (Google sign-in, App Check) will fail.",
                    lineOf(text, "package_name", selected)));
        }
    }

    private static String normalizeDatabaseUrl(String raw, List<Issue> issues, int line) {
        String value = raw.trim();
        if (value.regionMatches(true, 0, "http://", 0, 7)) {
            issues.add(new Issue(Severity.ERROR, "DATABASE_URL_NOT_HTTPS", "The database URL must use https, not http.", line));
            return "";
        }
        if (value.regionMatches(true, 0, "https://", 0, 8)) value = value.substring(8);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        String lower = value.toLowerCase(Locale.ROOT);
        if (!HOST.matcher(lower).matches()) {
            issues.add(new Issue(Severity.ERROR, "BAD_DATABASE_URL",
                    "The database URL must be a host name such as <project>-default-rtdb.firebaseio.com, without a path.", line));
            return "";
        }
        if (!lower.endsWith(".firebaseio.com") && !lower.endsWith(".firebasedatabase.app")) {
            issues.add(new Issue(Severity.WARNING, "UNUSUAL_DATABASE_HOST",
                    "The database host does not end with firebaseio.com or firebasedatabase.app.", line));
        }
        return lower;
    }

    private static String normalizeBucket(String raw, List<Issue> issues, int line) {
        String value = raw.trim();
        if (value.regionMatches(true, 0, "gs://", 0, 5)) value = value.substring(5);
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!BUCKET.matcher(value).matches()) {
            issues.add(new Issue(Severity.ERROR, "BAD_STORAGE_BUCKET",
                    "The storage bucket must be a bucket name such as <project>.appspot.com, without a path.", line));
            return "";
        }
        return value;
    }

    private static Issue syntaxIssue(String text, Exception e) {
        int line = 1;
        String message = e.getMessage() == null ? "" : e.getMessage();
        Matcher m = SYNTAX_POSITION.matcher(message);
        String where = "";
        if (m.find()) {
            line = Integer.parseInt(m.group(1));
            where = " (line " + line + ", column " + m.group(2) + ")";
        }
        String reason = message;
        int cut = reason.indexOf(" at line ");
        if (cut > 0) reason = reason.substring(0, cut);
        cut = reason.indexOf(" Use ");
        if (cut > 0) reason = reason.substring(0, cut);
        reason = reason.replaceAll("\\s+", " ").trim();
        if (reason.isEmpty()) reason = "Malformed JSON";
        return new Issue(Severity.ERROR, "SYNTAX", "Invalid JSON: " + reason + where, line);
    }

    // ---- accessors

    public boolean isEmpty() {
        return empty;
    }

    /** True when the text is not empty and has no error; warnings do not count. */
    public boolean isValid() {
        return !empty && !hasErrors();
    }

    public boolean hasErrors() {
        for (Issue issue : issues) if (issue.severity == Severity.ERROR) return true;
        return false;
    }

    public int count(Severity severity) {
        int n = 0;
        for (Issue issue : issues) if (issue.severity == severity) n++;
        return n;
    }

    public List<Issue> issues() {
        return issues;
    }

    public List<Client> clients() {
        return clients;
    }

    public int selectedIndex() {
        return selected;
    }

    public Client selectedClient() {
        return selected >= 0 ? clients.get(selected) : null;
    }

    public String projectId() {
        return projectId;
    }

    public String projectNumber() {
        return projectNumber;
    }

    /** The database host without scheme or trailing slash, or empty when the file has none. */
    public String databaseHost() {
        return databaseHost;
    }

    public String databaseUrl() {
        return databaseHost.isEmpty() ? "" : "https://" + databaseHost;
    }

    /** The bucket name without {@code gs://}, or empty. */
    public String storageBucket() {
        return storageBucket;
    }

    public String packageName() {
        Client c = selectedClient();
        return c == null ? "" : c.packageName;
    }

    public String appId() {
        Client c = selectedClient();
        return c == null ? "" : c.appId;
    }

    public String apiKey() {
        Client c = selectedClient();
        return c == null ? "" : c.apiKey;
    }

    /** The OAuth client of type 3 (Web) of the selected client, used as the default web client id; empty when none. */
    public String webClientId() {
        Client c = selectedClient();
        if (c == null) return "";
        for (OAuthClient o : c.oauthClients) if (o.type == 3) return o.clientId;
        return "";
    }

    // ---- what the build reads

    /** Value for the library entry's {@code data}: the database host, or the project id when the file has no database. */
    public String libraryData() {
        return databaseHost.isEmpty() ? projectId : databaseHost;
    }

    // ---- helpers around the text

    /** The text with the values of {@code current_key} and {@code mobilesdk_app_id} hidden; line structure is unchanged. */
    public static String maskSecrets(String json) {
        if (json == null) return "";
        Matcher m = SECRET_VALUE.matcher(json);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + mask(m.group(2)) + m.group(3)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** Keeps the last four characters of a credential. */
    public static String mask(String value) {
        if (value == null || value.isEmpty()) return "";
        int keep = Math.min(4, value.length());
        int dots = Math.min(12, Math.max(0, value.length() - keep));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dots; i++) sb.append('•');
        sb.append(value.substring(value.length() - keep));
        return sb.toString();
    }

    /** Pretty-prints valid JSON keeping key order and without escaping characters; returns {@code null} when it is not valid. */
    public static String format(String json) {
        if (json == null) return null;
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) return null;
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(element) + "\n";
        } catch (Exception e) {
            return null;
        }
    }

    // ---- migration

    /**
     * Rebuilds a {@code google-services.json} from the four values an existing project already stores, so a project made
     * with the old screen can be opened in the editor. Nothing is invented: a field that is not stored is left out, and
     * the project number is read from the app id.
     */
    public static String fromStoredValues(String data, String appId, String apiKey, String storageBucket, String packageName) {
        String d = data == null ? "" : data.trim();
        String app = appId == null ? "" : appId.trim();
        String key = apiKey == null ? "" : apiKey.trim();
        String bucket = storageBucket == null ? "" : storageBucket.trim();
        String pkg = packageName == null ? "" : packageName.trim();
        if (d.isEmpty() && app.isEmpty() && key.isEmpty() && bucket.isEmpty()) return "";

        String projectId;
        String url;
        if (d.contains(".")) {
            url = "https://" + d;
            projectId = DATABASE_HOST_SUFFIX.matcher(d).replaceAll("");
        } else {
            projectId = d;
            url = d.isEmpty() ? "" : "https://" + d + ".firebaseio.com";
        }

        JsonObject root = new JsonObject();
        JsonObject info = new JsonObject();
        Matcher m = APP_ID.matcher(app);
        if (m.matches()) info.addProperty("project_number", m.group(2));
        if (!url.isEmpty()) info.addProperty("firebase_url", url);
        if (!projectId.isEmpty()) info.addProperty("project_id", projectId);
        if (!bucket.isEmpty()) info.addProperty("storage_bucket", bucket);
        root.add("project_info", info);

        JsonObject client = new JsonObject();
        JsonObject clientInfo = new JsonObject();
        if (!app.isEmpty()) clientInfo.addProperty("mobilesdk_app_id", app);
        JsonObject android = new JsonObject();
        if (!pkg.isEmpty()) android.addProperty("package_name", pkg);
        clientInfo.add("android_client_info", android);
        client.add("client_info", clientInfo);
        if (!key.isEmpty()) {
            JsonArray keys = new JsonArray();
            JsonObject k = new JsonObject();
            k.addProperty("current_key", key);
            keys.add(k);
            client.add("api_key", keys);
        }
        JsonArray clients = new JsonArray();
        clients.add(client);
        root.add("client", clients);
        root.addProperty("configuration_version", "1");
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    // ---- small JSON helpers

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static String string(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return "";
        JsonElement e = parent.get(key);
        if (!e.isJsonPrimitive()) return "";
        return e.getAsString().trim();
    }

    /** Line (1-based) of the {@code occurrence}-th {@code "key"} in the text; falls back to the first one, then to 1. */
    static int lineOf(String text, String key, int occurrence) {
        String needle = "\"" + key + "\"";
        int from = 0;
        int found = -1;
        int seen = 0;
        int first = -1;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) break;
            if (first < 0) first = at;
            if (seen == occurrence) {
                found = at;
                break;
            }
            seen++;
            from = at + needle.length();
        }
        if (found < 0) found = first;
        if (found < 0) return 1;
        int line = 1;
        for (int i = 0; i < found; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }
}
