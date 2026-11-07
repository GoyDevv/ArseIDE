package com.goydevv.arseide.horizon

import java.io.File
import java.util.zip.ZipFile
import org.json.JSONObject

/**
 * Validates `.hnmod` (Horizon Native Mod) project directories and packaged `.hnmod` archives
 * against the exact rules of `crates/horizon-mods/src/index/native.rs` (`Bundle::read`).
 */
object HorizonModValidator {

    const val MANIFEST_NAME = "horizon-native.json"
    const val DEFAULT_HORIZON_REV = "6f8c7a0e5d531d3834c4d0be4659a1db8e7e5c2a"
    const val DEFAULT_MIXINS_REV = "e3ee0a7c450b28c3ed58fa59a4881ec78c5ee2e0"
    const val MAX_FILES = 512
    const val MAX_TOTAL_BYTES = 64L * 1024L * 1024L

    private val ALLOWED_MANIFEST_KEYS =
        setOf("schema", "id", "version", "game", "horizon", "mixins", "hooks")
    private val RESERVED_IDS = setOf("minecraft", "c", "horizon")
    private val MOD_ID_REGEX = Regex("^[a-z0-9_]{1,64}$")
    private val REVISION_REGEX = Regex("^[0-9a-f]{40}$")
    private val ALLOWED_TEXT_PREFIXES = listOf("README", "LICENSE", "LICENCE", "COPYING", "NOTICE")

    data class ValidationReport(
        val modId: String,
        val version: String,
        val game: String,
        val horizonRev: String,
        val mixinsRev: String,
        val hooksApi: Int?,
        val fileCount: Int,
        val hasEngineCrate: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
    ) {
        val isValid: Boolean
            get() = errors.isEmpty()
    }

    fun isValidId(id: String): Boolean = id !in RESERVED_IDS && MOD_ID_REGEX.matches(id)

    fun isValidRevision(rev: String): Boolean = REVISION_REGEX.matches(rev)

    /**
     * Exact port of `in_data(rest)` from `reference/native_bundle_validator.rs`:
     * Under `mixins/<id>/data/...` or `mixins/<id>/<crate>/data/...`.
     */
    fun isInData(rest: String): Boolean {
        val parts = rest.split('/')
        return parts.size >= 2 && (parts[0] == "data" || (parts.size >= 3 && parts[1] == "data"))
    }

    /**
     * Exact port of `text(rest)` from `reference/native_bundle_validator.rs`:
     * `README*`, `LICENSE*`, `LICENCE*`, `COPYING*`, `NOTICE*`, and `.gitignore` in `mixins/<id>/`
     * or a top-level folder of it (`mixins/<id>/<crate>/`).
     */
    fun isAllowedText(rest: String): Boolean {
        val parts = rest.split('/')
        val name = parts.lastOrNull() ?: return false
        return parts.size <= 2 &&
            (ALLOWED_TEXT_PREFIXES.any { name.startsWith(it) } || name == ".gitignore")
    }

    /**
     * Exact port of `allowed(rest)` from `reference/native_bundle_validator.rs`.
     */
    fun isAllowedMixinPath(rest: String): Boolean =
        rest.endsWith(".rs") || rest.endsWith(".toml") || isInData(rest) || isAllowedText(rest)

    fun validateProjectDir(projectDir: File): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val manifestFile = File(projectDir, MANIFEST_NAME)
        if (!manifestFile.isFile) {
            return ValidationReport(
                modId = projectDir.name.removeSuffix("-hnmod"),
                version = "0.1.0",
                game = "26.3",
                horizonRev = DEFAULT_HORIZON_REV,
                mixinsRev = DEFAULT_MIXINS_REV,
                hooksApi = null,
                fileCount = 0,
                hasEngineCrate = false,
                errors = listOf("Missing horizon-native.json at project root."),
                warnings = emptyList(),
            )
        }

        val manifestJson =
            runCatching { JSONObject(manifestFile.readText()) }
                .getOrElse {
                    return ValidationReport(
                        modId = projectDir.name.removeSuffix("-hnmod"),
                        version = "0.1.0",
                        game = "26.3",
                        horizonRev = DEFAULT_HORIZON_REV,
                        mixinsRev = DEFAULT_MIXINS_REV,
                        hooksApi = null,
                        fileCount = 0,
                        hasEngineCrate = false,
                        errors = listOf("Malformed JSON in horizon-native.json: ${it.message}"),
                        warnings = emptyList(),
                    )
                }

        val keys = mutableSetOf<String>()
        val keyIter = manifestJson.keys()
        while (keyIter.hasNext()) keys += keyIter.next()
        val unknownKeys = keys - ALLOWED_MANIFEST_KEYS
        if (unknownKeys.isNotEmpty()) {
            errors += "horizon-native.json contains unknown fields: ${unknownKeys.sorted().joinToString(", ")}"
        }

        val schema = manifestJson.optInt("schema", -1)
        val modId = manifestJson.optString("id", "").trim()
        val version = manifestJson.optString("version", "").trim()
        val game = manifestJson.optString("game", "").trim()
        val horizonRev = manifestJson.optString("horizon", "").trim()
        val mixinsRev = manifestJson.optString("mixins", "").trim()
        val hooksApi: Int? = if (manifestJson.has("hooks") && !manifestJson.isNull("hooks")) manifestJson.optInt("hooks", 0) else null

        if (schema != 1) errors += "horizon-native.json 'schema' must be 1 (found $schema)."
        if (!isValidId(modId)) errors += "horizon-native.json 'id' ('$modId') must match [a-z0-9_]{1,64} and not be reserved."
        if (version.isEmpty()) errors += "horizon-native.json 'version' cannot be empty."
        if (game.isEmpty()) errors += "horizon-native.json 'game' cannot be empty (expected \"26.3\")."
        if (!isValidRevision(horizonRev)) errors += "horizon-native.json 'horizon' must be a 40-char lowercase hex git SHA."
        if (!isValidRevision(mixinsRev)) errors += "horizon-native.json 'mixins' must be a 40-char lowercase hex git SHA."
        if (hooksApi != null && hooksApi <= 0) errors += "horizon-native.json 'hooks' must be >= 1 when present."

        val mixinDir = File(projectDir, "mixins/$modId")
        val mixinTomlFile = File(mixinDir, "horizon-mixin.toml")
        if (!mixinTomlFile.isFile) {
            errors += "Missing mixins/$modId/horizon-mixin.toml."
        } else {
            val mixinParsed = parseMixinTomlIdentity(mixinTomlFile.readText())
            if (mixinParsed.modId != modId) {
                errors += "mixins/$modId/horizon-mixin.toml [mod].id ('${mixinParsed.modId}') does not match horizon-native.json id ('$modId')."
            }
            if (mixinParsed.version != version) {
                errors += "mixins/$modId/horizon-mixin.toml [mod].version ('${mixinParsed.version}') does not match horizon-native.json version ('$version')."
            }
            if (mixinParsed.horizonRevision != horizonRev) {
                errors += "mixins/$modId/horizon-mixin.toml [compatibility].horizon_revision ('${mixinParsed.horizonRevision}') does not match horizon-native.json horizon ('$horizonRev')."
            }
        }

        var validFileCount = 1 // horizon-native.json
        var totalBytes = manifestFile.length()
        if (mixinDir.isDirectory) {
            mixinDir
                .walkTopDown()
                .onEnter { it.name !in setOf("target", ".git", "__pycache__", ".gradle", ".idea") }
                .filter { it.isFile }
                .forEach { f ->
                    val rest = f.relativeTo(mixinDir).invariantSeparatorsPath
                    if (isAllowedMixinPath(rest)) {
                        validFileCount++
                        totalBytes += f.length()
                    } else if (f.name != "Cargo.lock") {
                        warnings += "Ignored non-bundle file during .hnmod packaging: mixins/$modId/$rest"
                    }
                }
        }

        if (validFileCount > MAX_FILES) {
            errors += "Native bundle has $validFileCount files (exceeds 512 limit)."
        }
        if (totalBytes > MAX_TOTAL_BYTES) {
            errors += "Native bundle exceeds 64 MiB uncompressed limit."
        }

        val hasEngineCrate = File(mixinDir, "engine/Cargo.toml").isFile

        return ValidationReport(
            modId = modId.ifEmpty { projectDir.name.removeSuffix("-hnmod") },
            version = version.ifEmpty { "0.1.0" },
            game = game.ifEmpty { "26.3" },
            horizonRev = horizonRev.ifEmpty { DEFAULT_HORIZON_REV },
            mixinsRev = mixinsRev.ifEmpty { DEFAULT_MIXINS_REV },
            hooksApi = hooksApi,
            fileCount = validFileCount,
            hasEngineCrate = hasEngineCrate,
            errors = errors,
            warnings = warnings,
        )
    }

    /**
     * Validates an already-packaged `.hnmod` zip file directly against `Bundle::read`.
     */
    fun validateHnmodArchive(hnmodZip: File): List<String> {
        val errors = mutableListOf<String>()
        if (!hnmodZip.isFile) return listOf("Archive not found: ${hnmodZip.absolutePath}")
        runCatching {
            ZipFile(hnmodZip).use { zf ->
                val entries = zf.entries().toList().filter { !it.isDirectory }
                if (entries.size > MAX_FILES) errors += "Archive has ${entries.size} entries (max 512)."
                val manifestEntry = zf.getEntry(MANIFEST_NAME)
                if (manifestEntry == null) {
                    errors += "Archive is missing root horizon-native.json."
                    return@use
                }
                val manifestText = zf.getInputStream(manifestEntry).bufferedReader().readText()
                val manifestJson = JSONObject(manifestText)
                val modId = manifestJson.optString("id", "")
                val version = manifestJson.optString("version", "")
                val horizonRev = manifestJson.optString("horizon", "")
                val mixinsRev = manifestJson.optString("mixins", "")
                if (manifestJson.optInt("schema", -1) != 1 || !isValidId(modId) || !isValidRevision(horizonRev) || !isValidRevision(mixinsRev)) {
                    errors += "Invalid root horizon-native.json fields."
                }
                val prefix = "mixins/$modId/"
                entries.forEach { entry ->
                    val name = entry.name
                    if (name != MANIFEST_NAME) {
                        if (!name.startsWith(prefix)) {
                            errors += "Unexpected archive entry outside $prefix: $name"
                        } else {
                            val rest = name.removePrefix(prefix)
                            if (!isAllowedMixinPath(rest)) {
                                errors += "Disallowed archive file under $prefix: $name"
                            }
                        }
                    }
                }
                val mixinEntry = zf.getEntry("${prefix}horizon-mixin.toml")
                if (mixinEntry == null) {
                    errors += "Archive is missing ${prefix}horizon-mixin.toml."
                } else {
                    val tomlText = zf.getInputStream(mixinEntry).bufferedReader().readText()
                    val parsed = parseMixinTomlIdentity(tomlText)
                    if (parsed.modId != modId || parsed.version != version || parsed.horizonRevision != horizonRev) {
                        errors += "Native manifest and horizon-mixin.toml identity/revision differ."
                    }
                }
            }
        }.onFailure { errors += "Corrupt .hnmod zip archive: ${it.message}" }
        return errors
    }

    data class MixinTomlIdentity(
        val modId: String?,
        val version: String?,
        val horizonRevision: String?,
    )

    fun parseMixinTomlIdentity(tomlText: String): MixinTomlIdentity {
        var currentSection = ""
        var modId: String? = null
        var version: String? = null
        var horizonRevision: String? = null

        tomlText.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("[")) {
                currentSection = line.trim('[', ']').trim()
                return@forEach
            }
            if ('=' in line) {
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
                when (currentSection) {
                    "mod" -> {
                        if (key == "id") modId = value
                        if (key == "version") version = value
                    }
                    "compatibility" -> {
                        if (key == "horizon_revision") horizonRevision = value
                    }
                }
            }
        }
        return MixinTomlIdentity(modId, version, horizonRevision)
    }
}
