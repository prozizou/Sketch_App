package pro.sketchware.firebase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The fixtures are synthetic: they have the shape of a real google-services.json and obviously made-up identifiers.
 */
public class GoogleServicesConfigTest {
    static final String KEY = GoogleServicesConfigTestFixtures.KEY;
    static final String KEY_2 = GoogleServicesConfigTestFixtures.KEY_2;

    static String file(String pkg) {
        return GoogleServicesConfigTestFixtures.file(pkg);
    }

    static String twoClients() {
        return GoogleServicesConfigTestFixtures.twoClients();
    }

    private static boolean has(GoogleServicesConfig c, String code) {
        for (GoogleServicesConfig.Issue i : c.issues()) if (i.code.equals(code)) return true;
        return false;
    }

    @Test
    public void aCompleteFileIsReadAndValid() {
        GoogleServicesConfig c = GoogleServicesConfig.parse(file("com.demo.app"), "com.demo.app");
        assertTrue(c.isValid());
        assertEquals(0, c.issues().size());
        assertEquals("demo-app-12345", c.projectId());
        assertEquals("123456789012", c.projectNumber());
        assertEquals("demo-app-12345-default-rtdb.firebaseio.com", c.databaseHost());
        assertEquals("https://demo-app-12345-default-rtdb.firebaseio.com", c.databaseUrl());
        assertEquals("demo-app-12345.appspot.com", c.storageBucket());
        assertEquals("com.demo.app", c.packageName());
        assertEquals("1:123456789012:android:0123456789abcdef", c.appId());
        assertEquals(KEY, c.apiKey());
        assertEquals("123456789012-web.apps.googleusercontent.com", c.webClientId());
        assertEquals(2, c.selectedClient().oauthClients.size());
    }

    @Test
    public void whatTheBuildReadsIsTheSameFourValuesAsBefore() {
        GoogleServicesConfig c = GoogleServicesConfig.parse(file("com.demo.app"), "com.demo.app");
        // data = database host (the old import also dropped "https://"), then app id, API key and bucket.
        assertEquals("demo-app-12345-default-rtdb.firebaseio.com", c.libraryData());
        assertEquals("1:123456789012:android:0123456789abcdef", c.appId());
        assertEquals(KEY, c.apiKey());
        assertEquals("demo-app-12345.appspot.com", c.storageBucket());
    }

    @Test
    public void withoutADatabaseTheProjectIdIsStoredInstead() {
        String json = file("com.demo.app").replaceAll("\"firebase_url\": \"[^\"]*\",", "");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        assertTrue(c.isValid());
        assertTrue(has(c, "MISSING_DATABASE_URL"));
        assertEquals("demo-app-12345", c.libraryData());
    }

    @Test
    public void syntaxErrorsNameTheLine() {
        String json = "{\n  \"project_info\": {\n    \"project_id\": \"demo-app-12345\",\n  }\n}";
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        assertFalse(c.isValid());
        assertEquals(1, c.issues().size());
        GoogleServicesConfig.Issue issue = c.issues().get(0);
        assertEquals("SYNTAX", issue.code);
        assertEquals(GoogleServicesConfig.Severity.ERROR, issue.severity);
        assertEquals(4, issue.line);
        assertTrue(issue.message.startsWith("Invalid JSON"));
    }

    @Test
    public void trailingTextAndCommentsAreRejected() {
        assertTrue(has(GoogleServicesConfig.parse("{} extra", ""), "SYNTAX"));
        assertTrue(has(GoogleServicesConfig.parse("{ // note\n}", ""), "SYNTAX"));
        assertTrue(has(GoogleServicesConfig.parse("[1, 2]", ""), "NOT_OBJECT"));
    }

    @Test
    public void emptyTextIsEmptyNotInvalid() {
        GoogleServicesConfig c = GoogleServicesConfig.parse("  \n", "com.demo.app");
        assertTrue(c.isEmpty());
        assertFalse(c.isValid());
        assertEquals(0, c.issues().size());
    }

    @Test
    public void requiredFieldsAreReportedOneByOne() {
        GoogleServicesConfig c = GoogleServicesConfig.parse("{\"project_info\":{},\"client\":[{\"client_info\":{}}]}", "x.y");
        assertFalse(c.isValid());
        assertTrue(has(c, "MISSING_PROJECT_ID"));
        assertTrue(has(c, "MISSING_APP_ID"));
        assertTrue(has(c, "MISSING_PACKAGE"));
        assertTrue(has(c, "MISSING_API_KEY"));
        assertTrue(has(GoogleServicesConfig.parse("{\"project_info\":{\"project_id\":\"demo-app-12345\"}}", ""), "MISSING_CLIENT"));
    }

    @Test
    public void malformedValuesAreErrorsOrWarnings() {
        String json = file("com.demo.app")
                .replace("1:123456789012:android:0123456789abcdef", "not-an-app-id")
                .replace("https://demo-app-12345-default-rtdb.firebaseio.com", "http://demo-app-12345.firebaseio.com")
                .replace("demo-app-12345.appspot.com", "bad bucket/with path");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        assertFalse(c.isValid());
        assertTrue(has(c, "BAD_APP_ID"));
        assertTrue(has(c, "DATABASE_URL_NOT_HTTPS"));
        assertTrue(has(c, "BAD_STORAGE_BUCKET"));
    }

    @Test
    public void anIosAppIdIsNotAnAndroidOne() {
        GoogleServicesConfig c = GoogleServicesConfig.parse(file("com.demo.app").replace(":android:", ":ios:"), "com.demo.app");
        assertTrue(has(c, "NOT_ANDROID_APP_ID"));
        assertFalse(c.isValid());
    }

    @Test
    public void unusualButWorkableValuesOnlyWarn() {
        String json = file("com.demo.app").replace(KEY, "AIzaShort").replace("demo-app-12345\"", "Demo_App\"");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        assertTrue(c.isValid());
        assertTrue(has(c, "API_KEY_FORMAT"));
        assertTrue(has(c, "PROJECT_ID_FORMAT"));
    }

    @Test
    public void databaseUrlsAreNormalised() {
        String json = file("com.demo.app").replace("https://demo-app-12345-default-rtdb.firebaseio.com",
                "https://Demo-App-12345-default-rtdb.firebaseio.com/").replace("demo-app-12345.appspot.com", "gs://demo-app-12345.appspot.com/");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        assertEquals("demo-app-12345-default-rtdb.firebaseio.com", c.databaseHost());
        assertEquals("demo-app-12345.appspot.com", c.storageBucket());
        assertTrue(c.isValid());
    }

    @Test
    public void aDatabaseInAnotherRegionKeepsItsHostAndStillMapsToTheProjectId() {
        String host = "demo-app-12345-default-rtdb.europe-west1.firebasedatabase.app";
        GoogleServicesConfig c = GoogleServicesConfig.parse(file("com.demo.app").replace("demo-app-12345-default-rtdb.firebaseio.com", host), "com.demo.app");
        assertEquals(host, c.libraryData());
        assertFalse(has(c, "DATABASE_PROJECT_MISMATCH"));
        assertFalse(has(c, "UNUSUAL_DATABASE_HOST"));
    }

    @Test
    public void aDatabaseNamedAfterAnotherProjectWarnsBecauseTheBuildDerivesTheProjectIdFromIt() {
        GoogleServicesConfig c = GoogleServicesConfig.parse(
                file("com.demo.app").replace("demo-app-12345-default-rtdb.firebaseio.com", "other-db.firebaseio.com"), "com.demo.app");
        assertTrue(c.isValid());
        assertTrue(has(c, "DATABASE_PROJECT_MISMATCH"));
    }

    @Test
    public void theClientMatchingTheProjectPackageIsSelected() {
        GoogleServicesConfig c = GoogleServicesConfig.parse(twoClients(), "com.demo.second");
        assertEquals(2, c.clients().size());
        assertEquals(1, c.selectedIndex());
        assertEquals("com.demo.second", c.packageName());
        assertEquals("1:123456789012:android:bbbbbbbbbbbbbbbb", c.appId());
        assertEquals(KEY_2, c.apiKey());
        assertFalse(has(c, "PACKAGE_MISMATCH"));
    }

    @Test
    public void aClientChosenByHandWinsAndNoMatchFallsBackToTheFirstWithAWarning() {
        assertEquals("com.demo.first", GoogleServicesConfig.parse(twoClients(), "com.demo.second", "com.demo.first").packageName());
        GoogleServicesConfig none = GoogleServicesConfig.parse(twoClients(), "com.other.app");
        assertEquals(0, none.selectedIndex());
        assertTrue(has(none, "PACKAGE_MISMATCH"));
        assertTrue(none.isValid());
        for (GoogleServicesConfig.Issue i : none.issues()) {
            if (i.code.equals("PACKAGE_MISMATCH")) {
                assertTrue(i.message.contains("com.demo.first"));
                assertTrue(i.message.contains("com.demo.second"));
            }
        }
        // An unknown project package (not opened from a project) is not a mismatch.
        assertFalse(has(GoogleServicesConfig.parse(twoClients(), ""), "PACKAGE_MISMATCH"));
    }

    @Test
    public void twoClientsWithTheSamePackageWarn() {
        String json = twoClients().replace("com.demo.second", "com.demo.first");
        assertTrue(has(GoogleServicesConfig.parse(json, "com.demo.first"), "DUPLICATE_PACKAGE"));
    }

    @Test
    public void anAppIdFromAnotherProjectNumberWarns() {
        String json = file("com.demo.app").replace("\"project_number\": \"123456789012\"", "\"project_number\": \"999999999999\"");
        assertTrue(has(GoogleServicesConfig.parse(json, "com.demo.app"), "PROJECT_NUMBER_MISMATCH"));
    }

    @Test
    public void issueLinesPointAtTheRightPlace() {
        String json = file("not a package").replace("not a package", "x");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "");
        GoogleServicesConfig.Issue bad = null;
        for (GoogleServicesConfig.Issue i : c.issues()) if (i.code.equals("BAD_PACKAGE")) bad = i;
        assertNotNull(bad);
        String[] lines = json.split("\n");
        assertTrue(lines[bad.line - 1].contains("package_name"));
    }

    @Test
    public void noIssueMessageEverContainsACredential() {
        String json = file("com.demo.app").replace(KEY, "AIzaShortKey123").replace("1:123456789012:android:0123456789abcdef", "1:123456789012:ios:0123456789abcdef");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.other.pkg");
        assertTrue(c.issues().size() >= 2);
        for (GoogleServicesConfig.Issue i : c.issues()) {
            assertFalse(i.message, i.message.contains("AIzaShortKey123"));
            assertFalse(i.message, i.message.contains("0123456789abcdef"));
        }
    }

    // ---- masking and formatting

    @Test
    public void secretsAreMaskedAndTheLinesStayTheSame() {
        String json = file("com.demo.app");
        String masked = GoogleServicesConfig.maskSecrets(json);
        assertFalse(masked.contains(KEY));
        assertFalse(masked.contains("0123456789abcdef"));
        assertTrue(masked.contains("cdef\""));
        assertTrue(masked.contains("AAAA\""));
        assertEquals(json.split("\n", -1).length, masked.split("\n", -1).length);
        // Not secret, so kept.
        assertTrue(masked.contains("demo-app-12345"));
        assertEquals("", GoogleServicesConfig.maskSecrets(null));
    }

    @Test
    public void maskKeepsOnlyTheLastFourCharacters() {
        assertEquals("", GoogleServicesConfig.mask(""));
        assertEquals("abcd", GoogleServicesConfig.mask("abcd"));
        assertEquals("••wxyz", GoogleServicesConfig.mask("abwxyz"));
        assertEquals(16, GoogleServicesConfig.mask("x".repeat(60)).length());
    }

    @Test
    public void formattingKeepsKeyOrderAndValuesAndRejectsBrokenJson() {
        String compact = "{\"b\":\"a&b<c>\",\"a\":[1,2],\"c\":{\"z\":null}}";
        String pretty = GoogleServicesConfig.format(compact);
        assertEquals("{\n  \"b\": \"a&b<c>\",\n  \"a\": [\n    1,\n    2\n  ],\n  \"c\": {\n    \"z\": null\n  }\n}\n", pretty);
        assertNull(GoogleServicesConfig.format("{\"a\":"));
        assertNull(GoogleServicesConfig.format(null));
        // Formatting twice changes nothing more.
        assertEquals(pretty, GoogleServicesConfig.format(pretty));
    }

    // ---- migration of the four stored values

    @Test
    public void storedValuesBecomeAFileThatReadsBackTheSame() {
        String json = GoogleServicesConfig.fromStoredValues("demo-app-12345-default-rtdb.firebaseio.com",
                "1:123456789012:android:0123456789abcdef", KEY, "demo-app-12345.appspot.com", "com.demo.app");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        assertTrue(json, c.isValid());
        assertEquals(0, c.issues().size());
        assertEquals("demo-app-12345", c.projectId());
        assertEquals("123456789012", c.projectNumber());
        assertEquals("demo-app-12345-default-rtdb.firebaseio.com", c.libraryData());
        assertEquals("1:123456789012:android:0123456789abcdef", c.appId());
        assertEquals(KEY, c.apiKey());
        assertEquals("demo-app-12345.appspot.com", c.storageBucket());
    }

    @Test
    public void anOldProjectIdOnlyEntryRebuildsTheDatabaseUrlTheBuildUsed() {
        String json = GoogleServicesConfig.fromStoredValues("demo-app-12345", "1:123456789012:android:0123456789abcdef", KEY, "", "com.demo.app");
        GoogleServicesConfig c = GoogleServicesConfig.parse(json, "com.demo.app");
        assertEquals("demo-app-12345", c.projectId());
        // What the build did with a bare id: https://<id>.firebaseio.com
        assertEquals("https://demo-app-12345.firebaseio.com", c.databaseUrl());
        assertEquals("", c.storageBucket());
    }

    @Test
    public void nothingStoredGivesNoFileAndMissingValuesAreNotInvented() {
        assertEquals("", GoogleServicesConfig.fromStoredValues("", "", "", "", "com.demo.app"));
        assertEquals("", GoogleServicesConfig.fromStoredValues(null, null, null, null, null));
        String partial = GoogleServicesConfig.fromStoredValues("demo-app-12345", "", "", "", "");
        assertFalse(partial.contains("api_key"));
        assertFalse(partial.contains("mobilesdk_app_id"));
        assertFalse(partial.contains("storage_bucket"));
        assertFalse(GoogleServicesConfig.parse(partial, "").isValid());
    }
}
