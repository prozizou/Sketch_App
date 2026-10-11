package pro.sketchware.activities.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.eclipse.jgit.transport.http.JDKHttpConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * A server with a self-signed certificate stands in for someone intercepting the connection. Even when the
 * repository says {@code http.sslVerify = false}, push must refuse it before sending anything.
 */
public class VerifiedHttpConnectionFactoryTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private final AtomicInteger requests = new AtomicInteger();
    private SSLServerSocket server;
    private HttpConnectionFactory previousFactory;

    @Before
    public void startServer() throws Exception {
        previousFactory = HttpTransport.getConnectionFactory();
        File keyStore = new File(temp.getRoot(), "server.p12");
        String keytool = new File(System.getProperty("java.home"), "bin/keytool").getPath();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=127.0.0.1", "-validity", "2", "-storetype", "PKCS12", "-keystore", keyStore.getPath(),
                "-storepass", "password", "-keypass", "password").redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assertEquals("keytool failed", 0, process.waitFor());

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(keyStore)) {
            store.load(in, "password".toCharArray());
        }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, "password".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), null, null);

        server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread thread = new Thread(this::serve, "test-https-server");
        thread.setDaemon(true);
        thread.start();
    }

    /** Answers 404 to every request that gets past the TLS handshake, counting them. */
    private void serve() {
        while (!server.isClosed()) {
            try (SSLSocket socket = (SSLSocket) server.accept()) {
                socket.setSoTimeout(5000);
                try {
                    socket.startHandshake();
                    InputStream in = socket.getInputStream();
                    if (in.read() < 0) continue;
                    requests.incrementAndGet();
                    OutputStream out = socket.getOutputStream();
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                } catch (java.io.IOException handshakeOrReadFailed) {
                    // The client refused the certificate
                }
            } catch (java.io.IOException closed) {
                return;
            }
        }
    }

    @After
    public void stopServer() throws Exception {
        server.close();
        HttpTransport.setConnectionFactory(previousFactory);
    }

    /** A project whose repository config turns certificate checks off, pointed at the test server. */
    private ProjectGit projectTrustingAnyCertificate() throws Exception {
        File workTree = temp.newFolder("data");
        File gitDir = new File(temp.getRoot(), "project.git");
        try (ProjectGit git = ProjectGit.init(gitDir, workTree)) {
            Files.writeString(new File(workTree, "file").toPath(), "content");
            git.commitAll("first", "Ada", "ada@example.com");
            git.setRemoteUrl("https://127.0.0.1:" + server.getLocalPort() + "/project.git");
        }
        try (var repository = new FileRepositoryBuilder().setGitDir(gitDir).setMustExist(true).build()) {
            StoredConfig config = repository.getConfig();
            config.setBoolean("http", null, "sslVerify", false);
            config.save();
        }
        return ProjectGit.open(gitDir, workTree);
    }

    @Test
    public void pushRefusesAnUntrustedCertificateEvenWhenTheConfigSaysNotToCheck() throws Exception {
        try (ProjectGit git = projectTrustingAnyCertificate()) {
            try {
                git.push(new ProjectGit.Credentials("ada", "secret-token"));
                fail("Pushed to a server with an untrusted certificate");
            } catch (ProjectGit.GitProblem expected) {
                // The TLS handshake failed
            }
            assertTrue(HttpTransport.getConnectionFactory() instanceof VerifiedHttpConnectionFactory);
        }
        assertEquals("No request may reach a server with an untrusted certificate", 0, requests.get());
    }

    @Test
    public void installIsIdempotent() {
        VerifiedHttpConnectionFactory.install();
        HttpConnectionFactory installed = HttpTransport.getConnectionFactory();
        VerifiedHttpConnectionFactory.install();
        assertTrue(installed instanceof VerifiedHttpConnectionFactory);
        assertEquals(installed, HttpTransport.getConnectionFactory());
    }

    /** Something else replacing JGit's connection factory must not turn the checks off for the next push. */
    @Test
    public void pushPutsTheCheckingFactoryBack() throws Exception {
        try (ProjectGit git = projectTrustingAnyCertificate()) {
            HttpTransport.setConnectionFactory(new JDKHttpConnectionFactory());
            try {
                git.push(new ProjectGit.Credentials("ada", "secret-token"));
                fail("Pushed to a server with an untrusted certificate");
            } catch (ProjectGit.GitProblem expected) {
                // The TLS handshake failed
            }
            assertTrue(HttpTransport.getConnectionFactory() instanceof VerifiedHttpConnectionFactory);
        }
        assertEquals(0, requests.get());
    }

    /** Plain JGit with that setting really does send the request (and the token) to the server. */
    @Test
    public void jgitAloneWouldTrustTheServer() throws Exception {
        projectTrustingAnyCertificate().close();
        HttpTransport.setConnectionFactory(new JDKHttpConnectionFactory());
        try (Git git = Git.open(new File(temp.getRoot(), "project.git"))) {
            git.push().setRemote("origin").add("main")
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("ada", "secret-token")).call();
        } catch (Exception expected) {
            // The test server has no repository
        }
        assertTrue("JGit should have reached the server when sslVerify is false", requests.get() > 0);
    }
}
