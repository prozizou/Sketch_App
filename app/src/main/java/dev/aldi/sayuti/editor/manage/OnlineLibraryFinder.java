package dev.aldi.sayuti.editor.manage;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds, on the internet, the library a project needs but that is not on the phone: from the dependency
 * it was downloaded with when known, else from its folder name searched on Maven Central.
 */
public class OnlineLibraryFinder {
    private static final String SEARCH = "https://search.maven.org/solrsearch/select?wt=json&rows=20&q=";
    private static final String[] REPOSITORIES = {
            "https://repo.maven.apache.org/maven2",
            "https://dl.google.com/dl/android/maven2"
    };
    private static final Pattern VERSION_TAG = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>");

    /** What to download for one missing library. */
    public static class Plan {
        public final String libraryName;
        @Nullable
        public final String group;
        @Nullable
        public final String artifact;
        @Nullable
        public final String version;

        Plan(String libraryName, @Nullable String group, @Nullable String artifact, @Nullable String version) {
            this.libraryName = libraryName;
            this.group = group;
            this.artifact = artifact;
            this.version = version;
        }

        public boolean found() {
            return group != null && artifact != null && version != null;
        }

        public String coordinate() {
            return group + ":" + artifact + ":" + version;
        }
    }

    public static class Candidate {
        public final String group;
        public final String artifact;
        public final int versionCount;

        Candidate(String group, String artifact, int versionCount) {
            this.group = group;
            this.artifact = artifact;
            this.versionCount = versionCount;
        }
    }

    /** Plans the download of one library of a project. Throws when the network cannot be reached. */
    public Plan plan(HashMap<String, Object> libraryEntry) throws IOException {
        String name = String.valueOf(libraryEntry.get("name"));
        Object dependency = libraryEntry.get("dependency");
        if (dependency != null) {
            String[] parts = dependency.toString().split(":");
            if (parts.length == 3) {
                return planFor(name, parts[0], parts[1], parts[2]);
            }
        }

        LibraryNameParser parsed = LibraryNameParser.parse(name);
        Candidate candidate = bestCandidate(parsed.artifactId, searchByArtifact(parsed.artifactId));
        if (candidate == null) {
            return new Plan(name, null, null, null);
        }
        return planFor(name, candidate.group, candidate.artifact, parsed.version);
    }

    private Plan planFor(String name, String group, String artifact, @Nullable String wantedVersion) throws IOException {
        List<String> versions = new ArrayList<>();
        for (String repository : REPOSITORIES) {
            try {
                versions.addAll(parseVersions(fetch(repository + "/" + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml")));
            } catch (IOException notInThisRepository) {
                // Each repository only has its own libraries; a miss here is normal.
            }
        }
        String best = MavenVersions.pickBest(wantedVersion, versions);
        if (best == null) {
            return new Plan(name, null, null, null);
        }
        return new Plan(name, group, artifact, best);
    }

    public List<Candidate> searchByArtifact(String artifactId) throws IOException {
        String query = URLEncoder.encode("a:\"" + artifactId + "\"", "UTF-8");
        return parseSearch(fetch(SEARCH + query));
    }

    /** The exact artifact name that has the most releases: the most established library of that name. */
    @Nullable
    public static Candidate bestCandidate(String artifactId, List<Candidate> candidates) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (!candidate.artifact.equalsIgnoreCase(artifactId)) {
                continue;
            }
            if (best == null || candidate.versionCount > best.versionCount) {
                best = candidate;
            }
        }
        return best;
    }

    public static List<Candidate> parseSearch(String json) {
        List<Candidate> candidates = new ArrayList<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray docs = root.getAsJsonObject("response").getAsJsonArray("docs");
            for (JsonElement element : docs) {
                JsonObject doc = element.getAsJsonObject();
                if (doc.has("g") && doc.has("a")) {
                    int count = doc.has("versionCount") ? doc.get("versionCount").getAsInt() : 0;
                    candidates.add(new Candidate(doc.get("g").getAsString(), doc.get("a").getAsString(), count));
                }
            }
        } catch (RuntimeException notTheExpectedAnswer) {
            candidates.clear();
        }
        return candidates;
    }

    public static List<String> parseVersions(String metadataXml) {
        List<String> versions = new ArrayList<>();
        Matcher matcher = VERSION_TAG.matcher(metadataXml);
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        return versions;
    }

    private static String fetch(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode() + " for " + address);
            }
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
