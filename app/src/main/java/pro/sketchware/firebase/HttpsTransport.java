package pro.sketchware.firebase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The real network for {@link FirebaseChecks}: HTTPS only, default certificate checks, short timeouts, no redirects and a
 * capped body. It logs nothing, because the request carries the API key.
 */
public final class HttpsTransport implements FirebaseChecks.Transport {
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_BODY_BYTES = 16 * 1024;

    @Override
    public FirebaseChecks.Response send(String method, String url, Map<String, String> headers, String body) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("Only https is allowed");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (body != null) {
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new FirebaseChecks.Response(code, stream == null ? "" : read(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int n;
            while (out.size() < MAX_BODY_BYTES && (n = stream.read(buffer)) > 0) {
                out.write(buffer, 0, Math.min(n, MAX_BODY_BYTES - out.size()));
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
