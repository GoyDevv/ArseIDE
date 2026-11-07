package com.goydevv.arseide.horizon

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One-click `.hmod` and `.hnmod` packager for Arse IDE.
 *
 * Packages `horizon-mod.toml`, `mod.wasm` (if present), `assets/`, and `data/` into `<id>.hmod`
 * (ZIP format, deflate compression, no parent wrapper directory) after running [HorizonModValidator].
 */
object HorizonModPackager {

    data class PackageResult(
        val success: Boolean,
        val archiveFile: File?,
        val report: HorizonModValidator.ValidationReport,
        val message: String,
    )

    suspend fun packageProject(
        projectDir: File,
        outputFile: File? = null,
        autoFixMissingDispatches: Boolean = true,
    ): PackageResult =
        withContext(Dispatchers.IO) {
            if (autoFixMissingDispatches) {
                ensureItemDispatches(projectDir)
            }

            val report = HorizonModValidator.validate(projectDir)
            if (!report.isValid) {
                return@withContext PackageResult(
                    success = false,
                    archiveFile = null,
                    report = report,
                    message = "Validation failed:\n• " + report.errors.joinToString("\n• "),
                )
            }

            val ext = if (report.isNativeMixin) "hnmod" else "hmod"
            val targetZip = outputFile ?: File(projectDir, "${report.modId}.$ext")
            targetZip.parentFile?.mkdirs()

            runCatching {
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZip))).use { zos ->
                        zos.setLevel(Deflater.DEFAULT_COMPRESSION)

                        if (report.isNativeMixin) {
                            addFileToZip(zos, File(projectDir, "horizon-mixin.toml"), "horizon-mixin.toml")
                            listOf("libraries", "src").forEach { folder ->
                                val dir = File(projectDir, folder)
                                if (dir.isDirectory) addDirToZip(zos, projectDir, dir)
                            }
                        } else {
                            val manifestFile = File(projectDir, "horizon-mod.toml")
                            addFileToZip(zos, manifestFile, "horizon-mod.toml")

                            val parsed = HorizonModValidator.parseSimpleToml(manifestFile.readText())
                            val entryName = parsed.strings["entry"]?.ifBlank { "mod.wasm" } ?: "mod.wasm"
                            val wasmFile = findCompiledWasm(projectDir, entryName)
                            if (wasmFile != null && wasmFile.isFile) {
                                addFileToZip(zos, wasmFile, entryName)
                            }

                            listOf("assets", "data").forEach { folder ->
                                val dir = File(projectDir, folder)
                                if (dir.isDirectory) addDirToZip(zos, projectDir, dir)
                            }
                        }
                    }
                }
                .fold(
                    onSuccess = {
                        PackageResult(
                            success = true,
                            archiveFile = targetZip,
                            report = report,
                            message =
                                "Packaged ${targetZip.name} (${targetZip.length()} bytes) — " +
                                    "${report.declaredBlocks} block(s), ${report.declaredItems} item(s).",
                        )
                    },
                    onFailure = { err ->
                        PackageResult(
                            success = false,
                            archiveFile = null,
                            report = report,
                            message = "Packaging failed: ${err.message}",
                        )
                    },
                )
        }

    /**
     * Automatically generates missing 26.3 client item model dispatch files at
     * `assets/<id>/items/<name>.json` for any declared block (`item = true`) or item.
     */
    fun ensureItemDispatches(projectDir: File): Int {
        val modToml = File(projectDir, "horizon-mod.toml")
        if (!modToml.isFile) return 0
        val parsed = HorizonModValidator.parseSimpleToml(modToml.readText())
        val modId = parsed.strings["id"]?.trim().orEmpty()
        if (modId.isEmpty()) return 0

        var generated = 0
        val dispatchDir = File(projectDir, "assets/$modId/items")
        val itemModelsDir = File(projectDir, "assets/$modId/models/item")
        val blockDir = File(projectDir, "data/$modId/horizon/block")
        val itemDir = File(projectDir, "data/$modId/horizon/item")

        if (blockDir.isDirectory) {
            blockDir.listFiles { f -> f.isFile && f.extension == "json" }?.forEach { bfile ->
                val name = bfile.nameWithoutExtension.lowercase()
                val dispatchFile = File(dispatchDir, "$name.json")
                if (!dispatchFile.exists()) {
                    dispatchFile.parentFile?.mkdirs()
                    val modelTarget =
                        if (File(itemModelsDir, "$name.json").exists()) "$modId:item/$name"
                        else "$modId:block/$name"
                    dispatchFile.writeText(
                        """
                        {
                          "model": {
                            "type": "minecraft:model",
                            "model": "$modelTarget"
                          }
                        }
                        """
                            .trimIndent() + "\n"
                    )
                    generated++
                }
            }
        }

        if (itemDir.isDirectory) {
            itemDir.listFiles { f -> f.isFile && f.extension == "json" }?.forEach { ifile ->
                val name = ifile.nameWithoutExtension.lowercase()
                val dispatchFile = File(dispatchDir, "$name.json")
                if (!dispatchFile.exists()) {
                    dispatchFile.parentFile?.mkdirs()
                    dispatchFile.writeText(
                        """
                        {
                          "model": {
                            "type": "minecraft:model",
                            "model": "$modId:item/$name"
                          }
                        }
                        """
                            .trimIndent() + "\n"
                    )
                    generated++
                }
            }
        }
        return generated
    }

    private fun findCompiledWasm(projectDir: File, entryName: String): File? {
        val direct = File(projectDir, entryName)
        if (direct.isFile) return direct
        val releaseDir = File(projectDir, "target/wasm32-unknown-unknown/release")
        if (releaseDir.isDirectory) {
            return releaseDir
                .listFiles { f -> f.isFile && f.extension == "wasm" }
                ?.maxByOrNull { it.lastModified() }
        }
        return null
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, arcName: String) {
        if (!file.isFile) return
        val entry = ZipEntry(arcName.replace('\\', '/'))
        entry.time = file.lastModified()
        zos.putNextEntry(entry)
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun addDirToZip(zos: ZipOutputStream, rootDir: File, folder: File) {
        folder
            .walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(rootDir).invariantSeparatorsPath }
            .forEach { file ->
                val rel = file.relativeTo(rootDir).invariantSeparatorsPath.lowercase()
                addFileToZip(zos, file, rel)
            }
    }
}
