package pro.sketchware.resolver;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

/** Downloads a small answer. The real one is {@link UrlConnectionFetcher}; the tests use their own. */
public interface HttpFetcher {
    /**
     * @param url          where to get it from (the implementation checks it against its {@link DownloadPolicy})
     * @param maxBytes     the most it may be; more is an error and is not read
     * @param contentTypes the media types accepted (lowercase, without parameters)
     */
    @NonNull
    byte[] get(@NonNull URL url, long maxBytes, @NonNull Set<String> contentTypes) throws IOException;
}
