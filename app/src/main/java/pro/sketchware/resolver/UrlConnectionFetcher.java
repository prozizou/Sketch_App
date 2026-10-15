package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads with {@link HttpURLConnection} under a {@link DownloadPolicy}: no redirects are followed (a redirect could
 * lead anywhere), the media type must be one asked for, and the answer is cut off at the size limit instead of being
 * read to the end.
 */
public final class UrlConnectionFetcher implements HttpFetcher {
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 12_000;

    private final DownloadPolicy policy;

    public UrlConnectionFetcher(DownloadPolicy policy) {
        this.policy = policy;
    }

    @NonNull
    @Override
    public byte[] get(@NonNull URL url, long maxBytes, @NonNull Set<String> contentTypes) throws IOException {
        policy.requireTrusted(url);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", String.join(", ", contentTypes));
            connection.setRequestProperty("User-Agent", "SketchwareNWS-ResourceResolver");
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) throw new DownloadPolicy.Untrusted("The server tried to send the app somewhere else");
            if (code != 200) throw new IOException("The server answered " + code);
            String type = connection.getContentType();
            String mime = type == null ? "" : type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!contentTypes.contains(mime)) throw new IOException("The server sent \"" + (mime.isEmpty() ? "no type" : mime) + "\", not what was asked for");
            long length = connection.getContentLengthLong();
            if (length > maxBytes) throw new IOException("The answer is too large (" + length + " bytes)");
            try (InputStream in = connection.getInputStream()) {
                return readCapped(in, maxBytes);
            }
        } finally {
            connection.disconnect();
        }
    }

    static byte[] readCapped(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("The answer is too large");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
