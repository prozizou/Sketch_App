package pro.sketchware.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

import a.a.a.Lx;
import pro.sketchware.analysis.Finding;
import pro.sketchware.analysis.ProjectFacts;
import pro.sketchware.analysis.SecurityScanner;
import pro.sketchware.analysis.SourceFile;

/** The RequestNetworkController written into apps that use RequestNetwork must keep TLS checks on. */
public class GeneratedNetworkCodeTest {
    private final String code = Lx.g("com.example.app");

    @Test
    public void doesNotTrustEveryServer() {
        for (String insecure : new String[]{"TrustManager", "HostnameVerifier", "sslSocketFactory", "return true;"}) {
            assertFalse(insecure, code.contains(insecure));
        }
        List<Finding> findings = SecurityScanner.analyze(ProjectFacts.EMPTY,
                List.of(new SourceFile("RequestNetworkController.java", code)), List.of());
        assertTrue(findings.stream().noneMatch(f -> f.id().equals("security.tls-trust-all")));
    }

    @Test
    public void keepsTheTimeouts() {
        assertTrue(code.contains("builder.connectTimeout(SOCKET_TIMEOUT, TimeUnit.MILLISECONDS);"));
        assertTrue(code.contains("builder.readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS);"));
        assertTrue(code.contains("builder.writeTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS);"));
        assertTrue(code.contains("import java.util.concurrent.TimeUnit;"));
    }

    @Test
    public void bracesBalance() {
        assertEquals(code.chars().filter(c -> c == '{').count(), code.chars().filter(c -> c == '}').count());
    }
}
