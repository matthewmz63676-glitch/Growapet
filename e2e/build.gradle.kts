import groovy.json.JsonSlurper
import org.gradle.internal.os.OperatingSystem
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    base
    id("io.github.drownek.plugwright") version "2.0.3"
}

group = "me.growapet"
version = "1.0.0-e2e"

val repositoryRoot = rootProject.projectDir.parentFile
val lockFile = rootProject.file("dependencies.lock.json")
val lockDocument = JsonSlurper().parse(lockFile) as Map<*, *>
val dependencyEntries = (lockDocument["dependencies"] as List<*>)
    .map { it as Map<*, *> }
    .map { entry ->
        E2EDependency(
            id = entry["id"] as String,
            version = entry["version"] as String,
            filename = entry["filename"] as String,
            url = entry["url"] as String,
            sha256 = entry["sha256"] as String
        )
    }

data class E2EDependency(
    val id: String,
    val version: String,
    val filename: String,
    val url: String,
    val sha256: String
)

val lockedPluginDirectory = layout.buildDirectory.dir("locked-plugins")
val stagedGrowAPetJar = layout.buildDirectory.file("staged/GrowAPet-1.0.0.jar")
val repositoryGrowAPetJar = repositoryRoot.resolve("target/growapet-1.0.0.jar")
val preserveState = providers.gradleProperty("preserveState")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

fun sha256(file: java.io.File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString("") { byte -> "%02x".format(byte) }

fun downloadVerified(dependency: E2EDependency, destination: java.io.File) {
    destination.parentFile.mkdirs()
    var lastFailure: Throwable? = null
    repeat(3) { attempt ->
        try {
            val connection = URI(dependency.url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("User-Agent", "GrowAPet-E2E/${project.version}")
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode} for ${dependency.url}")
            }
            connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            connection.disconnect()
            val actual = sha256(destination)
            check(actual.equals(dependency.sha256, ignoreCase = true)) {
                "SHA-256 mismatch for ${dependency.id} ${dependency.version}: expected ${dependency.sha256}, got $actual"
            }
            return
        } catch (failure: Throwable) {
            lastFailure = failure
            if (attempt < 2) destination.delete()
        }
    }
    throw IllegalStateException("Could not download locked dependency ${dependency.id} ${dependency.version}", lastFailure)
}

val verifyE2EDependencyLock by tasks.registering {
    inputs.file(lockFile)
    outputs.dir(lockedPluginDirectory)
    doLast {
        check(lockDocument["schema"] == 1) { "Unsupported E2E dependency lock schema" }
        dependencyEntries.forEach { dependency ->
            check(dependency.url.startsWith("https://")) { "E2E dependency URLs must use HTTPS: ${dependency.id}" }
            check(dependency.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256 for ${dependency.id}" }
            val destination = lockedPluginDirectory.get().asFile.resolve(dependency.filename)
            if (!destination.isFile || !sha256(destination).equals(dependency.sha256, ignoreCase = true)) {
                downloadVerified(dependency, destination)
            }
            logger.lifecycle("Verified ${dependency.id} ${dependency.version} (${dependency.sha256})")
        }
    }
}

val buildGrowAPet by tasks.registering(Exec::class) {
    workingDir(repositoryRoot)
    if (OperatingSystem.current().isWindows) {
        commandLine("cmd", "/c", "mvnw.cmd", "-DskipTests", "clean", "package")
    } else {
        commandLine("./mvnw", "-DskipTests", "clean", "package")
    }
    inputs.files(repositoryRoot.resolve("pom.xml"), repositoryRoot.resolve("src/main"))
    outputs.file(repositoryGrowAPetJar)
}

val stageGrowAPet by tasks.registering(Copy::class) {
    dependsOn(buildGrowAPet)
    from(repositoryGrowAPetJar)
    into(stagedGrowAPetJar.get().asFile.parentFile)
    rename { stagedGrowAPetJar.get().asFile.name }
    outputs.file(stagedGrowAPetJar)
}

plugwright {
    minecraftVersion.set("1.21.11")
    runDir.set(file("run"))
    testsDir.set(file("src/test/e2e"))
    acceptEula.set(true)
    useExternalPluginsOnly.set(true)
    downloadNode.set(System.getenv("CI") != "true")
    nodeVersion.set("22.14.0")
    jvmArgs.set(listOf("-Xms1G", "-Xmx2G"))
    cleanExcludePatterns.set(
        if (preserveState.get()) {
            listOf("server.jar", "cache", "libraries", "plugins", "world", "world_nether", "world_the_end", "growapet_plots_e2e")
        } else {
            listOf("server.jar", "cache", "libraries")
        }
    )
    writeFiles {
        file("plugins/GrowAPet-1.0.0.jar", stagedGrowAPetJar.get().asFile)
        dependencyEntries.forEach { dependency ->
            file("plugins/${dependency.filename}", lockedPluginDirectory.get().asFile.resolve(dependency.filename))
        }
        file("server.properties", rootProject.file("fixtures/server.properties"))
        file("plugins/GrowAPet/config.yml", rootProject.file("fixtures/growapet/config.yml"))
        file("plugins/GrowAPet/zones.yml", rootProject.file("fixtures/growapet/zones.yml"))
        file("plugins/GrowAPet/tutorial.yml", rootProject.file("fixtures/growapet/tutorial.yml"))
        file("plugins/WorldGuard/worlds/world/regions.yml", rootProject.file("fixtures/world/regions.yml"))
        file("eula.txt", rootProject.file("fixtures/eula.txt"))
    }
}

tasks.named("plugwrightTest") {
    dependsOn(stageGrowAPet, verifyE2EDependencyLock)
    finalizedBy("scanE2ELog")
}

val scanE2ELog by tasks.registering {
    mustRunAfter("plugwrightTest")
    doLast {
        val latestLog = rootProject.file("run/logs/latest.log")
        if (!latestLog.isFile) {
            throw GradleException("Plugwright completed without run/logs/latest.log")
        }
        val lines = latestLog.readLines()
        val normalShutdownIndex = lines.indexOfLast { line ->
            line.lowercase().contains("[server thread/info]: stopping server")
        }
        val unexpected = lines.withIndex().filter { indexed ->
            val line = indexed.value
            val lower = line.lowercase()
            val expectedEulaNotice = lower.contains("spigot command line eula agreement flag") ||
                lower.contains("by using this setting you are indicating your agreement to mojang's eula") ||
                lower.contains("if you do not agree to the above eula please stop your server")
            (Regex("\\b(error|severe)\\b").containsMatchIn(lower) ||
                "exception" in lower || "asynccatcher" in lower ||
                "wrong thread" in lower || "sqliteexception" in lower ||
                "could not pass event" in lower || "disabling growapet" in lower) &&
                !lower.contains("placeholderapi expansion") &&
                !expectedEulaNotice &&
                !(normalShutdownIndex >= 0 && indexed.index > normalShutdownIndex && lower.contains("disabling growapet"))
        }.map { it.value }
        if (unexpected.isNotEmpty()) {
            throw GradleException("Unexpected runtime log diagnostics:\n${unexpected.take(40).joinToString("\n")}")
        }
        logger.lifecycle("E2E log scan passed: no unexpected errors, thread violations, or SQLite failures")
    }
}

tasks.register("e2eSmoke") {
    dependsOn("plugwrightTest")
}
