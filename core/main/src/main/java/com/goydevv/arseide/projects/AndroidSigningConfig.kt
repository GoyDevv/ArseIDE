package com.goydevv.arseide.projects

import android.content.Context
import android.net.Uri
import com.goydevv.arseide.exec.ShellUtils
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.runner.ProjectRunner
import com.goydevv.arseide.settings.Preference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SigningMode(val id: String, val label: String, val description: String) {
    DEFAULT_ARSEIDE(
        "arseide_default",
        "ArseIDE Signing Key",
        "Auto-managed RSA-2048 PKCS12 signing key stored in your sandbox.",
    ),
    CUSTOM_KEYSTORE(
        "custom",
        "Custom Keystore",
        "Sign APKs using your own .jks or .keystore file and credentials.",
    ),
    UNSIGNED(
        "unsigned",
        "Gradle Default / Unsigned",
        "Use the project's Gradle signingConfig without ArseIDE override.",
    ),
}

data class SigningProfile(
    val mode: SigningMode,
    val keystorePath: String,
    val keyAlias: String,
    val storePassword: String,
    val keyPassword: String,
)

object AndroidSigningConfig {

    const val DEFAULT_KEYSTORE_SANDBOX_PATH = "/home/.arseide/signing/arseide-default.keystore"
    const val DEFAULT_ALIAS = "arseide"
    const val DEFAULT_PASSWORD = "arseide2026"

    private fun keyMode(root: File) = "android_sign_mode::${root.absolutePath}"
    private fun keyPath(root: File) = "android_sign_path::${root.absolutePath}"
    private fun keyAlias(root: File) = "android_sign_alias::${root.absolutePath}"
    private fun keyStorePass(root: File) = "android_sign_store_pass::${root.absolutePath}"
    private fun keyKeyPass(root: File) = "android_sign_key_pass::${root.absolutePath}"

    fun signingDir(): File = File(sandboxHomeDir(), ".arseide/signing").apply { mkdirs() }

    fun defaultKeystoreFile(): File = File(signingDir(), "arseide-default.keystore")

    fun hasDefaultKeystore(): Boolean = defaultKeystoreFile().let { it.isFile && it.length() > 0L }

    fun load(root: File): SigningProfile {
        val modeName = Preference.getString(keyMode(root), SigningMode.DEFAULT_ARSEIDE.name)
        val mode = runCatching { SigningMode.valueOf(modeName) }.getOrDefault(SigningMode.DEFAULT_ARSEIDE)
        return SigningProfile(
            mode = mode,
            keystorePath = Preference.getString(keyPath(root), ""),
            keyAlias = Preference.getString(keyAlias(root), ""),
            storePassword = Preference.getString(keyStorePass(root), ""),
            keyPassword = Preference.getString(keyKeyPass(root), ""),
        )
    }

    fun save(root: File, profile: SigningProfile) {
        Preference.setString(keyMode(root), profile.mode.name)
        Preference.setString(keyPath(root), profile.keystorePath.trim())
        Preference.setString(keyAlias(root), profile.keyAlias.trim())
        Preference.setString(keyStorePass(root), profile.storePassword)
        Preference.setString(keyKeyPass(root), profile.keyPassword)
    }

    fun setMode(root: File, mode: SigningMode) {
        Preference.setString(keyMode(root), mode.name)
    }

    fun resolveForRunner(root: File): SigningProfile {
        val saved = load(root)
        return when (saved.mode) {
            SigningMode.DEFAULT_ARSEIDE ->
                SigningProfile(
                    mode = SigningMode.DEFAULT_ARSEIDE,
                    keystorePath = DEFAULT_KEYSTORE_SANDBOX_PATH,
                    keyAlias = DEFAULT_ALIAS,
                    storePassword = DEFAULT_PASSWORD,
                    keyPassword = DEFAULT_PASSWORD,
                )
            SigningMode.CUSTOM_KEYSTORE ->
                saved.copy(
                    keystorePath = toSandboxKeystorePath(saved.keystorePath),
                )
            SigningMode.UNSIGNED ->
                SigningProfile(
                    mode = SigningMode.UNSIGNED,
                    keystorePath = "",
                    keyAlias = "",
                    storePassword = "",
                    keyPassword = "",
                )
        }
    }

    fun toSandboxKeystorePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return ""
        val homePrefix = sandboxHomeDir().absolutePath
        if (trimmed.startsWith("$homePrefix/")) {
            return "/home" + trimmed.removePrefix(homePrefix)
        }
        return ProjectRunner.toSandboxPath(trimmed)
    }

    suspend fun importKeystoreFromUri(context: Context, uri: Uri, preferredName: String? = null): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseName =
                    preferredName?.takeIf { it.isNotBlank() }
                        ?: "custom-${System.currentTimeMillis()}.keystore"
                val safeName = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val target = File(signingDir(), safeName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                target
            }.getOrNull()
        }

    suspend fun ensureDefaultKeystore(): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (hasDefaultKeystore()) {
                return@withContext true to "ArseIDE Signing Key ready at $DEFAULT_KEYSTORE_SANDBOX_PATH"
            }
            generateKeystore(
                fileName = "arseide-default.keystore",
                alias = DEFAULT_ALIAS,
                storePassword = DEFAULT_PASSWORD,
                keyPassword = DEFAULT_PASSWORD,
                commonName = "ArseIDE",
                organization = "ArseIDE",
                validityYears = 25,
            )
        }

    suspend fun generateKeystore(
        fileName: String,
        alias: String,
        storePassword: String,
        keyPassword: String,
        commonName: String,
        organization: String,
        validityYears: Int = 25,
    ): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val cleanName =
                fileName.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_").let {
                    if (it.endsWith(".keystore") || it.endsWith(".jks")) it else "$it.keystore"
                }
            val cleanAlias = alias.trim().ifBlank { "release" }
            val cleanCn = commonName.trim().ifBlank { "ArseIDE Developer" }.replace("\"", "").replace(",", " ")
            val cleanOrg = organization.trim().ifBlank { "ArseIDE" }.replace("\"", "").replace(",", " ")
            val days = (validityYears.coerceIn(1, 50) * 365)
            val sandboxPath = "/home/.arseide/signing/$cleanName"

            val script =
                """
                mkdir -p "/home/.arseide/signing"
                if ! command -v keytool >/dev/null 2>&1; then
                  JVM_DIR=$(ls -d /usr/lib/jvm/java-*-openjdk* 2>/dev/null | head -1)
                  [ -n "${'$'}JVM_DIR" ] && export PATH="${'$'}JVM_DIR/bin:${'$'}PATH"
                fi
                if ! command -v keytool >/dev/null 2>&1; then
                  echo "keytool not found. Install JDK in Dependencies first."
                  exit 127
                fi
                rm -f "$sandboxPath"
                keytool -genkeypair -noprompt \
                  -keystore "$sandboxPath" \
                  -storetype PKCS12 \
                  -alias ${shellQuote(cleanAlias)} \
                  -keyalg RSA -keysize 2048 \
                  -validity $days \
                  -storepass ${shellQuote(storePassword)} \
                  -keypass ${shellQuote(keyPassword)} \
                  -dname ${shellQuote("CN=$cleanCn, OU=Mobile, O=$cleanOrg, L=Local, S=Local, C=US")} 2>&1
                """.trimIndent()

            val result = ShellUtils.runUbuntu(command = arrayOf("bash", "-lc", script), timeoutSeconds = 45L)
            if (result.exitCode == 0 && !result.timedOut) {
                true to sandboxPath
            } else {
                val msg = result.output.trim().ifBlank { "Failed to generate keystore (exit ${result.exitCode})." }
                false to msg
            }
        }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
