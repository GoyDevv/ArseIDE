package com.goydevv.arseide.horizon

import java.io.File
import org.json.JSONObject

/**
 * Validates Horizon Mod (`.hmod` / `.hnmod`) projects against Horizon's strict manifest and
 * Minecraft 26.3 client item model dispatch rules.
 */
object HorizonModValidator {

    data class ValidationReport(
        val modId: String,
        val modName: String,
        val version: String,
        val api: String,
        val isNativeMixin: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val declaredBlocks: Int,
        val declaredItems: Int,
    ) {
        val isValid: Boolean
            get() = errors.isEmpty()
    }

    private val ALLOWED_MANIFEST_KEYS =
        setOf(
            "id",
            "name",
            "version",
            "api",
            "entry",
            "side",
            "description",
            "authors",
            "namespaces",
            "capabilities",
            "experimental",
            "dependencies",
            "platform",
        )

    private val ALLOWED_MIXIN_KEYS =
        setOf(
            "id",
            "name",
            "version",
            "api",
            "description",
            "authors",
            "environment",
            "library",
            "libraries",
            "hooks",
        )

    private val EXPERIMENTAL_GATED_CAPABILITIES = setOf("ui", "render", "input")
    private val RESERVED_IDS = setOf("minecraft", "c", "horizon")
    private val MOD_ID_REGEX = Regex("^[a-z0-9_]{1,64}$")

    fun validate(projectDir: File): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val modToml = File(projectDir, "horizon-mod.toml")
        val mixinToml = File(projectDir, "horizon-mixin.toml")

        if (!modToml.isFile && mixinToml.isFile) {
            return validateNativeMixin(projectDir, mixinToml)
        }

        if (!modToml.isFile) {
            return ValidationReport(
                modId = projectDir.name.lowercase(),
                modName = projectDir.name,
                version = "0.1.0",
                api = "0.2",
                isNativeMixin = false,
                errors = listOf("Missing horizon-mod.toml in project root."),
                warnings = emptyList(),
                declaredBlocks = 0,
                declaredItems = 0,
            )
        }

        val parsed = parseSimpleToml(modToml.readText())
        val unknownKeys = parsed.topKeys - ALLOWED_MANIFEST_KEYS
        if (unknownKeys.isNotEmpty()) {
            errors +=
                "Unknown keys in horizon-mod.toml (rejected by Horizon strict manifest parser): ${unknownKeys.sorted().joinToString(", ")}"
        }

        val id = parsed.strings["id"].orEmpty().trim()
        val name = parsed.strings["name"].orEmpty().trim()
        val version = parsed.strings["version"].orEmpty().trim()
        val api = parsed.strings["api"].orEmpty().trim()
        val side = parsed.strings["side"].orEmpty().trim()

        if (id.isEmpty()) errors += "Missing required field 'id' in horizon-mod.toml."
        if (name.isEmpty()) errors += "Missing required field 'name' in horizon-mod.toml."
        if (version.isEmpty()) errors += "Missing required field 'version' in horizon-mod.toml."
        if (api.isEmpty()) errors += "Missing required field 'api' in horizon-mod.toml."

        if (id.isNotEmpty()) {
            if (id in RESERVED_IDS) {
                errors += "Mod id '$id' is reserved (cannot be 'minecraft', 'c', or 'horizon')."
            } else if (!MOD_ID_REGEX.matches(id)) {
                errors += "Mod id '$id' must match ^[a-z0-9_]{1,64}$ (lowercase ASCII, digits, underscores only)."
            }
        }

        if (api.isNotEmpty() && api !in setOf("0.1", "0.2", "0.3")) {
            warnings += "Uncommon Horizon API version '$api' (expected '0.1', '0.2', or '0.3')."
        }

        if (side.isNotEmpty() && side !in setOf("client", "server", "both")) {
            errors += "Invalid side '$side' in horizon-mod.toml (must be 'client', 'server', or 'both')."
        }

        val capabilities = parsed.arrays["capabilities"].orEmpty()
        val experimental = parsed.arrays["experimental"].orEmpty().toSet()

        for (cap in capabilities) {
            if (cap in EXPERIMENTAL_GATED_CAPABILITIES && cap !in experimental) {
                errors +=
                    "Capability \"$cap\" requires \"$cap\" to also be declared in experimental = [...] in horizon-mod.toml."
            }
        }

        // Verify every declared block and item has a 26.3 client item model dispatch at assets/<id>/items/<name>.json
        var blockCount = 0
        var itemCount = 0
        if (id.isNotEmpty()) {
            val blockDir = File(projectDir, "data/$id/horizon/block")
            val itemDir = File(projectDir, "data/$id/horizon/item")
            val dispatchDir = File(projectDir, "assets/$id/items")

            if (blockDir.isDirectory) {
                blockDir.listFiles { f -> f.isFile && f.extension == "json" }?.sortedBy { it.name }?.forEach { bfile ->
                    blockCount++
                    val blockName = bfile.nameWithoutExtension
                    val hasBlockItem =
                        runCatching {
                                val obj = JSONObject(bfile.readText())
                                obj.optBoolean("item", true)
                            }
                            .getOrElse {
                                errors += "Malformed JSON in data/$id/horizon/block/${bfile.name}: ${it.message}"
                                true
                            }
                    if (hasBlockItem) {
                        val dispatchFile = File(dispatchDir, "$blockName.json")
                        if (!dispatchFile.isFile) {
                            errors +=
                                "Block '$id:$blockName' has item=true but is missing 26.3 client item dispatch at assets/$id/items/$blockName.json."
                        }
                    }
                }
            }

            if (itemDir.isDirectory) {
                itemDir.listFiles { f -> f.isFile && f.extension == "json" }?.sortedBy { it.name }?.forEach { ifile ->
                    itemCount++
                    val itemName = ifile.nameWithoutExtension
                    runCatching { JSONObject(ifile.readText()) }
                        .onFailure {
                            errors += "Malformed JSON in data/$id/horizon/item/${ifile.name}: ${it.message}"
                        }
                    val dispatchFile = File(dispatchDir, "$itemName.json")
                    if (!dispatchFile.isFile) {
                        errors +=
                            "Item '$id:$itemName' is missing 26.3 client item model dispatch at assets/$id/items/$itemName.json."
                    }
                }
            }

            if ((blockCount > 0 || itemCount > 0) && "registries" !in capabilities) {
                errors +=
                    "Project declares $blockCount block(s) and $itemCount item(s) under data/$id/horizon/, but 'registries' is missing from capabilities = [...] in horizon-mod.toml."
            }
        }

        // Check uppercase paths under assets/ and data/
        listOf("assets", "data").forEach { folder ->
            val root = File(projectDir, folder)
            if (root.isDirectory) {
                root.walkTopDown().forEach { f ->
                    val rel = f.relativeTo(projectDir).invariantSeparatorsPath
                    if (rel.any { it.isUpperCase() }) {
                        errors += "Uppercase path detected ('$rel') — all paths under assets/ and data/ must be lowercase."
                    }
                }
            }
        }

        return ValidationReport(
            modId = id.ifEmpty { projectDir.name.lowercase() },
            modName = name.ifEmpty { projectDir.name },
            version = version.ifEmpty { "0.1.0" },
            api = api.ifEmpty { "0.2" },
            isNativeMixin = false,
            errors = errors,
            warnings = warnings,
            declaredBlocks = blockCount,
            declaredItems = itemCount,
        )
    }

    private fun validateNativeMixin(projectDir: File, mixinToml: File): ValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val parsed = parseSimpleToml(mixinToml.readText())
        val unknown = parsed.topKeys - ALLOWED_MIXIN_KEYS
        if (unknown.isNotEmpty()) {
            warnings += "Unrecognised top-level keys in horizon-mixin.toml: ${unknown.sorted().joinToString(", ")}"
        }
        val id = parsed.strings["id"].orEmpty().trim()
        val name = parsed.strings["name"].orEmpty().trim()
        val version = parsed.strings["version"].orEmpty().trim()
        val api = parsed.strings["api"].orEmpty().trim().ifEmpty { "0.1" }
        if (id.isEmpty()) errors += "Missing 'id' in horizon-mixin.toml."
        else if (id in RESERVED_IDS || !MOD_ID_REGEX.matches(id)) {
            errors += "Invalid mod id '$id' in horizon-mixin.toml."
        }
        if (name.isEmpty()) errors += "Missing 'name' in horizon-mixin.toml."
        return ValidationReport(
            modId = id.ifEmpty { projectDir.name.lowercase() },
            modName = name.ifEmpty { projectDir.name },
            version = version.ifEmpty { "0.1.0" },
            api = api,
            isNativeMixin = true,
            errors = errors,
            warnings = warnings,
            declaredBlocks = 0,
            declaredItems = 0,
        )
    }

    data class ParsedToml(
        val topKeys: Set<String>,
        val strings: Map<String, String>,
        val arrays: Map<String, List<String>>,
    )

    fun parseSimpleToml(text: String): ParsedToml {
        val topKeys = linkedSetOf<String>()
        val strings = linkedMapOf<String, String>()
        val arrays = linkedMapOf<String, List<String>>()
        var currentSection: String? = null

        val lines = text.lines()
        var idx = 0
        while (idx < lines.size) {
            val rawLine = lines[idx].substringBefore('#').trim()
            idx++
            if (rawLine.isEmpty()) continue
            if (rawLine.startsWith("[")) {
                val section = rawLine.trim('[', ']').trim().substringBefore('.')
                if (section.isNotEmpty()) {
                    currentSection = section
                    topKeys += section
                }
                continue
            }
            if ('=' in rawLine && currentSection == null) {
                val key = rawLine.substringBefore('=').trim()
                var rhs = rawLine.substringAfter('=').trim()
                topKeys += key
                if (rhs.startsWith("[")) {
                    while (!rhs.endsWith("]") && idx < lines.size) {
                        rhs += " " + lines[idx].substringBefore('#').trim()
                        idx++
                    }
                    val inner = rhs.removePrefix("[").removeSuffix("]").trim()
                    val items =
                        if (inner.isEmpty()) emptyList()
                        else
                            inner
                                .split(',')
                                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                                .filter { it.isNotEmpty() }
                    arrays[key] = items
                } else {
                    strings[key] = rhs.removeSurrounding("\"").removeSurrounding("'")
                }
            }
        }
        return ParsedToml(topKeys, strings, arrays)
    }
}
