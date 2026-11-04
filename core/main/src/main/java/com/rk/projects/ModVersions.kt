package com.rk.projects

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resolves real, currently-published Fabric and Forge versions for a given Minecraft version, so the
 * generated mod projects actually resolve their dependencies instead of shipping guessed numbers.
 */
object ModVersions {

    /**
     * Fabric dependency versions for a Minecraft version.
     * [hasYarn] is true when official Yarn mappings exist for this version; when false, the scaffolder
     * uses `loom.officialMojangMappings()` so the project still builds cleanly.
     */
    data class FabricPins(
        val yarn: String,
        val loader: String,
        val fabricApi: String,
        val hasYarn: Boolean,
        val online: Boolean,
    )

    /**
     * Resolved Forge version paired with the Minecraft version it actually supports.
     * [exactMatch] is false if Forge has no build for the requested MC version and we fell back to a
     * known working MC + Forge pair.
     */
    data class ForgePin(
        val mcVersion: String,
        val forgeVersion: String,
        val exactMatch: Boolean,
    )

    private val KNOWN_FABRIC_YARN =
        mapOf(
            "1.21.4" to "1.21.4+build.8",
            "1.21.3" to "1.21.3+build.2",
            "1.21.1" to "1.21.1+build.3",
            "1.21" to "1.21+build.9",
            "1.20.6" to "1.20.6+build.3",
            "1.20.4" to "1.20.4+build.3",
            "1.20.2" to "1.20.2+build.4",
            "1.20.1" to "1.20.1+build.10",
            "1.19.4" to "1.19.4+build.2",
            "1.19.2" to "1.19.2+build.28",
            "1.18.2" to "1.18.2+build.4",
        )

    private val KNOWN_FABRIC_API =
        mapOf(
            "1.21.4" to "0.114.0+1.21.4",
            "1.21.3" to "0.114.0+1.21.3",
            "1.21.1" to "0.115.0+1.21.1",
            "1.21" to "0.102.0+1.21",
            "1.20.6" to "0.100.8+1.20.6",
            "1.20.4" to "0.97.2+1.20.4",
            "1.20.2" to "0.91.6+1.20.2",
            "1.20.1" to "0.92.2+1.20.1",
            "1.19.4" to "0.87.2+1.19.4",
            "1.19.2" to "0.77.0+1.19.2",
            "1.18.2" to "0.77.0+1.18.2",
        )

    private val KNOWN_FORGE_BUILDS =
        mapOf(
            "1.21.4" to "54.1.0",
            "1.21.3" to "53.0.23",
            "1.21.1" to "52.1.0",
            "1.21" to "51.0.33",
            "1.20.6" to "50.1.32",
            "1.20.4" to "49.1.20",
            "1.20.2" to "48.1.0",
            "1.20.1" to "47.3.0",
            "1.19.4" to "45.3.0",
            "1.19.2" to "43.4.0",
            "1.18.2" to "40.2.21",
            "1.16.5" to "36.2.42",
            "1.12.2" to "14.23.5.2860",
        )

    private fun httpGet(urlStr: String): String? =
        runCatching {
                val c = (URL(urlStr).openConnection() as HttpURLConnection)
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.requestMethod = "GET"
                c.setRequestProperty("Accept", "application/json")
                c.setRequestProperty("User-Agent", "Xed-Editor (project scaffolder)")
                val code = c.responseCode
                if (code !in 200..299) {
                    c.disconnect()
                    null
                } else {
                    c.inputStream.use { it.bufferedReader().readText() }.also { c.disconnect() }
                }
            }
            .getOrNull()

    /**
     * Real Fabric versions for [mc]:
     *  - yarn mappings + loader from Fabric's official meta API,
     *  - Fabric API from Modrinth (filtered to the game version),
     *  - Known-good offline fallbacks for common MC versions.
     */
    fun fabricPins(mc: String): FabricPins {
        var online = true
        var hasYarn = true

        val liveYarn =
            runCatching {
                    val body = httpGet("https://meta.fabricmc.net/v2/versions/yarn/$mc") ?: return@runCatching null
                    val arr = JSONArray(body)
                    if (arr.length() > 0) arr.getJSONObject(0).getString("version") else null
                }
                .getOrNull()

        val yarn =
            liveYarn
                ?: KNOWN_FABRIC_YARN[mc]
                ?: run {
                    online = false
                    hasYarn = false
                    "$mc+build.1"
                }

        val loader =
            runCatching {
                    val body = httpGet("https://meta.fabricmc.net/v2/versions/loader") ?: return@runCatching null
                    val arr = JSONArray(body)
                    var stable: String? = null
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        if (o.optBoolean("stable", false)) {
                            stable = o.getString("version")
                            break
                        }
                    }
                    stable ?: if (arr.length() > 0) arr.getJSONObject(0).getString("version") else null
                }
                .getOrNull()
                ?: run {
                    online = false
                    "0.16.10"
                }

        val api =
            runCatching {
                    val gv = URLEncoder.encode("[\"$mc\"]", "UTF-8")
                    val ld = URLEncoder.encode("[\"fabric\"]", "UTF-8")
                    val body =
                        httpGet("https://api.modrinth.com/v2/project/fabric-api/version?game_versions=$gv&loaders=$ld")
                            ?: return@runCatching null
                    val arr = JSONArray(body)
                    if (arr.length() > 0) arr.getJSONObject(0).getString("version_number") else null
                }
                .getOrNull()
                ?: KNOWN_FABRIC_API[mc]
                ?: run {
                    online = false
                    "0.114.0+$mc"
                }

        return FabricPins(yarn = yarn, loader = loader, fabricApi = api, hasYarn = hasYarn, online = online)
    }

    /**
     * Real Forge build for [mc] from the official promotions manifest (recommended, else latest),
     * falling back to [KNOWN_FORGE_BUILDS] when offline.
     */
    fun forgeBuild(mc: String): String? =
        runCatching {
                val body =
                    httpGet("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
                        ?: return@runCatching null
                val promos = JSONObject(body).getJSONObject("promos")
                val recommended = promos.optString("$mc-recommended")
                val latest = promos.optString("$mc-latest")
                when {
                    recommended.isNotBlank() -> recommended
                    latest.isNotBlank() -> latest
                    else -> null
                }
            }
            .getOrNull() ?: KNOWN_FORGE_BUILDS[mc]

    /**
     * Resolves a guaranteed-valid (Minecraft, Forge) version pair. If Forge has no build for [mc],
     * falls back to 1.20.1 / 47.3.0 with `exactMatch = false` so `minecraft_version` and `forge_version`
     * in `gradle.properties` never mismatch.
     */
    fun resolveForgePin(mc: String): ForgePin {
        val build = forgeBuild(mc)
        return if (build != null) {
            ForgePin(mcVersion = mc, forgeVersion = build, exactMatch = true)
        } else {
            ForgePin(mcVersion = "1.20.1", forgeVersion = "47.3.0", exactMatch = false)
        }
    }
}
