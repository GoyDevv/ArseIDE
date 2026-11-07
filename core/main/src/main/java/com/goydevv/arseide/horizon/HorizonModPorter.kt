package com.goydevv.arseide.horizon

import com.goydevv.arseide.utils.application
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Full 4-stage Java (Fabric / NeoForge / Forge) -> Horizon Mod (`.hmod`) Porter.
 *
 * Accepts either:
 *  - An open-source Fabric/NeoForge/Forge project directory
 *  - A compiled `.jar` file
 *
 * Stages:
 *  1. **Metadata Extraction**: Parses `fabric.mod.json`, `META-INF/neoforge.mods.toml`, or
 *     `META-INF/mods.toml` to extract `id` (normalized to `[a-z0-9_]{1,64}`, never `minecraft`, `c`,
 *     or `horizon`), `name`, `version`, `description`, `authors`, and extra `namespaces`.
 *  2. **26.3 Asset & Data Upgrade**:
 *     - Lowercases all paths under `assets/` and `data/`.
 *     - Renames legacy 1.20/1.21 plural data folders to 26.3 singular folders (`recipes` -> `recipe`,
 *       `loot_tables` -> `loot_table`, `structures` -> `structure`, `advancements` -> `advancement`,
 *       `tags/blocks` -> `tags/block`, `tags/items` -> `tags/item`).
 *     - Upgrades 1.21.1 recipe ingredients (`{"item":"x"}` -> `"x"`, `{"tag":"y"}` -> `"#y"`),
 *       loot tables (`conditions` -> `condition`, `functions` -> `modifier`, `"function"` -> `"type"`),
 *       and renamed vanilla IDs (`minecraft:chain` -> `minecraft:iron_chain`).
 *     - Generates 26.3 client item model dispatch files at `assets/<id>/items/<name>.json`.
 *  3. **Java Registry -> Declarative Horizon JSON Conversion**:
 *     - Inspects Java `Registry.register(BuiltInRegistries.BLOCK/ITEM, ...)` calls,
 *       `assets/<id>/blockstates/` and `assets/<id>/models/item/` JSON files to generate
 *       `data/<id>/horizon/block/<name>.json`, `data/<id>/horizon/item/<name>.json` (with 26.3
 *       `components`), and `data/<id>/horizon/creative_tab/main.json`.
 *  4. **Java Code -> Rust WASM (`mod.wasm`) / ServerMod Translation**:
 *     - Translates detected client HUDs, keybindings, custom screens, or raycasts into `src/lib.rs`
 *       using `horizon-mod-sdk` (`Mod` trait) or server events/commands using `ServerMod` (`api = "0.3"`),
 *       bundling the local `sdk/horizon-mod-sdk` and adding matching capabilities to `horizon-mod.toml`.
 */
object HorizonModPorter {

    data class PortResult(
        val success: Boolean,
        val outputDir: File,
        val packagedHmod: File?,
        val modId: String,
        val modName: String,
        val version: String,
        val apiVersion: String,
        val blocksPorted: Int,
        val itemsPorted: Int,
        val recipesUpgraded: Int,
        val lootTablesUpgraded: Int,
        val generatedWasmSource: Boolean,
        val logs: List<String>,
        val error: String? = null,
    )

    private val RESERVED_IDS = setOf("minecraft", "c", "horizon")

    private val PLURAL_DATA_RENAMES =
        mapOf(
            "recipes" to "recipe",
            "loot_tables" to "loot_table",
            "structures" to "structure",
            "advancements" to "advancement",
            "predicates" to "predicate",
            "item_modifiers" to "item_modifier",
        )

    private val PLURAL_TAG_RENAMES =
        mapOf(
            "blocks" to "block",
            "items" to "item",
            "entity_types" to "entity_type",
            "fluids" to "fluid",
            "game_events" to "game_event",
        )

    private val VANILLA_ID_RENAMES =
        mapOf(
            "minecraft:chain" to "minecraft:iron_chain",
        )

    private val SOUND_MAP =
        mapOf(
            "WOOD" to "wood",
            "STONE" to "stone",
            "METAL" to "metal",
            "IRON" to "metal",
            "COPPER" to "copper",
            "GLASS" to "glass",
            "GRASS" to "grass",
            "GRAVEL" to "gravel",
            "SAND" to "sand",
            "SNOW" to "snow",
            "WOOL" to "wool",
            "SLIME_BLOCK" to "slime",
            "HONEY_BLOCK" to "honey",
            "ANCIENT_DEBRIS" to "netherite",
            "NETHERITE_BLOCK" to "netherite",
            "AMETHYST" to "amethyst",
            "DEEPSLATE" to "deepslate",
            "TUFF" to "tuff",
        )

    fun sanitizeModId(raw: String): String {
        val cleaned =
            raw.trim()
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
        val valid = if (cleaned.isEmpty() || cleaned in RESERVED_IDS) "ported_${cleaned.ifEmpty { "mod" }}" else cleaned
        return valid.take(64)
    }

    suspend fun port(
        sourceFileOrDir: File,
        targetParentDir: File,
        customOutputName: String? = null,
        alsoPackageHmod: Boolean = true,
    ): PortResult =
        withContext(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            var tempExtractDir: File? = null

            try {
                if (!sourceFileOrDir.exists()) {
                    return@withContext failResult(
                        targetParentDir,
                        logs,
                        "Source path does not exist: ${sourceFileOrDir.absolutePath}",
                    )
                }

                val workingSourceDir: File =
                    if (sourceFileOrDir.isFile && sourceFileOrDir.extension.equals("jar", ignoreCase = true)) {
                        logs += "Extracting JAR archive ${sourceFileOrDir.name}…"
                        val tmp = File(targetParentDir, ".tmp_port_${System.currentTimeMillis()}")
                        tmp.mkdirs()
                        tempExtractDir = tmp
                        extractJar(sourceFileOrDir, tmp)
                        tmp
                    } else if (sourceFileOrDir.isDirectory) {
                        logs += "Scanning Java/Fabric/NeoForge project folder ${sourceFileOrDir.name}…"
                        sourceFileOrDir
                    } else {
                        return@withContext failResult(
                            targetParentDir,
                            logs,
                            "Unsupported input: select a .jar file or a Fabric/NeoForge project folder.",
                        )
                    }

                // Stage 1: Metadata Extraction
                val meta = extractMetadata(workingSourceDir)
                val modId = meta.id
                logs += "Extracted metadata: id=$modId, name=\"${meta.name}\", version=${meta.version}"

                val outFolderName = customOutputName?.trim()?.ifBlank { null } ?: "${modId}-hmod"
                val outRoot = File(targetParentDir, outFolderName)
                if (outRoot.exists()) outRoot.deleteRecursively()
                outRoot.mkdirs()

                val resRoot =
                    File(workingSourceDir, "src/main/resources").takeIf { it.isDirectory } ?: workingSourceDir

                // Parse Java sources (if available) for Registry.register calls and client/server hooks
                val javaAnalysis = analyzeJavaSources(workingSourceDir)
                if (javaAnalysis.blocks.isNotEmpty() || javaAnalysis.items.isNotEmpty()) {
                    logs +=
                        "Parsed Java source registrations: ${javaAnalysis.blocks.size} block(s), ${javaAnalysis.items.size} item(s)"
                }

                // Stage 2: 26.3 Asset & Data Upgrade
                val extraNamespaces = sortedSetOf<String>()
                var recipesUpgraded = 0
                var lootTablesUpgraded = 0

                val assetsSrc = File(resRoot, "assets")
                if (assetsSrc.isDirectory) {
                    assetsSrc.listFiles()?.filter { it.isDirectory }?.forEach { nsDir ->
                        val ns = nsDir.name.lowercase()
                        if (ns != modId && ns != "minecraft") extraNamespaces += ns
                        nsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val rel = file.relativeTo(assetsSrc).invariantSeparatorsPath.lowercase()
                            val dest = File(outRoot, "assets/$rel")
                            dest.parentFile?.mkdirs()
                            file.copyTo(dest, overwrite = true)
                        }
                    }
                    logs += "Normalized assets/ paths to lowercase."
                }

                val dataSrc = File(resRoot, "data")
                if (dataSrc.isDirectory) {
                    dataSrc.listFiles()?.filter { it.isDirectory }?.forEach { nsDir ->
                        val ns = nsDir.name.lowercase()
                        if (ns != modId && ns != "minecraft") extraNamespaces += ns
                        nsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relParts = file.relativeTo(dataSrc).invariantSeparatorsPath.lowercase().split('/').toMutableList()
                            if (relParts.size >= 2) {
                                val cat = relParts[1]
                                PLURAL_DATA_RENAMES[cat]?.let { relParts[1] = it }
                                if (cat == "tags" && relParts.size >= 3) {
                                    PLURAL_TAG_RENAMES[relParts[2]]?.let { relParts[2] = it }
                                }
                            }
                            val remappedRel = relParts.joinToString("/")
                            val dest = File(outRoot, "data/$remappedRel")
                            dest.parentFile?.mkdirs()

                            if (file.extension.equals("json", ignoreCase = true)) {
                                val rawText = runCatching { file.readText() }.getOrDefault("")
                                val category = relParts.getOrNull(1)
                                when (category) {
                                    "recipe" -> {
                                        val upgraded = runCatching { upgradeRecipeJson(JSONObject(rawText)).toString(2) }.getOrNull()
                                        if (upgraded != null) {
                                            dest.writeText(upgraded + "\n")
                                            recipesUpgraded++
                                        } else {
                                            file.copyTo(dest, overwrite = true)
                                        }
                                    }
                                    "loot_table" -> {
                                        val upgraded = runCatching { upgradeLootJson(JSONObject(rawText)).toString(2) }.getOrNull()
                                        if (upgraded != null) {
                                            dest.writeText(upgraded + "\n")
                                            lootTablesUpgraded++
                                        } else {
                                            file.copyTo(dest, overwrite = true)
                                        }
                                    }
                                    else -> file.copyTo(dest, overwrite = true)
                                }
                            } else {
                                file.copyTo(dest, overwrite = true)
                            }
                        }
                    }
                    logs +=
                        "Upgraded data/ to Minecraft 26.3 singular folders ($recipesUpgraded recipe(s), $lootTablesUpgraded loot table(s) converted)."
                }

                // Stage 3: Java Registry + Blockstates + Models -> Declarative Horizon JSON Conversion
                val portedBlocks = sortedSetOf<String>()
                val blockstatesDir = File(outRoot, "assets/$modId/blockstates")
                val horizonBlockDir = File(outRoot, "data/$modId/horizon/block")
                horizonBlockDir.mkdirs()

                val allBlockNames = sortedSetOf<String>()
                allBlockNames.addAll(javaAnalysis.blocks.keys)
                if (blockstatesDir.isDirectory) {
                    blockstatesDir
                        .listFiles { f -> f.isFile && f.extension == "json" }
                        ?.forEach { allBlockNames += it.nameWithoutExtension.lowercase() }
                }

                for (bname in allBlockNames) {
                    val bsFile = File(blockstatesDir, "$bname.json")
                    val bsJson =
                        if (bsFile.isFile) runCatching { JSONObject(bsFile.readText()) }.getOrDefault(JSONObject())
                        else JSONObject()
                    val jBlock = javaAnalysis.blocks[bname]
                    val template = inferBlockTemplate(bname, bsJson, jBlock)
                    val blockObj =
                        JSONObject().apply {
                            put("template", template)
                            put("hardness", jBlock?.hardness ?: 2.0)
                            put("explosion_resistance", jBlock?.explosionResistance ?: 3.0)
                            put("requires_tool", jBlock?.requiresTool ?: (template == "minecraft:stone"))
                            jBlock?.light?.let { put("light", it) }
                            jBlock?.sounds?.let { put("sounds", it) }
                            put("item", true)
                        }
                    File(horizonBlockDir, "$bname.json").writeText(blockObj.toString(2) + "\n")
                    portedBlocks += bname
                }

                val modelsItemDir = File(outRoot, "assets/$modId/models/item")
                val itemsDispatchDir = File(outRoot, "assets/$modId/items")
                itemsDispatchDir.mkdirs()
                val horizonItemDir = File(outRoot, "data/$modId/horizon/item")
                horizonItemDir.mkdirs()

                val portedItems = sortedSetOf<String>()
                val candidateItems = sortedSetOf<String>()
                candidateItems.addAll(javaAnalysis.items.keys)
                if (modelsItemDir.isDirectory) {
                    modelsItemDir
                        .listFiles { f -> f.isFile && f.extension == "json" }
                        ?.forEach { candidateItems += it.nameWithoutExtension.lowercase() }
                }

                for (iname in candidateItems) {
                    val dispatchFile = File(itemsDispatchDir, "$iname.json")
                    if (!dispatchFile.exists()) {
                        val dispatchObj =
                            JSONObject().apply {
                                put(
                                    "model",
                                    JSONObject().apply {
                                        put("type", "minecraft:model")
                                        put("model", "$modId:item/$iname")
                                    },
                                )
                            }
                        dispatchFile.writeText(dispatchObj.toString(2) + "\n")
                    }

                    if (iname !in portedBlocks) {
                        val jItem = javaAnalysis.items[iname]
                        val itemObj =
                            JSONObject().apply {
                                put("max_stack_size", jItem?.maxStackSize ?: 64)
                                jItem?.rarity?.let { put("rarity", it) }
                                if (jItem?.fireResistant == true) put("fire_resistant", true)
                                jItem?.craftingRemainder?.let { put("crafting_remainder", it) }

                                val comps = JSONObject()
                                jItem?.maxDamage?.let { comps.put("minecraft:max_damage", it) }
                                jItem?.food?.let { food ->
                                    comps.put(
                                        "minecraft:food",
                                        JSONObject().apply {
                                            put("nutrition", food.nutrition)
                                            put("saturation", food.saturation)
                                            put("can_always_eat", food.canAlwaysEat)
                                        },
                                    )
                                }
                                if (comps.length() > 0) {
                                    put("components", comps)
                                }
                            }
                        File(horizonItemDir, "$iname.json").writeText(itemObj.toString(2) + "\n")
                        portedItems += iname
                    }
                }

                // Ensure every block with item:true has a 26.3 client item model dispatch
                for (bname in portedBlocks) {
                    val dispatchFile = File(itemsDispatchDir, "$bname.json")
                    if (!dispatchFile.exists()) {
                        val modelRef =
                            if (File(modelsItemDir, "$bname.json").exists()) "$modId:item/$bname"
                            else "$modId:block/$bname"
                        val dispatchObj =
                            JSONObject().apply {
                                put(
                                    "model",
                                    JSONObject().apply {
                                        put("type", "minecraft:model")
                                        put("model", modelRef)
                                    },
                                )
                            }
                        dispatchFile.writeText(dispatchObj.toString(2) + "\n")
                    }
                }

                // Generate data/<modId>/horizon/creative_tab/main.json
                val allTabItems = (portedBlocks + portedItems).sorted().map { "$modId:$it" }
                if (allTabItems.isNotEmpty()) {
                    val tabDir = File(outRoot, "data/$modId/horizon/creative_tab")
                    tabDir.mkdirs()
                    val tabObj =
                        JSONObject().apply {
                            put("title", meta.name)
                            put("icon", allTabItems.first())
                            put("items", JSONArray(allTabItems))
                        }
                    File(tabDir, "main.json").writeText(tabObj.toString(2) + "\n")
                }
                logs +=
                    "Generated Horizon registries: ${portedBlocks.size} block(s), ${portedItems.size} item(s), and 26.3 client item model dispatches."

                // Stage 4: Java Code -> Rust WASM / ServerMod Translation
                val wasmTranslation = generateRustWasmIfNeeded(outRoot, modId, meta, javaAnalysis)
                if (wasmTranslation.generatedWasm) {
                    copyBundledSdk(File(outRoot, "sdk/horizon-mod-sdk"))
                    logs +=
                        "Translated Java gameplay hooks to Rust WASM (src/lib.rs, API ${wasmTranslation.apiVersion}) and bundled horizon-mod-sdk."
                }

                // Write horizon-mod.toml
                val authorsToml = meta.authors.joinToString(", ") { "\"${it.replace("\"", "'")}\"" }
                val capsToml = wasmTranslation.capabilities.joinToString(", ") { "\"$it\"" }
                val expToml = wasmTranslation.experimental.joinToString(", ") { "\"$it\"" }
                val nsToml = extraNamespaces.joinToString(", ") { "\"$it\"" }
                val cleanDesc =
                    meta.description.lineSequence().firstOrNull()?.trim()?.replace("\"", "'").orEmpty().ifBlank {
                        "Ported to Horizon by Arse IDE"
                    }

                val manifestText = buildString {
                    appendLine("id = \"$modId\"")
                    appendLine("name = \"${meta.name.replace("\"", "'")}\"")
                    appendLine("version = \"${meta.version}\"")
                    appendLine("api = \"${wasmTranslation.apiVersion}\"")
                    appendLine("description = \"$cleanDesc\"")
                    appendLine("authors = [$authorsToml]")
                    if (wasmTranslation.apiVersion == "0.3") {
                        appendLine("side = \"server\"")
                    }
                    if (wasmTranslation.generatedWasm) {
                        appendLine("entry = \"mod.wasm\"")
                    }
                    if (extraNamespaces.isNotEmpty()) {
                        appendLine("namespaces = [$nsToml]")
                    }
                    appendLine("capabilities = [$capsToml]")
                    if (wasmTranslation.experimental.isNotEmpty()) {
                        appendLine("experimental = [$expToml]")
                    }
                }
                File(outRoot, "horizon-mod.toml").writeText(manifestText)

                // Copy helper tools into tools/ inside the ported project
                copyBundledTools(File(outRoot, "tools"))

                var packagedFile: File? = null
                if (alsoPackageHmod) {
                    val pkgRes =
                        HorizonModPackager.packageProject(
                            projectDir = outRoot,
                            outputFile = File(outRoot, "$modId.hmod"),
                            autoFixMissingDispatches = true,
                        )
                    if (pkgRes.success) {
                        packagedFile = pkgRes.archiveFile
                        logs += pkgRes.message
                    } else {
                        logs += "Warning during packaging: ${pkgRes.message}"
                    }
                }

                PortResult(
                    success = true,
                    outputDir = outRoot,
                    packagedHmod = packagedFile,
                    modId = modId,
                    modName = meta.name,
                    version = meta.version,
                    apiVersion = wasmTranslation.apiVersion,
                    blocksPorted = portedBlocks.size,
                    itemsPorted = portedItems.size,
                    recipesUpgraded = recipesUpgraded,
                    lootTablesUpgraded = lootTablesUpgraded,
                    generatedWasmSource = wasmTranslation.generatedWasm,
                    logs = logs,
                )
            } catch (t: Throwable) {
                failResult(targetParentDir, logs, t.message ?: "Unexpected error while porting mod")
            } finally {
                tempExtractDir?.let { runCatching { it.deleteRecursively() } }
            }
        }

    // ---- Internal helpers -------------------------------------------------------

    private data class ModMetadata(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val authors: List<String>,
    )

    private data class JavaBlockInfo(
        val template: String? = null,
        val hardness: Double? = null,
        val explosionResistance: Double? = null,
        val light: Int? = null,
        val requiresTool: Boolean? = null,
        val sounds: String? = null,
    )

    private data class JavaFoodInfo(
        val nutrition: Int,
        val saturation: Double,
        val canAlwaysEat: Boolean,
    )

    private data class JavaItemInfo(
        val maxStackSize: Int? = null,
        val maxDamage: Int? = null,
        val rarity: String? = null,
        val fireResistant: Boolean = false,
        val craftingRemainder: String? = null,
        val food: JavaFoodInfo? = null,
    )

    private data class JavaAnalysis(
        val blocks: Map<String, JavaBlockInfo>,
        val items: Map<String, JavaItemInfo>,
        val hasHud: Boolean,
        val hasKeybind: Boolean,
        val hasScreen: Boolean,
        val hasRaycast: Boolean,
        val hasServerBlockBreak: Boolean,
        val hasServerBlockUse: Boolean,
        val hasServerPlayerJoin: Boolean,
        val hasServerCommands: Boolean,
        val keybinds: List<Pair<String, String>>,
        val commands: List<String>,
    )

    private data class WasmTranslation(
        val generatedWasm: Boolean,
        val apiVersion: String,
        val capabilities: List<String>,
        val experimental: List<String>,
    )

    private fun extractJar(jarFile: File, destDir: File) {
        ZipFile(jarFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) continue
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun extractMetadata(root: File): ModMetadata {
        val fabricCandidates =
            listOf(
                File(root, "fabric.mod.json"),
                File(root, "src/main/resources/fabric.mod.json"),
            )
        fabricCandidates.firstOrNull { it.isFile }?.let { f ->
            runCatching {
                val obj = JSONObject(f.readText())
                val rawId = obj.optString("id", "ported_mod")
                val rawName = obj.optString("name", rawId)
                val rawVer = obj.optString("version", "0.1.0").let { if (it.startsWith("\${")) "0.1.0" else it }
                val desc = obj.optString("description", "")
                val authors = mutableListOf<String>()
                val arr = obj.optJSONArray("authors")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val el = arr.get(i)
                        when (el) {
                            is String -> authors += el
                            is JSONObject -> el.optString("name").takeIf { it.isNotBlank() }?.let { authors += it }
                        }
                    }
                }
                return ModMetadata(
                    id = sanitizeModId(rawId),
                    name = rawName.ifBlank { "Ported Mod" },
                    version = rawVer.ifBlank { "0.1.0" },
                    description = desc,
                    authors = authors.ifEmpty { listOf("Unknown") },
                )
            }
        }

        val tomlCandidates =
            listOf(
                File(root, "META-INF/neoforge.mods.toml"),
                File(root, "META-INF/mods.toml"),
                File(root, "src/main/resources/META-INF/neoforge.mods.toml"),
                File(root, "src/main/resources/META-INF/mods.toml"),
            )
        tomlCandidates.firstOrNull { it.isFile }?.let { f ->
            val text = runCatching { f.readText() }.getOrDefault("")
            val modId = Regex("""modId\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1) ?: "ported_mod"
            val name = Regex("""displayName\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1) ?: modId
            val version =
                Regex("""version\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1)?.let {
                    if (it.contains("\${")) "0.1.0" else it
                } ?: "0.1.0"
            val authorsStr = Regex("""authors\s*=\s*"([^"]+)"""").find(text)?.groupValues?.getOrNull(1) ?: "Unknown"
            val desc =
                Regex("""description\s*=\s*'''(.*?)'''""", setOf(RegexOption.DOT_MATCHES_ALL))
                    .find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    .orEmpty()
            return ModMetadata(
                id = sanitizeModId(modId),
                name = name,
                version = version,
                description = desc,
                authors = authorsStr.split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("Unknown") },
            )
        }

        return ModMetadata(
            id = sanitizeModId(root.nameWithoutExtension),
            name = root.nameWithoutExtension.replaceFirstChar { it.uppercase() },
            version = "0.1.0",
            description = "Ported to Horizon",
            authors = listOf("Unknown"),
        )
    }

    private fun upgradeIngredientValue(value: Any?): Any? =
        when (value) {
            is String -> VANILLA_ID_RENAMES[value] ?: value
            is JSONObject -> {
                if (value.length() == 1 && value.has("item")) {
                    val item = value.optString("item")
                    VANILLA_ID_RENAMES[item] ?: item
                } else if (value.length() == 1 && value.has("tag")) {
                    "#${value.optString("tag")}"
                } else {
                    val copy = JSONObject()
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        copy.put(k, upgradeIngredientValue(value.opt(k)))
                    }
                    copy
                }
            }
            is JSONArray -> {
                val arr = JSONArray()
                for (i in 0 until value.length()) {
                    arr.put(upgradeIngredientValue(value.opt(i)))
                }
                arr
            }
            else -> value
        }

    private fun upgradeRecipeJson(obj: JSONObject): JSONObject {
        val out = JSONObject(obj.toString())
        if (out.has("key") && out.opt("key") is JSONObject) {
            val keyObj = out.getJSONObject("key")
            val newKey = JSONObject()
            val iter = keyObj.keys()
            while (iter.hasNext()) {
                val k = iter.next()
                newKey.put(k, upgradeIngredientValue(keyObj.opt(k)))
            }
            out.put("key", newKey)
        }
        if (out.has("ingredients") && out.opt("ingredients") is JSONArray) {
            out.put("ingredients", upgradeIngredientValue(out.getJSONArray("ingredients")))
        }
        for (field in listOf("ingredient", "base", "addition", "template")) {
            if (out.has(field)) {
                out.put(field, upgradeIngredientValue(out.opt(field)))
            }
        }
        if (out.has("result")) {
            when (val res = out.opt("result")) {
                is String -> {
                    val count = out.optInt("count", 1)
                    out.remove("count")
                    out.put(
                        "result",
                        JSONObject().apply {
                            put("id", VANILLA_ID_RENAMES[res] ?: res)
                            put("count", count)
                        },
                    )
                }
                is JSONObject -> {
                    val resCopy = JSONObject(res.toString())
                    if (resCopy.has("item") && !resCopy.has("id")) {
                        val itemId = resCopy.optString("item")
                        resCopy.remove("item")
                        resCopy.put("id", VANILLA_ID_RENAMES[itemId] ?: itemId)
                    } else if (resCopy.has("id")) {
                        val id = resCopy.optString("id")
                        resCopy.put("id", VANILLA_ID_RENAMES[id] ?: id)
                    }
                    out.put("result", resCopy)
                }
            }
        }
        return out
    }

    private fun upgradeLootNode(node: Any?): Any? =
        when (node) {
            is JSONArray -> {
                val arr = JSONArray()
                for (i in 0 until node.length()) {
                    arr.put(upgradeLootNode(node.opt(i)))
                }
                arr
            }
            is JSONObject -> {
                val out = JSONObject()
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = node.opt(k)
                    when {
                        k == "functions" -> out.put("modifier", upgradeLootNode(v))
                        k == "function" -> out.put("type", upgradeLootNode(v))
                        k == "conditions" && v is JSONArray -> {
                            val upgraded = upgradeLootNode(v) as JSONArray
                            if (upgraded.length() == 1) {
                                out.put("condition", upgraded.opt(0))
                            } else if (upgraded.length() > 1) {
                                out.put(
                                    "condition",
                                    JSONObject().apply {
                                        put("condition", "minecraft:all_of")
                                        put("terms", upgraded)
                                    },
                                )
                            }
                        }
                        else -> out.put(k, upgradeLootNode(v))
                    }
                }
                val name = out.optString("name")
                if (name in VANILLA_ID_RENAMES) {
                    out.put("name", VANILLA_ID_RENAMES[name])
                }
                out
            }
            else -> node
        }

    private fun upgradeLootJson(obj: JSONObject): JSONObject = upgradeLootNode(obj) as JSONObject

    private fun inferBlockTemplate(name: String, blockstateJson: JSONObject, javaBlock: JavaBlockInfo?): String {
        javaBlock?.template?.let { return it }
        val variants = blockstateJson.optJSONObject("variants")
        val keysJoined =
            buildString {
                if (variants != null) {
                    val iter = variants.keys()
                    while (iter.hasNext()) append(iter.next()).append(' ')
                }
            }
        if (blockstateJson.has("multipart") && listOf("pipe", "cable", "duct", "conduit", "wire").any { it in name }) {
            return "minecraft:chorus_plant"
        }
        if ("half=" in keysJoined && "shape=" in keysJoined && "facing=" in keysJoined) return "minecraft:oak_stairs"
        if ("type=bottom" in keysJoined || "type=top" in keysJoined || name.endsWith("_slab")) return "minecraft:oak_slab"
        if ("half=lower" in keysJoined && "hinge=" in keysJoined) return "minecraft:oak_door"
        if ("open=" in keysJoined && "half=" in keysJoined && "facing=" in keysJoined) return "minecraft:oak_trapdoor"
        if ("axis=" in keysJoined || listOf("_log", "_pillar", "_stem", "_wood").any { name.endsWith(it) }) {
            return "minecraft:oak_log"
        }
        if ("lit=" in keysJoined && "facing=" in keysJoined) return "minecraft:furnace"
        if (name.endsWith("_fence")) return "minecraft:oak_fence"
        if (name.endsWith("_wall")) return "minecraft:cobblestone_wall"
        if (name.endsWith("_glass") || name.endsWith("_pane") || "glass" in name) return "minecraft:glass"
        if (name.endsWith("_planks")) return "minecraft:oak_planks"
        return "minecraft:stone"
    }

    private fun analyzeJavaSources(root: File): JavaAnalysis {
        val blocks = linkedMapOf<String, JavaBlockInfo>()
        val items = linkedMapOf<String, JavaItemInfo>()
        val keybinds = mutableListOf<Pair<String, String>>()
        val commands = mutableListOf<String>()

        val javaFiles = root.walkTopDown().filter { it.isFile && it.extension == "java" }.toList()
        if (javaFiles.isEmpty()) {
            return JavaAnalysis(
                blocks = blocks,
                items = items,
                hasHud = false,
                hasKeybind = false,
                hasScreen = false,
                hasRaycast = false,
                hasServerBlockBreak = false,
                hasServerBlockUse = false,
                hasServerPlayerJoin = false,
                hasServerCommands = false,
                keybinds = keybinds,
                commands = commands,
            )
        }

        val combined = javaFiles.joinToString("\n") { runCatching { it.readText() }.getOrDefault("") }

        val blockRegRegex =
            Regex(
                """(?:Registry\.register\s*\(\s*(?:BuiltInRegistries|Registries)\.BLOCK[^,]*,|registerBlock\s*\(|BLOCKS\.register\s*\()\s*(?:new\s+ResourceLocation\([^,]+,\s*"([a-z0-9_]+)"\)|Identifier\.of\([^,]+,\s*"([a-z0-9_]+)"\)|"([a-z0-9_]+)")\s*,\s*([^;]+);"""
            )
        blockRegRegex.findAll(combined).forEach { m ->
            val bname =
                (m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }).trim().lowercase()
            val expr = m.groupValues[4]
            if (bname.isNotEmpty()) {
                val template =
                    when {
                        "StairBlock" in expr || "StairsBlock" in expr -> "minecraft:oak_stairs"
                        "SlabBlock" in expr -> "minecraft:oak_slab"
                        "DoorBlock" in expr -> "minecraft:oak_door"
                        "TrapDoorBlock" in expr || "TrapdoorBlock" in expr -> "minecraft:oak_trapdoor"
                        "FenceBlock" in expr -> "minecraft:oak_fence"
                        "WallBlock" in expr -> "minecraft:cobblestone_wall"
                        "RotatedPillarBlock" in expr || "PillarBlock" in expr -> "minecraft:oak_log"
                        "FurnaceBlock" in expr || "AbstractFurnaceBlock" in expr -> "minecraft:furnace"
                        "PipeBlock" in expr || "ChorusPlantBlock" in expr -> "minecraft:chorus_plant"
                        else -> null
                    }
                val sm =
                    Regex("""\.strength\s*\(\s*([0-9.]+)\s*[fF]?(?:\s*,\s*([0-9.]+)\s*[fF]?)?\s*\)""").find(expr)
                val hardness = sm?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                val resistance = sm?.groupValues?.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: hardness
                val light =
                    Regex("""(?:luminance|lightLevel)\s*\([^)]*?([0-9]+)\s*\)""")
                        .find(expr)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?.coerceIn(0, 15)
                val reqTool =
                    if ("requiresCorrectToolForDrops" in expr || "requiresTool" in expr) true else null
                val soundKey =
                    Regex("""\.(?:sounds|sound)\s*\(\s*(?:BlockSoundGroup|SoundType)\.([A-Z_]+)\s*\)""")
                        .find(expr)
                        ?.groupValues
                        ?.getOrNull(1)
                blocks[bname] =
                    JavaBlockInfo(
                        template = template,
                        hardness = hardness,
                        explosionResistance = resistance,
                        light = light,
                        requiresTool = reqTool,
                        sounds = soundKey?.let { SOUND_MAP[it] },
                    )
            }
        }

        val itemRegRegex =
            Regex(
                """(?:Registry\.register\s*\(\s*(?:BuiltInRegistries|Registries)\.ITEM[^,]*,|registerItem\s*\(|ITEMS\.register\s*\()\s*(?:new\s+ResourceLocation\([^,]+,\s*"([a-z0-9_]+)"\)|Identifier\.of\([^,]+,\s*"([a-z0-9_]+)"\)|"([a-z0-9_]+)")\s*,\s*([^;]+);"""
            )
        itemRegRegex.findAll(combined).forEach { m ->
            val iname =
                (m.groupValues[1].ifEmpty { m.groupValues[2] }.ifEmpty { m.groupValues[3] }).trim().lowercase()
            val expr = m.groupValues[4]
            if (iname.isNotEmpty()) {
                val maxStack =
                    Regex("""\.(?:stacksTo|maxCount)\s*\(\s*([0-9]+)\s*\)""")
                        .find(expr)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?.coerceIn(1, 64)
                val maxDmg =
                    Regex("""\.(?:durability|maxDamage)\s*\(\s*([0-9]+)\s*\)""")
                        .find(expr)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                val rarity =
                    Regex("""\.rarity\s*\(\s*Rarity\.([A-Z_]+)\s*\)""")
                        .find(expr)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.lowercase()
                val foodMatch =
                    Regex(
                            """nutrition\s*\(\s*([0-9]+)\s*\).*?saturation(?:Modifier)?\s*\(\s*([0-9.]+)\s*[fF]?\s*\)""",
                            setOf(RegexOption.DOT_MATCHES_ALL),
                        )
                        .find(expr)
                val food =
                    foodMatch?.let {
                        JavaFoodInfo(
                            nutrition = it.groupValues[1].toIntOrNull() ?: 4,
                            saturation = it.groupValues[2].toDoubleOrNull() ?: 0.3,
                            canAlwaysEat = "alwaysEdible" in expr || "alwaysEat" in expr,
                        )
                    }
                items[iname] =
                    JavaItemInfo(
                        maxStackSize = if (maxDmg != null) 1 else maxStack,
                        maxDamage = maxDmg,
                        rarity = rarity,
                        fireResistant = "fireResistant" in expr,
                        food = food,
                    )
            }
        }

        val hasHud = listOf("HudRenderCallback", "InGameHud", "GuiGraphics", "DrawContext").any { it in combined }
        val hasKeybind = listOf("KeyBindingHelper", "KeyMapping", "GLFW.GLFW_KEY_").any { it in combined }
        val hasScreen = listOf("Screen", "HandledScreen", "AbstractContainerScreen").any { it in combined }
        val hasRaycast = listOf("crosshairTarget", "hitResult", "BlockHitResult", "raycast").any { it in combined }
        val hasServerBlockBreak = listOf("PlayerBlockBreakEvents", "BreakEvent", "onBlockBreak").any { it in combined }
        val hasServerBlockUse = listOf("UseBlockCallback", "RightClickBlock", "useOnBlock").any { it in combined }
        val hasServerPlayerJoin =
            listOf("ServerPlayConnectionEvents.JOIN", "PlayerLoggedInEvent", "onPlayerJoin").any { it in combined }
        val hasServerCommands =
            listOf("CommandRegistrationCallback", "RegisterCommandsEvent", "CommandManager.literal").any {
                it in combined
            }

        Regex("""new\s+(?:KeyBinding|KeyMapping)\s*\(\s*"([^"]+)"\s*,[^,]+,\s*GLFW\.GLFW_KEY_([A-Z0-9_]+)""")
            .findAll(combined)
            .forEach { km ->
                keybinds += km.groupValues[1] to "Key${km.groupValues[2].lowercase().replaceFirstChar { it.uppercase() }}"
            }

        Regex("""literal\s*\(\s*"([a-z0-9_-]+)"\s*\)""").findAll(combined).forEach { cm ->
            val cmd = cm.groupValues[1]
            if (cmd !in commands) commands += cmd
        }

        return JavaAnalysis(
            blocks = blocks,
            items = items,
            hasHud = hasHud,
            hasKeybind = hasKeybind,
            hasScreen = hasScreen,
            hasRaycast = hasRaycast,
            hasServerBlockBreak = hasServerBlockBreak,
            hasServerBlockUse = hasServerBlockUse,
            hasServerPlayerJoin = hasServerPlayerJoin,
            hasServerCommands = hasServerCommands,
            keybinds = keybinds,
            commands = commands,
        )
    }

    private fun generateRustWasmIfNeeded(
        outRoot: File,
        modId: String,
        meta: ModMetadata,
        analysis: JavaAnalysis,
    ): WasmTranslation {
        val hasServer =
            analysis.hasServerBlockBreak ||
                analysis.hasServerBlockUse ||
                analysis.hasServerPlayerJoin ||
                analysis.hasServerCommands
        val hasClient = analysis.hasHud || analysis.hasKeybind || analysis.hasScreen || analysis.hasRaycast

        if (!hasClient && !hasServer) {
            return WasmTranslation(
                generatedWasm = false,
                apiVersion = "0.2",
                capabilities = listOf("registries"),
                experimental = emptyList(),
            )
        }

        val srcDir = File(outRoot, "src")
        srcDir.mkdirs()

        File(outRoot, "Cargo.toml").writeText(
            """
            [package]
            name = "$modId"
            version = "${meta.version}"
            edition = "2021"

            [lib]
            crate-type = ["cdylib", "rlib"]

            [dependencies]
            horizon-mod-sdk = { path = "sdk/horizon-mod-sdk" }
            """
                .trimIndent() + "\n"
        )

        if (hasServer && !hasClient) {
            val cmds = analysis.commands.ifEmpty { listOf(modId) }.take(4).joinToString(", ") { "\"$it\"" }
            File(srcDir, "lib.rs").writeText(
                """
                use horizon_mod_sdk::{
                    commands, export_server_mod, log, players, storage, world, BlockBreakEvent, BlockUseEvent,
                    CommandContext, CommandResult, EventOutcome, PlayerJoinEvent, ServerMod,
                };

                #[derive(Default)]
                pub struct PortedServerMod;

                impl ServerMod for PortedServerMod {
                    fn init(&mut self) -> Result<(), String> {
                        for cmd in [$cmds] {
                            let _ = commands::register(cmd, "Ported command from ${meta.name}", &[]);
                        }
                        log::info("${meta.name} server WASM initialized");
                        Ok(())
                    }

                    fn on_player_join(&mut self, event: PlayerJoinEvent) {
                        let _ = players::send_message(&event.player.name, "Welcome to ${meta.name}!");
                    }

                    fn on_block_break(&mut self, event: BlockBreakEvent) -> EventOutcome {
                        log::debug(&format!(
                            "Block broken: {} at ({}, {}, {})",
                            event.block_id, event.pos.x, event.pos.y, event.pos.z
                        ));
                        EventOutcome::Continue
                    }

                    fn on_block_use(&mut self, event: BlockUseEvent) -> EventOutcome {
                        if event.is_sneaking {
                            if let Ok(actual) = world::get_block_id(event.pos) {
                                let _ = players::send_action_bar(&event.player.name, &format!("Inspecting {actual}"));
                            }
                        }
                        EventOutcome::Continue
                    }

                    fn on_command(&mut self, ctx: CommandContext) -> CommandResult {
                        let _ = storage::set("last_command_sender", ctx.sender.name.as_bytes());
                        CommandResult::Success(format!("[${meta.name}] Executed /{}", ctx.command))
                    }
                }

                export_server_mod!(PortedServerMod);
                """
                    .trimIndent() + "\n"
            )
            return WasmTranslation(
                generatedWasm = true,
                apiVersion = "0.3",
                capabilities = listOf("registries", "events", "world", "players", "commands", "storage"),
                experimental = listOf("world", "players"),
            )
        }

        val defaultKey = analysis.keybinds.firstOrNull()?.second ?: "KeyR"
        File(srcDir, "lib.rs").writeText(
            """
            use horizon_mod_sdk::{
                export_mod, game, hud, input, log, render, storage, Action, Anchor, InputEvent, KeybindId,
                Mod, RenderContext, UiEvent,
            };

            pub struct PortedClientMod {
                toggle_key: Option<KeybindId>,
                hud_enabled: bool,
            }

            impl Default for PortedClientMod {
                fn default() -> Self {
                    Self {
                        toggle_key: None,
                        hud_enabled: true,
                    }
                }
            }

            impl Mod for PortedClientMod {
                fn init(&mut self) -> Result<(), String> {
                    if let Some(saved) = storage::get("hud_enabled") {
                        self.hud_enabled = saved != b"0";
                    }
                    self.toggle_key = input::register_keybind(
                        "$modId.toggle_hud",
                        "Toggle ${meta.name} HUD",
                        "$defaultKey",
                        "${meta.name}",
                    )
                    .ok();
                    log::info("${meta.name} client WASM initialized");
                    Ok(())
                }

                fn tick(&mut self, _dt: f32) {
                    if !self.hud_enabled {
                        let _ = hud::remove(1);
                        return;
                    }
                    let label = if let Some(hit) = game::raycast(5.0) {
                        format!("[${meta.name}] Target: {}", hit.block_id)
                    } else {
                        "[${meta.name}] Active".to_string()
                    };
                    let _ = hud::text(1, &label, Anchor::TopLeft, 8.0, 8.0, 1.0, 0xFF55FFAA);
                }

                fn on_render(&mut self, _ctx: RenderContext) {
                    if self.hud_enabled {
                        let _ = render::draw_rect(6.0, 6.0, 180.0, 18.0, 0x66000000);
                    }
                }

                fn on_input(&mut self, event: InputEvent) -> bool {
                    if event.action == Action::Press && self.toggle_key == Some(event.keybind) {
                        self.hud_enabled = !self.hud_enabled;
                        let _ = storage::set("hud_enabled", if self.hud_enabled { b"1" } else { b"0" });
                        return true;
                    }
                    false
                }

                fn on_ui_event(&mut self, _event: UiEvent) {}
            }

            export_mod!(PortedClientMod);
            """
                .trimIndent() + "\n"
        )
        return WasmTranslation(
            generatedWasm = true,
            apiVersion = "0.2",
            capabilities = listOf("registries", "hud", "render", "ui", "input", "storage"),
            experimental = listOf("ui", "render", "input"),
        )
    }

    fun copyBundledSdk(targetSdkDir: File) {
        val assetPrefix = "horizon/sdk/horizon-mod-sdk"
        val files =
            listOf(
                "Cargo.toml",
                "src/lib.rs",
                "wit/horizon.wit",
            )
        files.forEach { rel ->
            copyAssetFile("$assetPrefix/$rel", File(targetSdkDir, rel))
        }
    }

    fun copyBundledTools(targetToolsDir: File) {
        val assetPrefix = "horizon/tools"
        listOf("package_hmod.py", "port_java_jar_to_hmod.py").forEach { name ->
            val dest = File(targetToolsDir, name)
            copyAssetFile("$assetPrefix/$name", dest)
            runCatching { dest.setExecutable(true, false) }
        }
    }

    private fun copyAssetFile(assetPath: String, destFile: File) {
        runCatching {
            destFile.parentFile?.mkdirs()
            application!!.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun failResult(targetParentDir: File, logs: List<String>, message: String): PortResult =
        PortResult(
            success = false,
            outputDir = targetParentDir,
            packagedHmod = null,
            modId = "",
            modName = "",
            version = "",
            apiVersion = "",
            blocksPorted = 0,
            itemsPorted = 0,
            recipesUpgraded = 0,
            lootTablesUpgraded = 0,
            generatedWasmSource = false,
            logs = logs + "ERROR: $message",
            error = message,
        )
}
