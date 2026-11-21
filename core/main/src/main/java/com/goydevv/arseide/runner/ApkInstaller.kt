package com.goydevv.arseide.runner

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {

    fun findApk(projectRealPath: String): File? {
        val roots =
            listOf(
                File(projectRealPath, "app/build/outputs/apk"),
                File(projectRealPath, "build/outputs/apk"),
            )
        val allApks =
            roots
                .filter { it.isDirectory }
                .flatMap { root -> root.walkTopDown().filter { f -> f.isFile && f.extension.equals("apk", true) }.toList() }
        if (allApks.isEmpty()) return null
        val newest = allApks.maxByOrNull { it.lastModified() } ?: return null
        val recentSigned =
            allApks
                .filter { it.name.endsWith("-signed.apk", ignoreCase = true) && newest.lastModified() - it.lastModified() <= 15_000L }
                .maxByOrNull { it.lastModified() }
        return recentSigned ?: newest
    }

    fun install(context: Context, projectRealPath: String): Boolean {
        return runCatching {
                val apk = findApk(projectRealPath) ?: return false
                val dest = File(context.getExternalFilesDir(null), "run-install.apk")
                apk.copyTo(dest, overwrite = true)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
                val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(intent)
                true
            }
            .getOrElse {
                it.printStackTrace()
                false
            }
    }
}
