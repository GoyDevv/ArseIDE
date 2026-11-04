package com.rk.projects

import com.rk.utils.application
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git

/**
 * Writes the real files for a [ProjectConfig] onto disk.
 */
object ProjectScaffolder {

    sealed interface Result {
        data class Success(val projectRoot: File, val warning: String? = null) : Result

        data class Failure(val message: String, val cause: Throwable? = null) : Result
    }

    suspend fun scaffold(config: ProjectConfig): Result =
        withContext(Dispatchers.IO) {
            runCatching {
                    val root = File(config.parentDir, config.name)
                    if (root.exists()) {
                        return@withContext Result.Failure("A project named \"${config.name}\" already exists here.")
                    }
                    if (!root.mkdirs()) {
                        return@withContext Result.Failure(
                            "Could not create project directory. Check storage permissions for ${config.parentDir.absolutePath}."
                        )
                    }

                    val warning: String? =
                        when (config.template) {
                            ProjectTemplate.NONE -> {
                                scaffoldNone(root, config)
                                null
                            }
                            ProjectTemplate.PYTHON3 -> {
                                scaffoldPython(root, config, python3 = true)
                                null
                            }
                            ProjectTemplate.PYTHON -> {
                                scaffoldPython(root, config, python3 = false)
                                null
                            }
                            ProjectTemplate.NODEJS -> {
                                scaffoldNode(root, config)
                                null
                            }
                            ProjectTemplate.WEB -> {
                                scaffoldWeb(root, config)
                                null
                            }
                            ProjectTemplate.MINECRAFT_MOD -> scaffoldMinecraft(root, config)
                            ProjectTemplate.ANDROID_COMPOSE -> {
                                scaffoldAndroidCompose(root, config)
                                null
                            }
                        }

                    if (config.initGit) initGitRepository(root, config)

                    Result.Success(root, warning)
                }
                .getOrElse { Result.Failure(it.message ?: "Unknown error while creating project", it) }
        }

    // ---- helpers ----------------------------------------------------------------

    private fun File.write(relativePath: String, content: String): File {
        val target = File(this, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content.trimStart('\n'))
        return target
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "\\'")

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun safeJavaClassName(raw: String, fallback: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9]"), "").ifBlank { fallback }
        val prefixed = if (cleaned.first().isDigit()) "App$cleaned" else cleaned
        return prefixed.replaceFirstChar { it.uppercase() }
    }

    private fun gradleJavaVersionEnum(jdk: String): String =
        if (jdk == "8" || jdk == "1.8") "VERSION_1_8" else "VERSION_$jdk"

    /**
     * Initialises a git repository and creates an initial commit.
     */
    private fun initGitRepository(root: File, config: ProjectConfig) {
        runCatching {
            Git.init().setDirectory(root).call().use { git ->
                git.add().addFilepattern(".").call()
                val author = config.author.ifBlank { "Xed" }
                git.commit()
                    .setAuthor(author, "noreply@xed-editor")
                    .setCommitter(author, "noreply@xed-editor")
                    .setMessage("Initial commit")
                    .call()
            }
        }
    }

    // ---- None -------------------------------------------------------------------

    private fun scaffoldNone(root: File, config: ProjectConfig) {
        root.write("README.md", "# ${config.name}\n\nCreated with XED-PRO.\n")
        root.write(".gitignore", ".DS_Store\n*.log\n/build/\n/out/\n")
    }

    // ---- Python -----------------------------------------------------------------

    private fun scaffoldPython(root: File, config: ProjectConfig, python3: Boolean) {
        val shebang = if (python3) "#!/usr/bin/env python3" else "#!/usr/bin/env python"
        val safeName = escapeJson(config.name)
        val mainFile =
            root.write(
                "main.py",
                """
                $shebang
                # ${config.name} - entry point


                def main() -> None:
                    print("Hello from $safeName!")


                if __name__ == "__main__":
                    main()
                """
                    .trimIndent() + "\n",
            )
        runCatching { mainFile.setExecutable(true, false) }
        root.write("requirements.txt", "# Add your pip dependencies here (one per line)\n")
        root.write(
            ".gitignore",
            """
            __pycache__/
            *.py[cod]
            .venv/
            venv/
            .env
            """
                .trimIndent() + "\n",
        )
        root.write(
            "README.md",
            "# ${config.name}\n\nRun with:\n\n```bash\n${if (python3) "python3" else "python"} main.py\n```\n",
        )
    }

    // ---- Node.js ----------------------------------------------------------------

    private fun scaffoldNode(root: File, config: ProjectConfig) {
        val pkg = config.name.lowercase().replace(Regex("[^a-z0-9-_]"), "-").trim('-').ifBlank { "app" }
        val safeDesc = escapeJson(config.name)
        val safeAuthor = escapeJson(config.author)
        root.write(
            "package.json",
            """
            {
              "name": "$pkg",
              "version": "1.0.0",
              "description": "$safeDesc",
              "main": "index.js",
              "type": "commonjs",
              "scripts": {
                "start": "node index.js",
                "test": "echo \"No tests configured yet\" && exit 0"
              },
              "keywords": [],
              "author": "$safeAuthor",
              "license": "MIT"
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "index.js",
            """
            "use strict";

            function main() {
              console.log("Hello from $safeDesc!");
            }

            main();
            """
                .trimIndent() + "\n",
        )
        root.write(
            ".gitignore",
            """
            node_modules/
            npm-debug.log*
            .env
            dist/
            """
                .trimIndent() + "\n",
        )
        root.write(
            "README.md",
            "# ${config.name}\n\n```bash\nnpm install\nnpm start\n```\n",
        )
    }

    // ---- Static Web -------------------------------------------------------------

    private fun scaffoldWeb(root: File, config: ProjectConfig) {
        val safeTitle = escapeHtml(config.name)
        val safeJsTitle = escapeJson(config.name)
        root.write(
            "index.html",
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>$safeTitle</title>
                <link rel="stylesheet" href="style.css" />
            </head>
            <body>
                <main class="glass-card">
                    <span class="badge">XED-PRO Web Starter</span>
                    <h1>$safeTitle</h1>
                    <p id="message">Welcome to $safeTitle — edit <code>index.html</code>, <code>style.css</code>, or <code>script.js</code> and hit Run to preview live.</p>
                    <div class="actions">
                        <button id="counter-btn" type="button">Clicks: <span id="count">0</span></button>
                    </div>
                </main>
                <script src="script.js"></script>
            </body>
            </html>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "style.css",
            """
            :root {
                color-scheme: light dark;
                --bg-start: #0b0f19;
                --bg-end: #162038;
                --card-bg: rgba(255, 255, 255, 0.08);
                --card-border: rgba(255, 255, 255, 0.18);
                --accent: #66e3f4;
                --text: #eef2ff;
                --muted: #94a3b8;
            }
            * { box-sizing: border-box; }
            body {
                font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                margin: 0;
                min-height: 100vh;
                display: grid;
                place-items: center;
                padding: 1.5rem;
                background: radial-gradient(circle at top left, #1e293b, var(--bg-start) 65%);
                color: var(--text);
            }
            .glass-card {
                max-width: 460px;
                width: 100%;
                text-align: center;
                padding: 2.25rem 1.75rem;
                border-radius: 24px;
                background: var(--card-bg);
                backdrop-filter: blur(18px) saturate(160%);
                -webkit-backdrop-filter: blur(18px) saturate(160%);
                border: 1px solid var(--card-border);
                box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35);
            }
            .badge {
                display: inline-block;
                padding: 0.3rem 0.85rem;
                border-radius: 999px;
                font-size: 0.75rem;
                font-weight: 600;
                letter-spacing: 0.05em;
                color: var(--accent);
                background: rgba(102, 227, 244, 0.12);
                border: 1px solid rgba(102, 227, 244, 0.28);
            }
            h1 { margin: 1rem 0 0.6rem; font-size: 2rem; }
            p { color: var(--muted); line-height: 1.6; margin-bottom: 1.5rem; }
            button {
                appearance: none;
                border: 1px solid rgba(102, 227, 244, 0.4);
                background: rgba(102, 227, 244, 0.16);
                color: var(--text);
                font-weight: 600;
                font-size: 0.95rem;
                padding: 0.7rem 1.4rem;
                border-radius: 14px;
                cursor: pointer;
                transition: transform 0.15s ease, background 0.2s ease;
            }
            button:active { transform: scale(0.96); }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "script.js",
            """
            document.addEventListener("DOMContentLoaded", () => {
              let count = 0;
              const btn = document.getElementById("counter-btn");
              const countEl = document.getElementById("count");
              if (btn && countEl) {
                btn.addEventListener("click", () => {
                  count += 1;
                  countEl.textContent = String(count);
                });
              }
              console.log("$safeJsTitle is running!");
            });
            """
                .trimIndent() + "\n",
        )
        root.write(".gitignore", "node_modules/\ndist/\n.env\n")
        root.write("README.md", "# ${config.name}\n\nOpen `index.html` and press the Run button to launch the live preview.\n")
    }

    // ---- Minecraft --------------------------------------------------------------
    private fun scaffoldMinecraft(root: File, config: ProjectConfig): String? =
        when (config.modLoader) {
            ModLoader.FORGE -> scaffoldForge(root, config)
            else -> scaffoldFabric(root, config)
        }

    private fun scaffoldFabric(root: File, config: ProjectConfig): String? {
        val modId = config.resolvedModId()
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val mainClass = safeJavaClassName(config.name, "ExampleMod")
        val mc = config.minecraftVersion.ifBlank { "1.21.4" }
        val jdk = config.jdkVersion.ifBlank { "21" }
        val javaVerEnum = gradleJavaVersionEnum(jdk)
        val pins = ModVersions.fabricPins(mc)
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { config.name })
        val safeAuthor = escapeJson(config.author.ifBlank { "Author" })

        val mappingsLine =
            if (pins.hasYarn) {
                "mappings \"net.fabricmc:yarn:\${project.yarn_mappings}:v2\""
            } else {
                "mappings loom.officialMojangMappings()"
            }

        root.write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx2G
            org.gradle.parallel=true

            # Fabric Properties (resolved for Minecraft $mc)
            minecraft_version=$mc
            yarn_mappings=${pins.yarn}
            loader_version=${pins.loader}

            # Mod Properties
            mod_version=${config.modVersion}
            maven_group=$pkg
            archives_base_name=$modId

            # Fabric API
            fabric_version=${pins.fabricApi}
            """
                .trimIndent() + "\n",
        )

        root.write(
            "settings.gradle",
            """
            pluginManagement {
                repositories {
                    maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            rootProject.name = '$modId'
            """
                .trimIndent() + "\n",
        )

        root.write(
            "build.gradle",
            """
            plugins {
                id 'fabric-loom' version '1.9-SNAPSHOT'
                id 'maven-publish'
            }

            version = project.mod_version
            group = project.maven_group

            base { archivesName = project.archives_base_name }

            repositories {
                mavenCentral()
            }

            dependencies {
                minecraft "com.mojang:minecraft:${'$'}{project.minecraft_version}"
                $mappingsLine
                modImplementation "net.fabricmc:fabric-loader:${'$'}{project.loader_version}"
                modImplementation "net.fabricmc.fabric-api:fabric-api:${'$'}{project.fabric_version}"
            }

            processResources {
                inputs.property "version", project.version
                filteringCharset "UTF-8"
                filesMatching("fabric.mod.json") {
                    expand "version": project.version
                }
            }

            java {
                sourceCompatibility = JavaVersion.$javaVerEnum
                targetCompatibility = JavaVersion.$javaVerEnum
                withSourcesJar()
            }

            tasks.withType(JavaCompile).configureEach {
                it.options.encoding = "UTF-8"
                it.options.release = $jdk
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/fabric.mod.json",
            """
            {
              "schemaVersion": 1,
              "id": "$modId",
              "version": "${'$'}{version}",
              "name": "$safeName",
              "description": "$safeDesc",
              "authors": ["$safeAuthor"],
              "license": "MIT",
              "environment": "*",
              "entrypoints": {
                "main": ["$pkg.$mainClass"]
              },
              "mixins": ["$modId.mixins.json"],
              "depends": {
                "fabricloader": ">=${pins.loader}",
                "minecraft": "~$mc",
                "java": ">=$jdk",
                "fabric-api": "*"
              }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/$modId.mixins.json",
            """
            {
              "required": true,
              "package": "$pkg.mixin",
              "compatibilityLevel": "JAVA_$jdk",
              "mixins": [],
              "client": [],
              "injectors": { "defaultRequire": 1 }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/java/$pkgPath/$mainClass.java",
            """
            package $pkg;

            import net.fabricmc.api.ModInitializer;
            import org.slf4j.Logger;
            import org.slf4j.LoggerFactory;

            public class $mainClass implements ModInitializer {
                public static final String MOD_ID = "$modId";
                public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

                @Override
                public void onInitialize() {
                    LOGGER.info("$safeName initialized!");
                }
            }
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "8.10.2")
        root.write(".gitignore", ".gradle/\nbuild/\nout/\n.idea/\n*.iml\n")
        root.write("README.md", minecraftReadme(config, "Fabric", mc, jdk))

        return if (!pins.online) {
            "Used offline Fabric defaults for Minecraft $mc — check gradle.properties if you customize dependencies."
        } else {
            null
        }
    }

    private fun scaffoldForge(root: File, config: ProjectConfig): String? {
        val modId = config.resolvedModId()
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val mainClass = safeJavaClassName(config.name, "ExampleMod")
        val requestedMc = config.minecraftVersion.ifBlank { "1.20.1" }
        val forgePin = ModVersions.resolveForgePin(requestedMc)
        val mc = forgePin.mcVersion
        val forge = forgePin.forgeVersion
        val forgeMajor = forge.substringBefore(".").ifBlank { "47" }
        val forgeMajorInt = forgeMajor.toIntOrNull() ?: 47
        val jdk =
            if (!forgePin.exactMatch) "17"
            else config.jdkVersion.ifBlank { if (forgeMajorInt >= 50) "21" else "17" }
        val packFormat = packFormatForMc(mc)
        val safeName = escapeJson(config.name)
        val safeDesc = escapeJson(config.modDescription.ifBlank { config.name })
        val safeAuthor = escapeJson(config.author.ifBlank { "Author" })

        val modConstructor =
            if (forgeMajorInt >= 50) {
                """
                public $mainClass(FMLJavaModLoadingContext context) {
                    IEventBus modEventBus = context.getModEventBus();
                    LOGGER.info("$safeName loading");
                }
                """
                    .trimIndent()
            } else {
                """
                public $mainClass() {
                    IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
                    LOGGER.info("$safeName loading");
                }
                """
                    .trimIndent()
            }

        root.write(
            "gradle.properties",
            """
            org.gradle.jvmargs=-Xmx2G
            org.gradle.daemon=false

            minecraft_version=$mc
            # Resolved Forge build for Minecraft $mc
            forge_version=$forge
            mod_id=$modId
            mod_version=${config.modVersion}
            maven_group=$pkg
            """
                .trimIndent() + "\n",
        )

        root.write(
            "settings.gradle",
            """
            pluginManagement {
                repositories {
                    maven { url = 'https://maven.minecraftforge.net/' }
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            rootProject.name = '$modId'
            """
                .trimIndent() + "\n",
        )

        root.write(
            "build.gradle",
            """
            plugins {
                id 'net.minecraftforge.gradle' version '[6.0.24,6.2)'
                id 'java'
            }

            version = project.mod_version
            group = project.maven_group

            base {
                archivesName = project.mod_id
            }

            java {
                toolchain.languageVersion = JavaLanguageVersion.of($jdk)
            }

            minecraft {
                mappings channel: 'official', version: project.minecraft_version
                copyIdeResources = true
            }

            repositories {
                mavenCentral()
                maven { url = 'https://maven.minecraftforge.net/' }
            }

            dependencies {
                minecraft "net.minecraftforge:forge:${'$'}{project.minecraft_version}-${'$'}{project.forge_version}"
            }

            tasks.withType(JavaCompile).configureEach {
                options.encoding = 'UTF-8'
            }

            jar {
                manifest {
                    attributes([
                        "Specification-Title"   : project.mod_id,
                        "Implementation-Title"  : project.mod_id,
                        "Implementation-Version": project.version
                    ])
                }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/META-INF/mods.toml",
            """
            modLoader="javafml"
            loaderVersion="[$forgeMajor,)"
            license="MIT"

            [[mods]]
            modId="$modId"
            version="${config.modVersion}"
            displayName="$safeName"
            authors="$safeAuthor"
            description='''
            $safeDesc
            '''

            [[dependencies.$modId]]
                modId="forge"
                mandatory=true
                versionRange="[$forgeMajor,)"
                ordering="NONE"
                side="BOTH"

            [[dependencies.$modId]]
                modId="minecraft"
                mandatory=true
                versionRange="[$mc]"
                ordering="NONE"
                side="BOTH"
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/resources/pack.mcmeta",
            """
            {
              "pack": {
                "description": "$safeName resources",
                "pack_format": $packFormat
              }
            }
            """
                .trimIndent() + "\n",
        )

        root.write(
            "src/main/java/$pkgPath/$mainClass.java",
            """
            package $pkg;

            import net.minecraftforge.fml.common.Mod;
            import net.minecraftforge.eventbus.api.IEventBus;
            import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
            import org.slf4j.Logger;
            import com.mojang.logging.LogUtils;

            @Mod($mainClass.MOD_ID)
            public class $mainClass {
                public static final String MOD_ID = "$modId";
                private static final Logger LOGGER = LogUtils.getLogger();

                $modConstructor
            }
            """
                .trimIndent() + "\n",
        )

        // Gradle 8.8+ supports both Java 17 and Java 21 with ForgeGradle 6.0.24+.
        writeGradleWrapper(root, "8.8")
        root.write(".gitignore", ".gradle/\nbuild/\nout/\n.idea/\n*.iml\n")
        root.write("README.md", minecraftReadme(config, "Forge", mc, jdk))

        return if (!forgePin.exactMatch) {
            "Forge does not have a published build for Minecraft $requestedMc yet — configured for Minecraft $mc (Forge $forge) so the project builds cleanly."
        } else {
            null
        }
    }

    private fun minecraftReadme(config: ProjectConfig, loader: String, mc: String, jdk: String): String =
        """
        # ${config.name}

        $loader mod for Minecraft $mc (Java $jdk).

        > Built with Gradle. Run from an exec-capable filesystem (the terminal sandbox),
        > not from /sdcard (Documents/Downloads), because shared storage is mounted noexec.

        ```bash
        ./gradlew build
        ```
        """
            .trimIndent() + "\n"

    private fun packFormatForMc(mc: String): Int =
        when {
            mc.startsWith("1.21.4") -> 46
            mc.startsWith("1.21.2") || mc.startsWith("1.21.3") -> 42
            mc.startsWith("1.21") -> 34
            mc.startsWith("1.20.5") || mc.startsWith("1.20.6") -> 32
            mc.startsWith("1.20.3") || mc.startsWith("1.20.4") -> 22
            mc.startsWith("1.20.2") -> 18
            mc.startsWith("1.20") -> 15
            mc.startsWith("1.19") -> 9
            else -> 15
        }

    // ---- Android Jetpack Compose (complete Android Studio "Empty Activity" project) -----

    private fun scaffoldAndroidCompose(root: File, config: ProjectConfig) {
        val pkg = config.resolvedPackageName()
        val pkgPath = pkg.replace('.', '/')
        val jdk = config.jdkVersion.takeIf { it in listOf("17", "21") } ?: "21"
        val sdk = config.resolvedCompileSdk()
        val minSdk = config.resolvedMinSdk()
        val appNoSpace = safeJavaClassName(config.name, "App")
        val styleName = "Theme.$appNoSpace"
        val safeRootName = escapeJson(config.name)
        val safeXmlAppName = escapeXml(config.name)

        // settings.gradle.kts
        root.write(
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    google {
                        content {
                            includeGroupByRegex("com\\.android.*")
                            includeGroupByRegex("com\\.google.*")
                            includeGroupByRegex("androidx.*")
                        }
                    }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "$safeRootName"
            include(":app")
            """
                .trimIndent() + "\n",
        )

        // Root build.gradle.kts — matches Xed-EditorPRO's own AGP 9.2.1 setup (AGP 9 has built-in Kotlin
        // support and must NOT apply org.jetbrains.kotlin.android separately).
        root.write(
            "build.gradle.kts",
            """
            // Top-level build file where you can add configuration options common to all sub-projects/modules.
            plugins {
                alias(libs.plugins.android.application) apply false
                alias(libs.plugins.kotlin.compose) apply false
            }
            """
                .trimIndent() + "\n",
        )

        // Version catalog
        root.write(
            "gradle/libs.versions.toml",
            """
            [versions]
            agp = "9.2.1"
            kotlin = "2.3.20"
            coreKtx = "1.17.0"
            junit = "4.13.2"
            junitVersion = "1.3.0"
            espressoCore = "3.7.0"
            lifecycleRuntimeKtx = "2.10.0"
            activityCompose = "1.12.0"
            composeBom = "2025.11.01"

            [libraries]
            androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
            junit = { group = "junit", name = "junit", version.ref = "junit" }
            androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
            androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
            androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
            androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
            androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
            androidx-ui = { group = "androidx.compose.ui", name = "ui" }
            androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
            androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
            androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
            androidx-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
            androidx-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
            androidx-material3 = { group = "androidx.compose.material3", name = "material3" }

            [plugins]
            android-application = { id = "com.android.application", version.ref = "agp" }
            kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
            """
                .trimIndent() + "\n",
        )

        // gradle.properties
        root.write(
            "gradle.properties",
            """
            # Project-wide Gradle settings.
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            org.gradle.parallel=true
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
            """
                .trimIndent() + "\n",
        )

        // app/build.gradle.kts
        root.write(
            "app/build.gradle.kts",
            """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.compose)
            }

            android {
                namespace = "$pkg"
                compileSdk = $sdk

                defaultConfig {
                    applicationId = "$pkg"
                    minSdk = $minSdk
                    targetSdk = $sdk
                    versionCode = 1
                    versionName = "1.0"

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables { useSupportLibrary = true }
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_$jdk
                    targetCompatibility = JavaVersion.VERSION_$jdk
                }
                buildFeatures { compose = true }
            }

            kotlin {
                jvmToolchain($jdk)
            }

            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(platform(libs.androidx.compose.bom))
                implementation(libs.androidx.ui)
                implementation(libs.androidx.ui.graphics)
                implementation(libs.androidx.ui.tooling.preview)
                implementation(libs.androidx.material3)
                testImplementation(libs.junit)
                androidTestImplementation(libs.androidx.junit)
                androidTestImplementation(libs.androidx.espresso.core)
                androidTestImplementation(platform(libs.androidx.compose.bom))
                androidTestImplementation(libs.androidx.ui.test.junit4)
                debugImplementation(libs.androidx.ui.tooling)
                debugImplementation(libs.androidx.ui.test.manifest)
            }
            """
                .trimIndent() + "\n",
        )

        root.write("app/proguard-rules.pro", "# Add project specific ProGuard rules here.\n")
        root.write("app/.gitignore", "/build\n")

        // AndroidManifest.xml
        root.write(
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools">

                <application
                    android:allowBackup="true"
                    android:dataExtractionRules="@xml/data_extraction_rules"
                    android:fullBackupContent="@xml/backup_rules"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:roundIcon="@mipmap/ic_launcher_round"
                    android:supportsRtl="true"
                    android:theme="@style/$styleName"
                    tools:targetApi="31">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:label="@string/app_name"
                        android:theme="@style/$styleName">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """
                .trimIndent() + "\n",
        )

        // Complete ui.theme package (Color.kt, Type.kt, Theme.kt)
        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Color.kt",
            """
            package $pkg.ui.theme

            import androidx.compose.ui.graphics.Color

            val PrimaryDark = Color(0xFFADC6FF)
            val SecondaryDark = Color(0xFFBCC6E2)
            val TertiaryDark = Color(0xFFDCB9E8)

            val PrimaryLight = Color(0xFF3B5BA9)
            val SecondaryLight = Color(0xFF535E78)
            val TertiaryLight = Color(0xFF6F527A)
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Type.kt",
            """
            package $pkg.ui.theme

            import androidx.compose.material3.Typography
            import androidx.compose.ui.text.TextStyle
            import androidx.compose.ui.text.font.FontFamily
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.sp

            val Typography = Typography(
                bodyLarge = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.5.sp,
                ),
            )
            """
                .trimIndent() + "\n",
        )

        root.write(
            "app/src/main/java/$pkgPath/ui/theme/Theme.kt",
            """
            package $pkg.ui.theme

            import android.os.Build
            import androidx.compose.foundation.isSystemInDarkTheme
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.material3.dynamicDarkColorScheme
            import androidx.compose.material3.dynamicLightColorScheme
            import androidx.compose.material3.lightColorScheme
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.platform.LocalContext

            private val DarkColorScheme = darkColorScheme(
                primary = PrimaryDark,
                secondary = SecondaryDark,
                tertiary = TertiaryDark,
            )

            private val LightColorScheme = lightColorScheme(
                primary = PrimaryLight,
                secondary = SecondaryLight,
                tertiary = TertiaryLight,
            )

            @Composable
            fun ${appNoSpace}Theme(
                darkTheme: Boolean = isSystemInDarkTheme(),
                dynamicColor: Boolean = true,
                content: @Composable () -> Unit,
            ) {
                val colorScheme = when {
                    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        val context = LocalContext.current
                        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    darkTheme -> DarkColorScheme
                    else -> LightColorScheme
                }

                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = Typography,
                    content = content,
                )
            }
            """
                .trimIndent() + "\n",
        )

        // MainActivity.kt
        root.write(
            "app/src/main/java/$pkgPath/MainActivity.kt",
            """
            package $pkg

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.Spacer
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.height
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.Button
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.getValue
            import androidx.compose.runtime.mutableIntStateOf
            import androidx.compose.runtime.remember
            import androidx.compose.runtime.setValue
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.ui.unit.dp
            import $pkg.ui.theme.${appNoSpace}Theme

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    enableEdgeToEdge()
                    setContent {
                        ${appNoSpace}Theme {
                            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                Greeting(
                                    name = "$safeRootName",
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }
                        }
                    }
                }
            }

            @Composable
            fun Greeting(name: String, modifier: Modifier = Modifier) {
                var count by remember { mutableIntStateOf(0) }
                Column(
                    modifier = modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Hello from ${'$'}name!",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { count++ }) {
                        Text(text = "Clicked ${'$'}count times")
                    }
                }
            }

            @Preview(showBackground = true)
            @Composable
            fun GreetingPreview() {
                ${appNoSpace}Theme { Greeting("Android") }
            }
            """
                .trimIndent() + "\n",
        )

        // Example unit + instrumented tests
        root.write(
            "app/src/test/java/$pkgPath/ExampleUnitTest.kt",
            """
            package $pkg

            import org.junit.Assert.assertEquals
            import org.junit.Test

            /** Example local unit test, which will execute on the development machine (host). */
            class ExampleUnitTest {
                @Test
                fun addition_isCorrect() {
                    assertEquals(4, 2 + 2)
                }
            }
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/androidTest/java/$pkgPath/ExampleInstrumentedTest.kt",
            """
            package $pkg

            import androidx.test.ext.junit.runners.AndroidJUnit4
            import androidx.test.platform.app.InstrumentationRegistry
            import org.junit.Assert.assertEquals
            import org.junit.Test
            import org.junit.runner.RunWith

            /** Instrumented test, which will execute on an Android device. */
            @RunWith(AndroidJUnit4::class)
            class ExampleInstrumentedTest {
                @Test
                fun useAppContext() {
                    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
                    assertEquals("$pkg", appContext.packageName)
                }
            }
            """
                .trimIndent() + "\n",
        )

        // Resources
        root.write(
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">$safeXmlAppName</string>
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values/colors.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="black">#FF000000</color>
                <color name="white">#FFFFFFFF</color>
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values/themes.xml",
            """
            <resources xmlns:tools="http://schemas.android.com/tools">
                <style name="$styleName" parent="android:Theme.Material.Light.NoActionBar" />
            </resources>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/values-night/themes.xml",
            """
            <resources xmlns:tools="http://schemas.android.com/tools">
                <style name="$styleName" parent="android:Theme.Material.NoActionBar" />
            </resources>
            """
                .trimIndent() + "\n",
        )

        // Backup / data-extraction rules
        root.write(
            "app/src/main/res/xml/backup_rules.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <full-backup-content>
            </full-backup-content>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/xml/data_extraction_rules.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <data-extraction-rules>
                <cloud-backup>
                </cloud-backup>
            </data-extraction-rules>
            """
                .trimIndent() + "\n",
        )

        // Launcher icons (vector adaptive + legacy fallback)
        root.write(
            "app/src/main/res/drawable/ic_launcher_background.xml",
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path android:fillColor="#3DDC84" android:pathData="M0,0h108v108h-108z" />
            </vector>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path android:fillColor="#FFFFFF" android:pathData="M54,34 a20,20 0 1,0 0,40 a20,20 0 1,0 0,-40 z" />
            </vector>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
                <monochrome android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
            """
                .trimIndent() + "\n",
        )
        root.write(
            "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
                <monochrome android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
            """
                .trimIndent() + "\n",
        )
        val legacyIcon =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
                <item android:drawable="@drawable/ic_launcher_background" />
                <item android:drawable="@drawable/ic_launcher_foreground" />
            </layer-list>
            """
                .trimIndent() + "\n"
        root.write("app/src/main/res/mipmap/ic_launcher.xml", legacyIcon)
        root.write("app/src/main/res/mipmap/ic_launcher_round.xml", legacyIcon)

        // Root .gitignore
        root.write(
            ".gitignore",
            """
            *.iml
            .gradle
            /local.properties
            /.idea
            .DS_Store
            /build
            /captures
            .externalNativeBuild
            .cxx
            local.properties
            """
                .trimIndent() + "\n",
        )

        writeGradleWrapper(root, "9.5.1")

        root.write(
            "README.md",
            """
            # ${config.name}

            A Jetpack Compose Android app generated as a complete Android Studio project
            (version catalog, theme package, adaptive icons, tests).

            ## Building
            Requires the Android SDK. Install it from the editor's **Dependencies** download dialog,
            or point Gradle at an existing SDK via `local.properties`:

            ```
            sdk.dir=/path/to/android-sdk
            ```

            Then:

            ```bash
            ./gradlew assembleDebug
            ```

            > Build from an exec-capable filesystem (the terminal sandbox), not /sdcard.
            """
                .trimIndent() + "\n",
        )
    }

    // ---- Gradle wrapper (real, working: scripts + jar copied from bundled assets) ------

    private fun writeGradleWrapper(root: File, gradleVersion: String) {
        root.write(
            "gradle/wrapper/gradle-wrapper.properties",
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """
                .trimIndent() + "\n",
        )

        copyAsset("templates/gradle-wrapper.jar", File(root, "gradle/wrapper/gradle-wrapper.jar"))
        val gradlew = File(root, "gradlew")
        copyAsset("templates/gradlew", gradlew)
        copyAsset("templates/gradlew.bat", File(root, "gradlew.bat"))
        runCatching { gradlew.setExecutable(true, false) }
    }

    private fun copyAsset(assetPath: String, target: File) {
        runCatching {
            target.parentFile?.mkdirs()
            application!!.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}
