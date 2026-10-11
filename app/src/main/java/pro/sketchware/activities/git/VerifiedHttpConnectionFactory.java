package pro.sketchware.activities.git;

import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.HttpConnectionFactory2;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;

/**
 * HTTP connections for JGit that always check the server's TLS certificate and host name.
 * <p>
 * JGit turns certificate checks off for a connection when a Git configuration says {@code http.sslVerify = false}
 * (repository, user or system file) or when a credentials provider agrees to trust a bad certificate. It then uses
 * its trust-everything {@code NoCheckX509TrustManager}, so anyone on the network could read the access token sent
 * on push and pull. The repository config sits next to the project, where it can be edited from outside the app,
 * so that setting is ignored here: connections keep the system's certificate and host name checks.
 */
final class VerifiedHttpConnectionFactory implements HttpConnectionFactory2 {
    private final JDKHttpConnectionFactory delegate = new JDKHttpConnectionFactory();

    /** Makes every JGit HTTP connection of the app go through this factory. */
    static void install() {
        if (!(HttpTransport.getConnectionFactory() instanceof VerifiedHttpConnectionFactory)) {
            HttpTransport.setConnectionFactory(new VerifiedHttpConnectionFactory());
        }
    }

    @Override
    public HttpConnection create(URL url) throws IOException {
        return delegate.create(url);
    }

    @Override
    public HttpConnection create(URL url, Proxy proxy) throws IOException {
        return delegate.create(url, proxy);
    }

    @Override
    public GitSession newSession() {
        return new GitSession() {
            @Override
            public HttpConnection configure(HttpConnection connection, boolean sslVerify) {
                // Whatever sslVerify says, the connection keeps the platform's default trust managers and host
                // name verifier.
                return connection;
            }

            @Override
            public void close() {
            }
        };
    }
}
