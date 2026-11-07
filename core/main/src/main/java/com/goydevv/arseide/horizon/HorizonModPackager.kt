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
 * Packages a Horizon Native Mod project (`horizon-native.json` + `mixins/<id>/...`) into a
 * validated `<id>.hnmod` bundle conforming to `crates/horizon-mods/src/index/native.rs`.
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
    ): PackageResult =
        withContext(Dispatchers.IO) {
            val report = HorizonModValidator.validateProjectDir(projectDir)
            if (!report.isValid) {
                return@withContext PackageResult(
                    success = false,
                    archiveFile = null,
                    report = report,
                    message = "Validation failed:\n• " + report.errors.joinToString("\n• "),
                )
            }

            val modId = report.modId
            val targetZip = outputFile ?: File(projectDir, "$modId.hnmod")
            targetZip.parentFile?.mkdirs()

            val manifestFile = File(projectDir, HorizonModValidator.MANIFEST_NAME)
            val mixinRoot = File(projectDir, "mixins/$modId")

            runCatching {
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZip))).use { zos ->
                        zos.setLevel(Deflater.DEFAULT_COMPRESSION)
                        addFileToZip(zos, manifestFile, HorizonModValidator.MANIFEST_NAME)

                        if (mixinRoot.isDirectory) {
                            mixinRoot
                                .walkTopDown()
                                .onEnter { it.name !in setOf("target", ".git", "__pycache__", ".gradle", ".idea") }
                                .filter { it.isFile }
                                .sortedBy { it.relativeTo(mixinRoot).invariantSeparatorsPath }
                                .forEach { file ->
                                    val rest = file.relativeTo(mixinRoot).invariantSeparatorsPath
                                    if (HorizonModValidator.isAllowedMixinPath(rest)) {
                                        addFileToZip(zos, file, "mixins/$modId/$rest")
                                    }
                                }
                        }
                    }

                    // Double-check the packaged archive against Bundle::read rules
                    val archiveErrors = HorizonModValidator.validateHnmodArchive(targetZip)
                    if (archiveErrors.isNotEmpty()) {
                        throw IllegalStateException(archiveErrors.joinToString("; "))
                    }
                }
                .fold(
                    onSuccess = {
                        PackageResult(
                            success = true,
                            archiveFile = targetZip,
                            report = report,
                            message =
                                "Packaged ${targetZip.name} (${targetZip.length()} bytes, ${report.fileCount} files, hooks API ${report.hooksApi ?: 1}).",
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

    private fun addFileToZip(zos: ZipOutputStream, file: File, arcName: String) {
        if (!file.isFile) return
        val entry = ZipEntry(arcName.replace('\\', '/'))
        entry.time = file.lastModified()
        zos.putNextEntry(entry)
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }
}
