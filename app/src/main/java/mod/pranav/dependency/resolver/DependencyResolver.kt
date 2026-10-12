package mod.pranav.dependency.resolver

import android.os.Environment
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.google.gson.Gson
import dev.aldi.sayuti.editor.manage.ArchiveCheck
import dev.aldi.sayuti.editor.manage.EmbeddedLibs
import kotlinx.coroutines.runBlocking
import mod.hey.studios.build.BuildSettings
import mod.hey.studios.util.Helper
import mod.jbk.build.BuiltInLibraries
import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.api.EventReciever
import org.cosmic.ide.dependency.resolver.api.Repository
import org.cosmic.ide.dependency.resolver.eventReciever
import org.cosmic.ide.dependency.resolver.getArtifact
import org.cosmic.ide.dependency.resolver.repositories
import pro.sketchware.utility.FileUtil
import pro.sketchware.utility.ZipSafety
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DependencyResolver(
    private val groupId: String,
    private val artifactId: String,
    private val version: String,
    private val skipDependencies: Boolean,
    private val buildSettings: BuildSettings
) {
    companion object {
        private val DEFAULT_REPOS = """
          |[
          |    {"url": "https://repo.hortonworks.com/content/repositories/releases", "name": "HortanWorks"},
          |    {"url": "https://maven.atlassian.com/content/repositories/atlassian-public", "name": "Atlassian"},
          |    {"url": "https://jcenter.bintray.com", "name": "JCenter"},
          |    {"url": "https://oss.sonatype.org/content/repositories/releases", "name": "Sonatype"},
          |    {"url": "https://repo.spring.io/plugins-release", "name": "Spring Plugins"},
          |    {"url": "https://repo.spring.io/libs-milestone", "name": "Spring Milestone"},
          |    {"url": "https://repo.maven.apache.org/maven2", "name": "Apache Maven"}
          |]
        """.trimMargin()

        private const val DOWNLOAD_ATTEMPTS = 3

        /** What D8 compiles a library against: the lambda stubs and the android.jar. */
        @JvmStatic
        fun compileLibraryJars(buildSettings: BuildSettings): List<Path> = listOf(
            BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath().resolve("core-lambda-stubs.jar"),
            Paths.get(
                buildSettings.getValue(
                    BuildSettings.SETTING_ANDROID_JAR_PATH,
                    BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath
                )
            )
        )

        /**
         * Compiles the JARs an AAR carries in its libs/ folder (see [EmbeddedLibs]); the downloader only
         * compiled classes.jar, so their classes were missing from the app.
         *
         * @return names of the JARs that couldn't be compiled
         */
        @JvmStatic
        fun dexEmbeddedJars(libraryFolder: File, libraryJars: List<Path>): List<String> {
            val failed = mutableListOf<String>()
            for (jar in EmbeddedLibs.jarsToCompile(libraryFolder)) {
                val output = Files.createTempDirectory(libraryFolder.toPath(), "embedded-dex")
                try {
                    D8.run(
                        D8Command.builder().setIntermediate(true).setMode(CompilationMode.RELEASE)
                            .addProgramFiles(jar.toPath()).addLibraryFiles(libraryJars)
                            .setOutput(output, OutputMode.DexIndexed).build()
                    )
                    val dexFiles = output.toFile().listFiles { file -> file.name.endsWith(".dex") }
                        ?.sortedBy { it.name }.orEmpty()
                    dexFiles.forEachIndexed { index, dex ->
                        Files.move(
                            dex.toPath(), EmbeddedLibs.dexFor(libraryFolder, jar, index).toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                    if (dexFiles.isEmpty()) failed.add(jar.name)
                } catch (e: Exception) {
                    failed.add(jar.name)
                } finally {
                    output.toFile().deleteRecursively()
                }
            }
            return failed
        }
    }

    private val downloadPath: String =
        FileUtil.getExternalStorageDir() + "/.sketch_nws/libs/local_libs"

    private val repositoriesJson = Paths.get(
        Environment.getExternalStorageDirectory().absolutePath,
        ".sketch_nws",
        "libs",
        "repositories.json"
    )

    init {
        if (Files.notExists(repositoriesJson)) {
            Files.createDirectories(repositoriesJson.parent)
            repositoriesJson.writeText(DEFAULT_REPOS)
        }
        Gson().fromJson(repositoriesJson.readText(), Helper.TYPE_MAP_LIST).forEach {
            val url: String? = it["url"] as String?
            if (url != null) {
                repositories.add(object : Repository {
                    override fun getName(): String {
                        return it["name"] as String
                    }

                    override fun getURL(): String {
                        return if (url.endsWith("/")) {
                            url.substringBeforeLast("/")
                        } else {
                            url
                        }
                    }
                })
            }
        }
    }

    open class DependencyResolverCallback : EventReciever() {
        override fun artifactFound(artifact: Artifact) {}
        override fun onArtifactNotFound(artifact: Artifact) {}
        override fun onFetchingLatestVersion(artifact: Artifact) {}
        override fun onFetchedLatestVersion(artifact: Artifact, version: String) {}
        override fun onResolving(artifact: Artifact, dependency: Artifact) {}
        override fun onResolutionComplete(artifact: Artifact) {}
        override fun onSkippingResolution(artifact: Artifact) {}
        override fun onVersionNotFound(artifact: Artifact) {}
        override fun onDependenciesNotFound(artifact: Artifact) {}
        override fun onInvalidScope(artifact: Artifact, scope: String) {}
        override fun onInvalidPOM(artifact: Artifact) {}
        override fun onDownloadStart(artifact: Artifact) {}
        override fun onDownloadEnd(artifact: Artifact) {}
        override fun onDownloadError(artifact: Artifact, error: Throwable) {}
        open fun unzipping(artifact: Artifact) {}
        open fun dexing(artifact: Artifact) {}
        open fun onTaskCompleted(artifacts: List<String>) {}
        open fun dexingFailed(artifact: Artifact, e: Exception) {}
        open fun invalidPackaging(artifact: Artifact) {}
    }

    fun resolveDependency(callback: DependencyResolverCallback) = runBlocking {
        eventReciever = callback
        val dependency = getArtifact(groupId, artifactId, version) ?: return@runBlocking

        if (dependency.extension != "jar" && dependency.extension != "aar") {
            callback.invalidPackaging(dependency)
            return@runBlocking
        }

        val libraryJars = listOf(
            BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath()
                .resolve("core-lambda-stubs.jar"), Paths.get(
                buildSettings.getValue(
                    BuildSettings.SETTING_ANDROID_JAR_PATH,
                    BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath
                )
            )
        )
        val dependencyClasspath = mutableListOf<Path>()

        val classpath = buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "")

        classpath.split(":").forEach {
            if (it.isEmpty()) return@forEach
            dependencyClasspath.add(Paths.get(it))
        }

        val rootArchive = File(
            downloadPath + "/${dependency.artifactId}-v${dependency.version}/classes.${dependency.extension}"
        ).apply { parentFile?.mkdirs() }
        val rootProblem = downloadChecked(dependency, rootArchive)
        if (rootProblem != null) {
            callback.onDownloadError(dependency, rootProblem)
            return@runBlocking
        }

        if (dependency.extension == "aar") {
            callback.unzipping(dependency)
            unzip(
                Paths.get(
                    downloadPath,
                    "${dependency.artifactId}-v${dependency.version}",
                    "classes.aar"
                )
            )
            Files.delete(
                Paths.get(
                    downloadPath,
                    "${dependency.artifactId}-v${dependency.version}",
                    "classes.aar"
                )
            )
            dexEmbeddedJars(
                Paths.get(downloadPath, "${dependency.artifactId}-v${dependency.version}").toFile(),
                libraryJars
            )
            val packageName = findPackageName(
                Paths.get(downloadPath, "${dependency.artifactId}-v${dependency.version}")
                    .toAbsolutePath().toString(),
                dependency.groupId
            )
            Paths.get(downloadPath, "${dependency.artifactId}-v${dependency.version}", "config")
                .writeText(packageName)
        }

        val jar = Paths.get(
            downloadPath,
            "${dependency.artifactId}-v${dependency.version}",
            "classes.jar"
        )

        callback.dexing(dependency)
        try {
            compileJar(jar, dependencyClasspath, libraryJars)
            callback.onResolutionComplete(dependency)
        } catch (e: Exception) {
            callback.dexingFailed(dependency, e)
        }

        if (skipDependencies) {
            callback.onSkippingResolution(dependency)
            callback.onTaskCompleted(listOf("${dependency.artifactId}-v${dependency.version}"))
            return@runBlocking
        }
        dependency.resolveDependencyTree()

        dependency.getAllDependencies().forEach { dep ->
            println("Resolving dependency: ${dep.artifactId} v${dep.version}")
            if (dep.extension != "jar" && dep.extension != "aar") {
                callback.invalidPackaging(dep)
                return@forEach
            }

            if (dep.version.isEmpty()) {
                callback.onVersionNotFound(dep)
                return@forEach
            }

            val path = Paths.get(
                downloadPath,
                "${dep.artifactId}-v${dep.version}",
                "classes.${dep.extension}"
            )

            Files.createDirectories(path.parent)

            val problem = downloadChecked(dep, File(path.toString()))
            if (problem != null) {
                // Stop instead of skipping it: a library with a missing dependency crashes the app later.
                callback.onDownloadError(dep, problem)
                return@runBlocking
            }

            if (dep.extension == "aar") {
                callback.unzipping(dep)
                unzip(path)
                Files.delete(path)
                dexEmbeddedJars(path.parent.toFile(), libraryJars)
                val packageName =
                    findPackageName(path.parent.toAbsolutePath().toString(), dep.groupId)
                path.parent.resolve("config").writeText(packageName)
            }

            val jar = if (dep.extension == "jar") path else Paths.get(
                downloadPath, "${dep.artifactId}-v${dep.version}", "classes.jar"
            )
            if (Files.notExists(jar)) {
                callback.onDependenciesNotFound(dep)
                return@forEach
            }

            dependencyClasspath.add(jar)
        }

        dependency.getAllDependencies().forEach { dep ->
            val jar = Paths.get(downloadPath, "${dep.artifactId}-v${dep.version}", "classes.jar")

            callback.dexing(dep)
            try {
                compileJar(
                    jar, dependencyClasspath.toMutableList().apply { remove(jar) }, libraryJars
                )
                callback.onResolutionComplete(dep)
            } catch (e: Exception) {
                callback.dexingFailed(dep, e)
                return@forEach
            }
        }

        callback.onTaskCompleted(
            dependency.getAllDependencies().map { "${it.artifactId}-v${it.version}" })
    }

    /**
     * Downloads [artifact] to [file] and checks that it is a complete archive, trying again if it isn't. A
     * rate-limited or interrupted download leaves an error page or a cut-off file, which used to crash the app while
     * unzipping ("zip END header not found").
     *
     * @return null when the file is good, otherwise why it isn't
     */
    private fun downloadChecked(artifact: Artifact, file: File): Throwable? {
        var failure: Throwable? = null
        for (attempt in 1..DOWNLOAD_ATTEMPTS) {
            try {
                file.delete()
                artifact.downloadTo(file)
                if (ArchiveCheck.isValidZip(file)) return null
                failure = IOException(
                    "The downloaded file for $artifact is not a complete archive " +
                        "(the connection was cut off, or the server sent an error page such as a download limit)."
                )
            } catch (e: Exception) {
                failure = e
            }
            file.delete()
            if (attempt < DOWNLOAD_ATTEMPTS) Thread.sleep(2000L * attempt)
        }
        return failure
    }

    private fun findPackageName(path: String, defaultValue: String): String {
        val manifest =
            File(path).walk().filter { it.isFile && it.name == "AndroidManifest.xml" }.firstOrNull()
        val content = manifest?.readText() ?: return defaultValue
        val p = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL)
        val m = p.matcher(content)
        if (m.find()) {
            return m.group(1)!!
        }

        return defaultValue
    }

    private fun unzip(path: Path) {
        val zipFile = ZipFile(path.toFile())
        zipFile.use { zip ->
            zip.entries().asSequence().forEach { entry ->
                // Downloaded archives are untrusted too: reject entries that escape the target folder (Zip Slip).
                val entryDestination = ZipSafety.resolveEntry(path.parent.toFile(), entry.name).toPath()
                if (entry.isDirectory) {
                    Files.createDirectories(entryDestination)
                } else {
                    Files.createDirectories(entryDestination.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(entryDestination).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun compileJar(jarFile: Path, jars: List<Path>, libraryJars: List<Path>) {
        Files.createDirectories(jarFile.parent)
        D8.run(
            D8Command.builder().setIntermediate(true).setMode(CompilationMode.RELEASE)
                .addProgramFiles(jarFile).addLibraryFiles(libraryJars).addClasspathFiles(jars)
                .setOutput(jarFile.parent, OutputMode.DexIndexed).build()
        )
    }
}
