package pro.sketchware.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SecurityScannerTest {
    private static List<Finding> scan(String name, String content) {
        return SecurityScanner.analyze(ProjectFacts.EMPTY, List.of(new SourceFile(name, content)), List.of());
    }

    private static List<String> ids(List<Finding> findings) {
        return findings.stream().map(Finding::id).collect(Collectors.toList());
    }

    private static Finding find(List<Finding> findings, String id) {
        return findings.stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
    }

    @Test
    public void aCleanProjectHasNoFindings() {
        assertEquals(List.of(), scan("A.java", "class A { String url = \"https://example.org\"; int x = 1; }"));
    }

    @Test
    public void wellKnownSecretFormatsAreFoundAndNeverRepeatedInTheReport() {
        String aws = "AKIAIOSFODNN7EXAMPLE";
        String github = "ghp_" + "a".repeat(10) + "B".repeat(26);
        String google = "AIza" + "SyA-1234567890abcdefghijklmnopqrstu";
        String content = "String a = \"" + aws + "\";\nString g = \"" + github + "\";\nString k = \"" + google + "\";\n-----BEGIN RSA PRIVATE KEY-----\n";
        List<Finding> findings = scan("Secrets.java", content);
        assertTrue(ids(findings).containsAll(List.of("security.secret-aws", "security.secret-github", "security.secret-google-api-key", "security.secret-private-key")));
        for (Finding f : findings) {
            String evidence = String.valueOf(f.evidence());
            assertFalse(f.id(), evidence.contains(aws));
            assertFalse(f.id(), evidence.contains(github));
            assertFalse(f.id(), evidence.contains(google));
        }
        assertEquals("Secrets.java:1: AKIA…[20 characters hidden]", find(findings, "security.secret-aws").evidence());
        assertEquals(Severity.WARNING, find(findings, "security.secret-google-api-key").severity());
    }

    @Test
    public void hardcodedPasswordsAreFoundButPlaceholdersAreNot() {
        List<Finding> real = scan("A.java", "String password = \"Tr0ub4dor&3xyz\";\nString apiKey = \"k9f83hf72hd9s\";");
        Finding f = find(real, "security.secret-hardcoded");
        assertEquals("Passwords or keys written into the code (2)", f.title());
        assertFalse(f.evidence().contains("Tr0ub4dor"));
        assertTrue(f.evidence().contains("password = Tr0u…"));
        assertEquals(List.of(), scan("A.java", "String password = \"your_password_here\";\nString token = \"xxxxxxxxxx\";\nString secret = \"changeme123\";"));
        assertEquals(List.of(), scan("strings.xml", "<string name=\"password_hint\">@string/enter</string>"));
    }

    @Test
    public void cleartextAddressesIgnoreLocalhostAndXmlNamespaces() {
        List<Finding> findings = scan("A.java", "String a = \"http://api.example.org/v1\";\nString b = \"http://localhost:8080\";\nString c = \"http://schemas.android.com/apk/res/android\";");
        Finding f = find(findings, "security.cleartext-http");
        assertEquals("A.java:1: http://api.example.org/v1", f.evidence());
        assertEquals(1, ids(findings).size());
    }

    @Test
    public void disabledCertificateChecks() {
        String code = "TrustManager t = new X509TrustManager() {\n public void checkServerTrusted(X509Certificate[] c, String a) { }\n};";
        assertEquals(Severity.ERROR, find(scan("Net.java", code), "security.tls-trust-all").severity());
        assertTrue(ids(scan("Net.java", "conn.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);")).contains("security.tls-trust-all"));
        assertTrue(ids(scan("Net.java", "public boolean verify(String h, SSLSession s) { return true; }")).contains("security.tls-trust-all"));
        assertFalse(ids(scan("Net.java", "public void checkServerTrusted(X509Certificate[] c, String a) { check(c); }")).contains("security.tls-trust-all"));
    }

    @Test
    public void riskyApis() {
        List<String> ids = ids(scan("A.java", "getSharedPreferences(\"x\", MODE_WORLD_READABLE);\nweb.addJavascriptInterface(new Bridge(), \"b\");\n"
                + "web.getSettings().setAllowUniversalAccessFromFileURLs(true);\nMessageDigest.getInstance(\"MD5\");\nCipher.getInstance(\"AES\");\nCipher.getInstance(\"DES\");"));
        assertTrue(ids.toString(), ids.containsAll(List.of("security.world-accessible-file", "security.webview-js-bridge", "security.webview-file-access",
                "security.weak-hash", "security.weak-cipher")));
        assertFalse(ids(scan("A.java", "MessageDigest.getInstance(\"SHA-256\"); Cipher.getInstance(\"AES/GCM/NoPadding\");")).contains("security.weak-hash"));
        assertEquals(List.of(), ids(scan("A.java", "Cipher.getInstance(\"AES/GCM/NoPadding\");")));
    }

    @Test
    public void permissions() {
        ProjectFacts facts = new ProjectFacts("a.b", 24, 34, Set.of("android.permission.SYSTEM_ALERT_WINDOW", "android.permission.READ_PHONE_STATE",
                "android.permission.INTERNET"), List.of(), Set.of(), true, Set.of(), List.of(), true);
        Finding high = find(SecurityScanner.analyze(facts, List.of(), List.of()), "security.permission-high-risk");
        assertEquals("READ_PHONE_STATE, SYSTEM_ALERT_WINDOW", high.evidence());

        Set<String> many = Set.of("CAMERA", "RECORD_AUDIO", "READ_CONTACTS", "WRITE_CONTACTS", "ACCESS_FINE_LOCATION", "READ_SMS", "SEND_SMS", "CALL_PHONE");
        ProjectFacts heavy = new ProjectFacts("a.b", 24, 34, many, List.of(), Set.of(), true, Set.of(), List.of(), true);
        assertTrue(ids(SecurityScanner.analyze(heavy, List.of(), List.of())).contains("security.permission-many"));
        assertFalse(ids(SecurityScanner.analyze(facts, List.of(), List.of())).contains("security.permission-many"));
    }

    @Test
    public void vulnerableLibrariesAreMatchedByVersion() {
        List<Finding> findings = SecurityScanner.analyze(ProjectFacts.EMPTY, List.of(),
                List.of("gson-2.8.5", "com.google.code.gson:gson:2.8.9", "okhttp-4.9.0", "commons-text:1.9", "jsoup-1.15.3", "log4j-core-2.14.1"));
        Finding f = find(findings, "security.vulnerable-library");
        assertEquals(Severity.ERROR, f.severity());
        assertTrue(f.evidence(), f.evidence().contains("gson 2.8.5"));
        assertTrue(f.evidence().contains("commons-text 1.9"));
        assertTrue(f.evidence().contains("log4j-core 2.14.1"));
        assertFalse(f.evidence().contains("gson 2.8.9"));
        assertFalse(f.evidence().contains("jsoup"));
        assertFalse(f.evidence().contains("okhttp"));
        assertEquals(List.of(), SecurityScanner.analyze(ProjectFacts.EMPTY, List.of(), List.of("gson-2.10.1", "something")));
    }

    @Test
    public void versionComparisonIsNumericNotAlphabetical() {
        assertTrue(SecurityScanner.compareVersions("1.9", "1.10") < 0);
        assertTrue(SecurityScanner.compareVersions("2.8.5", "2.8.9") < 0);
        assertEquals(0, SecurityScanner.compareVersions("2.0", "2.0.0"));
        assertTrue(SecurityScanner.compareVersions("3.2.2", "3.2.1") > 0);
    }

    @Test
    public void redactionHidesTheValue() {
        assertEquals("…[5 characters hidden]", SecurityScanner.redact("abcde"));
        assertEquals("abcd…[12 characters hidden]", SecurityScanner.redact("abcdefghijkl"));
    }
}
