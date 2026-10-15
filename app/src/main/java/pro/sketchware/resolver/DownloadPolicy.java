package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Set;

/**
 * What the resolver is allowed to download: HTTPS only, from a short list of hosts, on the standard port, with no
 * credentials in the address, and a cap on how much is read. It is checked before a connection is opened and for every
 * address the code builds, so a bug (or a hostile answer) can't send the app somewhere else.
 */
public final class DownloadPolicy {
    /** The address is not one the resolver downloads from. */
    public static final class Untrusted extends IOException {
        public Untrusted(String message) {
            super(message);
        }
    }

    /** The only host icons come from. */
    public static final String ICON_HOST = "api.iconify.design";
    /** The policy the app uses. */
    public static final DownloadPolicy ICON_SERVICE = new DownloadPolicy(Set.of(ICON_HOST), true);

    private final Set<String> hosts;
    private final boolean httpsOnly;

    /**
     * @param hosts     the host names allowed (lowercase)
     * @param httpsOnly whether anything but HTTPS is refused (only the tests turn this off, to talk to a local server)
     */
    public DownloadPolicy(Set<String> hosts, boolean httpsOnly) {
        this.hosts = Set.copyOf(hosts);
        this.httpsOnly = httpsOnly;
    }

    /** @throws Untrusted if the app may not download from this address */
    public void requireTrusted(@NonNull URL url) throws Untrusted {
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        if (httpsOnly ? !protocol.equals("https") : !(protocol.equals("https") || protocol.equals("http"))) {
            throw new Untrusted("Only secure (https) downloads are allowed");
        }
        if (url.getUserInfo() != null) throw new Untrusted("The address has a user name or password");
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
        if (!hosts.contains(host)) throw new Untrusted("Downloads from " + (host.isEmpty() ? "that address" : host) + " aren't allowed");
        if (httpsOnly && url.getPort() != -1 && url.getPort() != 443) throw new Untrusted("Only the standard secure port is allowed");
        if (url.getRef() != null) throw new Untrusted("The address has a fragment");
    }
}
