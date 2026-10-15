package pro.sketchware.resolver;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** The real connection code, against a tiny server on this machine (the policy is opened to it for the test only). */
public class UrlConnectionFetcherTest {
    private ServerSocket socket;
    private Thread thread;
    private UrlConnectionFetcher fetcher;
    private final AtomicInteger targetHits = new AtomicInteger();
    private static final Set<String> JSON = Set.of("application/json");

    @Before
    public void start() throws IOException {
        socket = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        thread = new Thread(() -> {
            while (!socket.isClosed()) {
                try (Socket client = socket.accept()) {
                    serve(client);
                } catch (IOException ignored) {
                    // closed by stop()
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        fetcher = new UrlConnectionFetcher(new DownloadPolicy(Set.of("localhost"), false));
    }

    @After
    public void stop() throws IOException {
        socket.close();
    }

    private void serve(Socket client) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
        String request = in.readLine();
        for (String line = in.readLine(); line != null && !line.isEmpty(); line = in.readLine()) {
            // headers are not needed
        }
        String path = request == null ? "" : request.split(" ")[1];
        OutputStream out = client.getOutputStream();
        switch (path) {
            case "/ok" -> respond(out, "200 OK", "application/json; charset=utf-8", "{\"a\":1}".getBytes(StandardCharsets.UTF_8), true, null);
            case "/html" -> respond(out, "200 OK", "text/html", "<html>".getBytes(StandardCharsets.UTF_8), true, null);
            case "/missing" -> respond(out, "404 Not Found", "application/json", new byte[0], true, null);
            case "/big" -> respond(out, "200 OK", "application/json", new byte[5000], true, null);
            case "/bigchunked" -> respond(out, "200 OK", "application/json", new byte[5000], false, null);
            case "/redirect" -> respond(out, "302 Found", "text/plain", new byte[0], true, "Location: http://localhost:" + socket.getLocalPort() + "/target\r\n");
            case "/target" -> {
                targetHits.incrementAndGet();
                respond(out, "200 OK", "application/json", "{}".getBytes(StandardCharsets.UTF_8), true, null);
            }
            default -> respond(out, "404 Not Found", "text/plain", new byte[0], true, null);
        }
        out.flush();
    }

    private static void respond(OutputStream out, String status, String type, byte[] body, boolean withLength, String extraHeader) throws IOException {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append("\r\nContent-Type: ").append(type).append("\r\nConnection: close\r\n");
        if (extraHeader != null) head.append(extraHeader);
        if (withLength) head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
    }

    private URL url(String path) throws IOException {
        return new URL("http://localhost:" + socket.getLocalPort() + path);
    }

    @Test
    public void fetchesWhatWasAskedFor() throws Exception {
        assertEquals("{\"a\":1}", new String(fetcher.get(url("/ok"), 1000, JSON), StandardCharsets.UTF_8));
    }

    @Test
    public void aWrongMediaTypeIsRefused() {
        IOException e = assertThrows(IOException.class, () -> fetcher.get(url("/html"), 1000, JSON));
        assertTrue(e.getMessage(), e.getMessage().contains("text/html"));
    }

    @Test
    public void anErrorStatusIsAnError() {
        IOException e = assertThrows(IOException.class, () -> fetcher.get(url("/missing"), 1000, JSON));
        assertTrue(e.getMessage(), e.getMessage().contains("404"));
    }

    @Test
    public void anAnswerOverTheLimitIsRefusedWhetherOrNotItsSizeIsAnnounced() throws Exception {
        assertTrue(assertThrows(IOException.class, () -> fetcher.get(url("/big"), 1000, JSON)).getMessage().contains("too large"));
        assertTrue(assertThrows(IOException.class, () -> fetcher.get(url("/bigchunked"), 1000, JSON)).getMessage().contains("too large"));
        assertEquals(5000, fetcher.get(url("/big"), 5000, JSON).length);
    }

    @Test
    public void aRedirectIsNeverFollowed() {
        DownloadPolicy.Untrusted e = assertThrows(DownloadPolicy.Untrusted.class, () -> fetcher.get(url("/redirect"), 1000, JSON));
        assertTrue(e.getMessage(), e.getMessage().contains("somewhere else"));
        assertEquals("the target was never asked", 0, targetHits.get());
    }

    @Test
    public void anAddressThePolicyDoesNotAllowIsNotEvenConnectedTo() {
        UrlConnectionFetcher strict = new UrlConnectionFetcher(DownloadPolicy.ICON_SERVICE);
        assertThrows(DownloadPolicy.Untrusted.class, () -> strict.get(url("/target"), 1000, JSON));
        assertEquals(0, targetHits.get());
    }

    @Test
    public void readingStopsAtTheLimit() throws Exception {
        assertArrayEquals(new byte[10], UrlConnectionFetcher.readCapped(new ByteArrayInputStream(new byte[10]), 10));
        assertThrows(IOException.class, () -> UrlConnectionFetcher.readCapped(new ByteArrayInputStream(new byte[11]), 10));
    }
}
