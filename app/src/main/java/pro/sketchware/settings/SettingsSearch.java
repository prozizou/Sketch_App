package pro.sketchware.settings;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds App Settings rows from what the user types. Pure Java so it can be unit tested.
 * <p>
 * Every word of the query has to appear in the row's title, summary, location or keywords. Case and accents
 * are ignored, so "theme", "Thème" and "THEME" find the same rows. Rows whose title matches come first.
 */
public final class SettingsSearch {

    /**
     * One searchable row.
     *
     * @param key         the preference key
     * @param title       the row's title
     * @param summary     the row's description, may be empty
     * @param screenKey   the category screen holding the row, {@code null} for the main page
     * @param screenTitle the category's title, empty for the main page
     * @param section     the section inside the category, may be empty
     */
    public record Entry(String key, String title, String summary, String screenKey, String screenTitle,
                        String section) {

        /**
         * @return where the row lives, e.g. "Build &amp; Signing › Signing".
         */
        public String location() {
            if (screenTitle.isEmpty()) {
                return section;
            }
            return section.isEmpty() ? screenTitle : screenTitle + " › " + section;
        }

        /**
         * @return whether this entry is a category of the main page rather than a setting.
         */
        public boolean isScreen() {
            return key.equals(screenKey);
        }
    }

    /**
     * Words people search for that are not in the row's text, including the French ones.
     */
    public static final Map<String, String> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("screen-appearance", "look theme dark light apparence editeur");
        KEYWORDS.put("screen-projects", "workspace files disk space projets stockage fichiers");
        KEYWORDS.put("screen-backup", "backup restore security sauvegarde securite restauration");
        KEYWORDS.put("screen-build", "compile apk aab sign gradle r8 proguard compilation signature");
        KEYWORDS.put("screen-performance", "speed fast ram memory cpu vitesse memoire");
        KEYWORDS.put("screen-updates", "version release mise a jour");
        KEYWORDS.put("screen-diagnostics", "debug logs crash error diagnostic journaux erreur");
        KEYWORDS.put("screen-advanced", "developer experimental flags avance developpeur");
        KEYWORDS.put("app-theme", "dark light night mode color colour sombre clair nuit couleur");
        KEYWORDS.put("built-in-blocks", "palette logic blocs");
        KEYWORDS.put("always-show-blocks", "variables palette blocs");
        KEYWORDS.put("use-asd-highlighter", "syntax color colour coloration");
        KEYWORDS.put("auto-save-interval", "autosave save timer enregistrement automatique");
        KEYWORDS.put("auto-save-mode", "autosave snapshot enregistrement");
        KEYWORDS.put("ff-device-preview", "tablet foldable landscape orientation safe area tablette pliable apercu");
        KEYWORDS.put("ff-block-debugger", "debug step breakpoint variables debogueur pas a pas");
        KEYWORDS.put("use-new-version-control", "version name code");
        KEYWORDS.put("auto-version-code", "increment version");
        KEYWORDS.put("root-auto-install-projects", "root su install apk installer");
        KEYWORDS.put("root-auto-open-after-installing", "root launch open lancer ouvrir");
        KEYWORDS.put("clear-cache", "cache free space clean delete nettoyer vider espace");
        KEYWORDS.put("storage-usage", "disk space size espace taille stockage");
        KEYWORDS.put("auto-snapshots", "snapshot backup copy instantane sauvegarde");
        KEYWORDS.put("auto-backup", "backup swb sauvegarde automatique");
        KEYWORDS.put("auto-backup-frequency", "daily weekly backup frequence quotidienne");
        KEYWORDS.put("backup-retention", "keep delete old backups conserver");
        KEYWORDS.put("restore-backup", "restore recover import swb restaurer recuperer");
        KEYWORDS.put("crash-recovery", "crash recover lost work plantage recuperation");
        KEYWORDS.put("backup-dir", "folder path directory dossier chemin");
        KEYWORDS.put("backup-filename", "name pattern format nom fichier");
        KEYWORDS.put("signing-keystore", "keystore jks key signing cle signature");
        KEYWORDS.put("build-mode", "debug release apk aab compile mode compilation");
        KEYWORDS.put("build-history", "builds history results historique compilations");
        KEYWORDS.put("ff-build-doctor", "build error failure fix compile erreur compilation");
        KEYWORDS.put("keystore-path", "keystore jks key signing release cle signature");
        KEYWORDS.put("keystore-alias", "keystore key alias signing cle");
        KEYWORDS.put("keystore-verify", "keystore password expiry check certificate verifier mot de passe");
        KEYWORDS.put("keystore-create", "keystore new key generate creer cle");
        KEYWORDS.put("ff-release-manager", "release mapping r8 proguard publish publication");
        KEYWORDS.put("ff-export-ci", "github actions ci workflow android studio export");
        KEYWORDS.put("ff-export-ci-lint", "github lint ci");
        KEYWORDS.put("ff-export-ci-tests", "github tests junit ci");
        KEYWORDS.put("memory-alerts", "ram memory low oom memoire alerte");
        KEYWORDS.put("memory-alert-threshold", "ram memory percent seuil memoire");
        KEYWORDS.put("ff-memory-aware-build", "ram memory threads oom d8 r8 dex memoire");
        KEYWORDS.put("ff-force-low-memory-build", "ram memory slow oom memoire");
        KEYWORDS.put("show-every-single-block", "palette blocks slow lent blocs");
        KEYWORDS.put("app-version", "version about a propos");
        KEYWORDS.put("update-channel", "beta stable dev canal");
        KEYWORDS.put("check-updates", "update new version mise a jour");
        KEYWORDS.put("health-check", "problems scan check analyse sante");
        KEYWORDS.put("ff-project-analysis", "health compatibility security dependencies sante securite");
        KEYWORDS.put("ff-auto-fix", "fix repair correct corriger correction automatique");
        KEYWORDS.put("view-logs", "logs logcat errors journaux");
        KEYWORDS.put("diagnostic-report", "report device info bug rapport");
        KEYWORDS.put("export-settings", "export save share file json exporter partager");
        KEYWORDS.put("import-settings", "import load file json importer charger");
        KEYWORDS.put("ff-data-designer", "rest api sqlite firebase database model generator base de donnees");
        KEYWORDS.put("ff-layout-guides", "alignment guides snap distances alignement");
        KEYWORDS.put("reset-settings", "reset defaults restore reinitialiser defaut");
    }

    private final List<Entry> entries;
    private final Map<String, String> keywords;

    public SettingsSearch(List<Entry> entries) {
        this(entries, KEYWORDS);
    }

    public SettingsSearch(List<Entry> entries, Map<String, String> keywords) {
        this.entries = List.copyOf(entries);
        this.keywords = keywords;
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * @return the rows matching {@code query}, best first, at most {@code limit} of them. An empty query finds nothing.
     */
    public List<Entry> search(String query, int limit) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        String[] words = normalized.split(" ");
        List<int[]> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            int score = score(entries.get(i), normalized, words);
            if (score > 0) {
                scored.add(new int[]{score, i});
            }
        }
        scored.sort((a, b) -> a[0] != b[0] ? Integer.compare(b[0], a[0]) : Integer.compare(a[1], b[1]));
        List<Entry> found = new ArrayList<>();
        for (int[] item : scored) {
            if (found.size() == limit) {
                break;
            }
            found.add(entries.get(item[1]));
        }
        return found;
    }

    private int score(Entry entry, String query, String[] words) {
        String title = normalize(entry.title());
        String others = normalize(entry.summary() + " " + entry.location() + " "
                + keywords.getOrDefault(entry.key(), "") + " " + entry.key().replace('-', ' '));
        int titleWords = 0;
        for (String word : words) {
            boolean inTitle = startsWord(title, word);
            if (!inTitle && !title.contains(word) && !others.contains(word)) {
                return 0;
            }
            if (inTitle) {
                titleWords++;
            }
        }
        int score;
        if (title.equals(query)) {
            score = 100;
        } else if (title.startsWith(query)) {
            score = 80;
        } else if (titleWords == words.length) {
            score = 60;
        } else if (title.contains(query)) {
            score = 50;
        } else {
            score = 10 + 10 * titleWords;
        }
        // A setting is more useful than the category holding it when both match equally.
        return entry.isScreen() ? score - 1 : score;
    }

    private static boolean startsWord(String text, String word) {
        int from = 0;
        while (true) {
            int at = text.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            if (at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1))) {
                return true;
            }
            from = at + 1;
        }
    }

    /**
     * Lower-cases, removes accents and punctuation, and collapses spaces: "Build &amp; Signing" becomes "build signing".
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(decomposed.length());
        boolean space = true;
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
                space = false;
            } else if (!space) {
                out.append(' ');
                space = true;
            }
        }
        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == ' ') {
            out.setLength(end - 1);
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
