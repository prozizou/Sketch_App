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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds, on the internet, the library a project needs but that is not on the phone: from the dependency
 * it was downloaded with when known, else from its folder name, looked up in several places in turn:
 * Google's Maven repository (AndroidX and other Google groups guessed from the name), Maven Central's search
 * (the old and the new search host), Google's Maven index, and GitHub repositories built by JitPack.
 *
 * <p>A slow or unreachable source only skips to the next one; the search fails as offline only when no source
 * answered at all.
 */
public class OnlineLibraryFinder {
    private static final String[] SEARCH_HOSTS = {
            "https://search.maven.org/solrsearch/select?wt=json&rows=20&q=",
            "https://central.sonatype.com/solrsearch/select?wt=json&rows=20&q="
    };
    private static final String GOOGLE = "https://dl.google.com/dl/android/maven2";
    private static final String JITPACK = "https://jitpack.io";
    private static final String[] REPOSITORIES = {
            GOOGLE,
            "https://repo.maven.apache.org/maven2",
            JITPACK
    };
    private static final Pattern INDEX_GROUP = Pattern.compile("<([A-Za-z0-9_.\\-]+)\\s*/>");
    private static final Pattern GITHUB_REPO = Pattern.compile("\"full_name\"\\s*:\\s*\"([^\"/]+)/([^\"]+)\"");

    /** Whether any source answered (even "not found"), and the last network error seen. */
    private boolean reachedAny;
    @Nullable
    private String lastNetworkError;
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

        public Plan(String libraryName, @Nullable String group, @Nullable String artifact, @Nullable String version) {
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

    /** Plans the download of one library of a project. Throws when no source on the network could be reached. */
    public Plan plan(HashMap<String, Object> libraryEntry) throws IOException {
        reachedAny = false;
        lastNetworkError = null;
        Plan plan = planUnchecked(libraryEntry);
        if (!plan.found() && !reachedAny && lastNetworkError != null) {
            throw new IOException(lastNetworkError);
        }
        return plan;
    }

    private Plan planUnchecked(HashMap<String, Object> libraryEntry) {
        String name = String.valueOf(libraryEntry.get("name"));
        Object dependency = libraryEntry.get("dependency");
        if (dependency != null) {
            String[] parts = dependency.toString().split(":");
            if (parts.length == 3) {
                Plan plan = planFor(name, parts[0], parts[1], parts[2]);
                if (plan.found()) return plan;
            }
        }

        LibraryNameParser parsed = LibraryNameParser.parse(name);
        String artifact = parsed.artifactId;

        // 1. Google's Maven repository, groups guessed from the name: one cheap request each.
        for (String group : guessGoogleGroups(artifact)) {
            Plan plan = planFor(name, group, artifact, parsed.version, GOOGLE);
            if (plan.found()) return plan;
        }

        // 2. Maven Central's search, on the old host then the new one.
        for (String host : SEARCH_HOSTS) {
            String answer = fetchOrNull(host + encode("a:\"" + artifact + "\""));
            if (answer == null) continue;
            Candidate candidate = bestCandidate(artifact, parseSearch(answer));
            if (candidate != null) {
                Plan plan = planFor(name, candidate.group, candidate.artifact, parsed.version);
                if (plan.found()) return plan;
            }
            break;
        }

        // 3. Google's Maven index: the groups whose name shares a word with the library.
        String masterIndex = fetchOrNull(GOOGLE + "/master-index.xml");
        if (masterIndex != null) {
            int checked = 0;
            for (String group : googleGroupsMatching(artifact, parseIndexGroups(masterIndex))) {
                if (checked++ >= 12) break;
                String groupIndex = fetchOrNull(GOOGLE + "/" + group.replace('.', '/') + "/group-index.xml");
                List<String> versions = groupIndex == null ? List.of() : parseGroupIndexVersions(groupIndex, artifact);
                String best = MavenVersions.pickBest(parsed.version, versions);
                if (best != null) return new Plan(name, group, artifact, best);
            }
        }

        // 4. GitHub repositories of that name, built by JitPack.
        String repos = fetchOrNull("https://api.github.com/search/repositories?per_page=5&sort=stars&q="
                + encode(artifact + " in:name"));
        if (repos != null) {
            for (String[] repo : parseGitHubRepos(repos)) {
                if (!repo[1].equalsIgnoreCase(artifact)) continue;
                Plan plan = planFor(name, "com.github." + repo[0], repo[1], parsed.version, JITPACK);
                if (plan.found()) return plan;
            }
        }
        return new Plan(name, null, null, null);
    }

    private Plan planFor(String name, String group, String artifact, @Nullable String wantedVersion) {
        return planFor(name, group, artifact, wantedVersion, REPOSITORIES);
    }

    private Plan planFor(String name, String group, String artifact, @Nullable String wantedVersion, String... repositories) {
        List<String> versions = new ArrayList<>();
        for (String repository : repositories) {
            // Each repository only has its own libraries; a miss here is normal.
            String metadata = fetchOrNull(repository + "/" + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml");
            if (metadata != null) {
                versions.addAll(parseVersions(metadata));
            }
        }
        String best = MavenVersions.pickBest(wantedVersion, versions);
        if (best == null) {
            return new Plan(name, null, null, null);
        }
        return new Plan(name, group, artifact, best);
    }

    public List<Candidate> searchByArtifact(String artifactId) throws IOException {
        String query = encode("a:\"" + artifactId + "\"");
        return parseSearch(fetch(SEARCH_HOSTS[0] + query));
    }

    /**
     * Google groups a library folder name most likely belongs to: AndroidX names its groups after the library
     * ({@code activity} → {@code androidx.activity}, {@code appcompat-resources} → {@code androidx.appcompat},
     * {@code core-common} → {@code androidx.arch.core}), and a few Google libraries keep their own group.
     */
    public static List<String> guessGoogleGroups(String artifact) {
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        String lower = artifact.toLowerCase(Locale.ROOT);
        String first = lower.contains("-") ? lower.substring(0, lower.indexOf('-')) : lower;
        groups.add("androidx." + lower.replace('-', '.'));
        groups.add("androidx." + first);
        if (lower.startsWith("core-")) groups.add("androidx.arch.core");
        if (lower.startsWith("lifecycle")) groups.add("androidx.lifecycle");
        if (lower.startsWith("room")) groups.add("androidx.room");
        if (lower.startsWith("work")) groups.add("androidx.work");
        if (lower.startsWith("navigation")) groups.add("androidx.navigation");
        if (lower.startsWith("camera")) groups.add("androidx.camera");
        if (lower.startsWith("media3")) groups.add("androidx.media3");
        if (lower.equals("material")) groups.add("com.google.android.material");
        if (lower.startsWith("play-services")) groups.add("com.google.android.gms");
        if (lower.startsWith("firebase")) groups.add("com.google.firebase");
        if (lower.startsWith("flexbox")) groups.add("com.google.android.flexbox");
        return new ArrayList<>(groups);
    }

    /** @return the groups listed in Google's master-index.xml. */
    public static List<String> parseIndexGroups(String masterIndexXml) {
        List<String> groups = new ArrayList<>();
        Matcher matcher = INDEX_GROUP.matcher(masterIndexXml);
        while (matcher.find()) {
            groups.add(matcher.group(1));
        }
        return groups;
    }

    /** @return the Google groups worth checking for this artifact: those sharing its first word, AndroidX first. */
    public static List<String> googleGroupsMatching(String artifact, List<String> groups) {
        String lower = artifact.toLowerCase(Locale.ROOT);
        String first = lower.contains("-") ? lower.substring(0, lower.indexOf('-')) : lower;
        List<String> androidx = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String group : groups) {
            String last = group.substring(group.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (last.equals(first) || last.equals(lower) || group.toLowerCase(Locale.ROOT).contains("." + first)) {
                (group.startsWith("androidx.") ? androidx : others).add(group);
            }
        }
        androidx.addAll(others);
        return androidx;
    }

    /** @return the versions a Google group-index.xml lists for one artifact, empty when it doesn't have it. */
    public static List<String> parseGroupIndexVersions(String groupIndexXml, String artifact) {
        Matcher matcher = Pattern.compile("<" + Pattern.quote(artifact) + "\\s+versions=\"([^\"]*)\"").matcher(groupIndexXml);
        if (!matcher.find()) {
            return List.of();
        }
        List<String> versions = new ArrayList<>();
        for (String version : matcher.group(1).split(",")) {
            if (!version.trim().isEmpty()) versions.add(version.trim());
        }
        return versions;
    }

    /** @return {owner, name} of each repository in a GitHub repository search answer. */
    public static List<String[]> parseGitHubRepos(String json) {
        List<String[]> repos = new ArrayList<>();
        Matcher matcher = GITHUB_REPO.matcher(json);
        while (matcher.find()) {
            repos.add(new String[]{matcher.group(1), matcher.group(2)});
        }
        return repos;
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

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /**
     * @return the answer, or null when this source doesn't have it or can't be reached right now (the search then
     * moves on to the next source).
     */
    @Nullable
    private String fetchOrNull(String address) {
        try {
            String body = fetch(address);
            reachedAny = true;
            return body;
        } catch (HttpStatusException answeredButMissing) {
            reachedAny = true;
            return null;
        } catch (IOException unreachable) {
            lastNetworkError = unreachable.getMessage() != null ? unreachable.getMessage() : unreachable.getClass().getSimpleName();
            return null;
        }
    }

    /** The server answered, just not with the file: the network works. */
    private static class HttpStatusException extends IOException {
        HttpStatusException(String message) {
            super(message);
        }
    }

    private static String fetch(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        // Short: a slow source should not hold the others up.
        connection.setConnectTimeout(6_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("User-Agent", "NewWorldSketchware");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new HttpStatusException("HTTP " + connection.getResponseCode() + " for " + address);
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
