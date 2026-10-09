package pro.sketchware.analysis;

import android.os.Environment;

import com.besome.sketch.Config;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import a.a.a.jC;
import a.a.a.lC;
import a.a.a.yB;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.agus.jcoderz.beans.ViewBeans;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.code.completion.ClassIndex;
import mod.jbk.editor.manage.library.ExcludeBuiltInLibrariesActivity;
import pro.sketchware.analysis.DependencyInspector.Library;
import pro.sketchware.analysis.ViewFacts.Kind;
import pro.sketchware.settings.AppLog;
import pro.sketchware.utility.FilePathUtil;

/**
 * Reads a project's data from the app's own classes and files into the plain models the analyses work on.
 * Everything is read-only. It must run on a background thread, and only while the project is open in the editor,
 * because the widgets and library settings are read from the editor's loaded data.
 */
public final class ProjectFactsLoader {
    /** Everything gathered about a project. */
    public record Loaded(ProjectFacts facts, List<SourceFile> files, List<String> libraryNames, Map<String, Library> builtInGraph,
                         List<String> builtInRoots, Set<String> excludedBuiltIn, Map<String, Set<String>> classesByLibrary,
                         List<pro.sketchware.logic.LogicScreen> logicScreens) {
    }

    private static final String TAG = "ProjectFactsLoader";
    private static final long MAX_FILE_BYTES = 300 * 1024;
    private static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024;
    private static final Set<String> CODE_EXTENSIONS = Set.of("java", "kt");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("xml", "json", "txt", "properties", "js", "html", "cfg", "ini", "env", "yml", "yaml", "gradle", "md", "csv");

    private ProjectFactsLoader() {
    }

    public static Loaded load(String scId) {
        FilePathUtil paths = new FilePathUtil();
        HashMap<String, Object> metadata = lC.b(scId);
        ProjectSettings settings = new ProjectSettings(scId);

        int minSdk = settings.getMinSdkVersion();
        int targetSdk = parseInt(settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(Config.VAR_DEFAULT_TARGET_SDK_VERSION)), Config.VAR_DEFAULT_TARGET_SDK_VERSION);

        boolean appCompat = isUsed(jC.c(scId).c());
        List<String> roots = builtInRoots(scId);
        Map<String, Library> graph = builtInGraph();
        Set<String> excluded = new TreeSet<>();
        for (BuiltInLibraries.BuiltInLibrary library : ExcludeBuiltInLibrariesActivity.getExcludedLibraries(scId)) excluded.add(library.getName());

        // Libraries that end up in the app, with where their classes are
        Map<String, Set<String>> classesByLibrary = new TreeMap<>();
        List<String> libraryNames = new ArrayList<>();
        for (String name : DependencyInspector.closure(graph, roots, excluded)) {
            libraryNames.add(name);
            addClasses(classesByLibrary, name, BuiltInLibraries.getLibraryClassesJarPathString(name));
        }
        for (HashMap<String, Object> local : LocalLibrariesUtil.getLocalLibraries(scId)) {
            Object name = local.get("name");
            if (name == null) continue;
            libraryNames.add(name.toString());
            Object jar = local.get("jarPath");
            if (jar != null) addClasses(classesByLibrary, name.toString(), jar.toString());
        }

        // Files
        List<SourceFile> sources = new ArrayList<>();
        List<SourceFile> otherFiles = new ArrayList<>();
        long[] budget = {MAX_TOTAL_BYTES};
        File javaDir = new File(paths.getPathJava(scId));
        collect(javaDir, javaDir, CODE_EXTENSIONS, sources, budget);
        File filesRoot = javaDir.getParentFile();
        if (filesRoot != null) {
            collect(new File(filesRoot, "assets"), new File(filesRoot, "assets"), TEXT_EXTENSIONS, otherFiles, budget);
            collect(new File(filesRoot, "resource"), new File(filesRoot, "resource"), TEXT_EXTENSIONS, otherFiles, budget);
        }
        File dataDir = new File(Environment.getExternalStorageDirectory(), ".sketch_nws/data/" + scId);
        collectIfFile(new File(dataDir, "google-services.json"), "google-services.json", otherFiles, budget);

        ProjectFacts facts = new ProjectFacts(yB.c(metadata, "my_sc_pkg_name"), minSdk, targetSdk, permissions(paths, scId), sources,
                new HashSet<>(libraryNames), appCompat, nativeAbis(paths, scId), views(scId), hasNightResources(paths, scId));
        List<pro.sketchware.logic.LogicScreen> logic;
        try {
            logic = pro.sketchware.logic.LogicFactsLoader.load(scId);
        } catch (RuntimeException e) {
            // The logic checks are skipped rather than failing the whole analysis
            AppLog.e(TAG, "Couldn't read the blocks: " + e);
            logic = List.of();
        }
        return new Loaded(facts, otherFiles, libraryNames, graph, roots, excluded, classesByLibrary, logic);
    }

    // ---- libraries

    /** The built-in libraries the Library Manager's switches ask for (AppCompat and Material, Firebase, AdMob, Maps). */
    public static List<String> builtInRoots(String scId) {
        List<String> roots = new ArrayList<>();
        if (isUsed(jC.c(scId).c())) {
            roots.add(BuiltInLibraries.ANDROIDX_APPCOMPAT);
            roots.add(BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT);
            roots.add(BuiltInLibraries.MATERIAL);
        }
        if (isUsed(jC.c(scId).d())) roots.add(BuiltInLibraries.FIREBASE_COMMON);
        if (isUsed(jC.c(scId).b())) roots.add(BuiltInLibraries.PLAY_SERVICES_ADS);
        if (isUsed(jC.c(scId).e())) roots.add(BuiltInLibraries.PLAY_SERVICES_MAPS);
        return roots;
    }

    /** Every built-in library with what it needs. */
    public static Map<String, Library> builtInGraph() {
        Map<String, Library> graph = new TreeMap<>();
        for (BuiltInLibraries.BuiltInLibrary library : BuiltInLibraries.KNOWN_BUILT_IN_LIBRARIES) {
            Set<String> packages = library.getPackageName().map(p -> Set.of(p)).orElse(Set.of());
            graph.put(library.getName(), new Library(library.getName(), library.getDependencyNames(), packages));
        }
        return graph;
    }

    /** Java packages that the project's local libraries contain, which can stand in for an excluded built-in library. */
    public static Set<String> packagesOfLocalLibraries(String scId) {
        Set<String> packages = new HashSet<>();
        for (HashMap<String, Object> local : LocalLibrariesUtil.getLocalLibraries(scId)) {
            Object jar = local.get("jarPath");
            if (jar == null || !new File(jar.toString()).isFile()) continue;
            try {
                for (String className : ClassIndex.fromJar(new File(jar.toString())).all()) packages.add(ClassIndex.packageOf(className));
            } catch (IOException | RuntimeException e) {
                AppLog.e(TAG, "Couldn't read " + jar + ": " + e);
            }
        }
        return packages;
    }

    private static boolean isUsed(ProjectLibraryBean bean) {
        return bean != null && ProjectLibraryBean.LIB_USE_Y.equals(bean.useYn);
    }

    private static void addClasses(Map<String, Set<String>> into, String library, String jarPath) {
        File jar = new File(jarPath);
        if (!jar.isFile()) return;
        try {
            into.put(library, new HashSet<>(ClassIndex.fromJar(jar).all()));
        } catch (IOException | RuntimeException e) {
            AppLog.e(TAG, "Couldn't read the classes of " + library + ": " + e);
        }
    }

    // ---- permissions, native libraries, resources

    private static Set<String> permissions(FilePathUtil paths, String scId) {
        File file = new File(paths.getPathPermission(scId));
        if (!file.isFile()) return Set.of();
        try {
            List<String> list = new Gson().fromJson(readText(file), new TypeToken<ArrayList<String>>() {
            }.getType());
            return list == null ? Set.of() : new TreeSet<>(list);
        } catch (RuntimeException | IOException e) {
            AppLog.e(TAG, "Couldn't read the permissions: " + e);
            return Set.of();
        }
    }

    private static Set<String> nativeAbis(FilePathUtil paths, String scId) {
        Set<String> abis = new TreeSet<>();
        File[] folders = new File(paths.getPathNativelibs(scId)).listFiles(File::isDirectory);
        if (folders == null) return abis;
        for (File folder : folders) {
            File[] libs = folder.listFiles((dir, name) -> name.endsWith(".so"));
            if (libs != null && libs.length > 0) abis.add(folder.getName());
        }
        return abis;
    }

    private static boolean hasNightResources(FilePathUtil paths, String scId) {
        File[] folders = new File(paths.getPathResource(scId)).listFiles(File::isDirectory);
        if (folders == null) return false;
        for (File folder : folders) if (folder.getName().toLowerCase(Locale.ROOT).contains("night")) return true;
        return false;
    }

    // ---- screens

    private static List<ViewFacts> views(String scId) {
        List<ViewFacts> result = new ArrayList<>();
        Set<String> screens = new LinkedHashSet<>();
        for (ProjectFileBean file : jC.b(scId).b()) screens.add(file.getXmlName());
        for (ProjectFileBean file : jC.b(scId).c()) screens.add(file.getXmlName());
        for (String xml : screens) {
            List<ViewBean> beans = jC.a(scId).d(xml);
            if (beans == null) continue;
            String screen = xml.endsWith(".xml") ? xml.substring(0, xml.length() - 4) : xml;
            for (ViewBean bean : beans) result.add(toFacts(screen, bean));
        }
        return result;
    }

    static ViewFacts toFacts(String screen, ViewBean bean) {
        LayoutBean layout = bean.layout;
        Kind kind = switch (bean.type) {
            case ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW, ViewBeans.VIEW_TYPE_WIDGET_CIRCLEIMAGEVIEW -> Kind.IMAGE;
            case ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW -> Kind.TEXT;
            case ViewBean.VIEW_TYPE_WIDGET_EDITTEXT -> Kind.TEXT_INPUT;
            case ViewBean.VIEW_TYPE_WIDGET_BUTTON, ViewBeans.VIEW_TYPE_WIDGET_MATERIALBUTTON -> Kind.BUTTON;
            default -> Kind.OTHER;
        };
        // Every widget is "clickable" by default in the editor, so only controls that are meant to be tapped count
        boolean tappable = kind == Kind.BUTTON || bean.type == ViewBean.VIEW_TYPE_WIDGET_CHECKBOX || bean.type == ViewBean.VIEW_TYPE_WIDGET_SWITCH
                || bean.type == ViewBeans.VIEW_TYPE_WIDGET_RADIOBUTTON;
        boolean hasText = kind == Kind.TEXT || kind == Kind.TEXT_INPUT || kind == Kind.BUTTON;
        String text = bean.text == null ? "" : bean.text.text;
        boolean literalTextColor = bean.text != null && isEmpty(bean.text.resTextColor);
        boolean literalBackground = isEmpty(layout.backgroundResColor) && isEmpty(layout.backgroundResource) && (layout.backgroundColor >>> 24) != 0;
        return new ViewFacts(screen, bean.id, kind, layout.width, layout.height, tappable,
                bean.inject != null && bean.inject.contains("contentDescription"),
                hasText && literalTextColor ? bean.text.textColor : null,
                literalBackground ? layout.backgroundColor : null,
                hasText && bean.text != null ? bean.text.textSize : 0,
                layout.marginLeft, layout.marginRight, layout.paddingLeft, layout.paddingRight,
                hasText && text != null && !text.isEmpty() && !text.startsWith("@string/"));
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    // ---- reading files

    private static void collect(File root, File dir, Set<String> extensions, List<SourceFile> into, long[] budget) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File child : children) {
            if (child.isDirectory()) {
                collect(root, child, extensions, into, budget);
            } else {
                String name = child.getName();
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
                    collectIfFile(child, relative(root, child), into, budget);
                }
            }
        }
    }

    private static void collectIfFile(File file, String shownName, List<SourceFile> into, long[] budget) {
        if (!file.isFile() || file.length() > MAX_FILE_BYTES || file.length() > budget[0]) return;
        try {
            into.add(new SourceFile(shownName, readText(file), file.getAbsolutePath()));
            budget[0] -= file.length();
        } catch (IOException e) {
            AppLog.e(TAG, "Couldn't read " + file + ": " + e);
        }
    }

    private static String relative(File root, File file) {
        String path = file.getAbsolutePath();
        String base = root.getAbsolutePath() + File.separator;
        return root.getName() + "/" + (path.startsWith(base) ? path.substring(base.length()) : file.getName());
    }

    private static String readText(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
