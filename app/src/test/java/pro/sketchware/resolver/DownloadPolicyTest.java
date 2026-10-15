package pro.sketchware.resolver;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.net.URL;
import java.util.Set;

public class DownloadPolicyTest {
    private static final DownloadPolicy POLICY = DownloadPolicy.ICON_SERVICE;

    private static void ok(String url) throws Exception {
        POLICY.requireTrusted(new URL(url));
    }

    private static void refused(String url) throws Exception {
        assertThrows(url, DownloadPolicy.Untrusted.class, () -> POLICY.requireTrusted(new URL(url)));
    }

    @Test
    public void theIconServiceOverHttpsIsAllowed() throws Exception {
        ok("https://api.iconify.design/search?query=add&limit=10");
        ok("https://api.iconify.design/ic.json?icons=baseline-add");
        ok("https://API.ICONIFY.DESIGN/mdi/home.svg");
        ok("https://api.iconify.design:443/ic.json");
    }

    @Test
    public void everythingElseIsRefused() throws Exception {
        refused("http://api.iconify.design/search?query=add");
        refused("ftp://api.iconify.design/x");
        refused("https://example.com/icon.svg");
        refused("https://api.iconify.design.evil.com/x");
        refused("https://evil.com/api.iconify.design");
        refused("https://iconify.design/x");
        refused("https://api.iconify.design@evil.com/x");
        refused("https://user:pass@api.iconify.design/x");
        refused("https://api.iconify.design:8443/x");
        refused("https://127.0.0.1/x");
        refused("https://localhost/x");
        refused("https://api.iconify.design/x#fragment");
    }

    @Test
    public void aTestPolicyCanOpenALocalServerButStillChecksTheHost() throws Exception {
        DownloadPolicy local = new DownloadPolicy(Set.of("localhost"), false);
        local.requireTrusted(new URL("http://localhost:8080/x"));
        assertThrows(DownloadPolicy.Untrusted.class, () -> local.requireTrusted(new URL("http://127.0.0.1:8080/x")));
        assertThrows(DownloadPolicy.Untrusted.class, () -> local.requireTrusted(new URL("file:///etc/passwd")));
    }
}
