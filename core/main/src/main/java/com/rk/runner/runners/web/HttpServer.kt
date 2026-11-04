package com.rk.runner.runners.web

import android.content.Context
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.amoled
import com.rk.utils.isDarkTheme
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.net.URLConnection
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking

class HttpServer(
    val context: Context,
    port: Int,
    val root: FileObject,
    val serveHook: ((FileObject, IHTTPSession) -> Response?)? = null,
) : NanoHTTPD(port) {
    init {
        start()
    }

    override fun useGzipWhenAccepted(r: Response?): Boolean {
        return false
    }

    private fun resolveMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js", "mjs", "cjs" -> "application/javascript; charset=utf-8"
            "json", "map" -> "application/json; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "wasm" -> "application/wasm"
            "xml" -> "application/xml; charset=utf-8"
            "txt", "md", "markdown" -> "text/plain; charset=utf-8"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        }
    }

    private fun resolveRelativePath(base: FileObject, rawUri: String): FileObject? {
        val decoded = runCatching { URLDecoder.decode(rawUri, "UTF-8") }.getOrDefault(rawUri)
        val segments = decoded.trimStart('/').split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.any { it == ".." }) return null
        var current: FileObject = base
        for (seg in segments) {
            current = current.getChildForName(seg)
        }
        return current
    }

    override fun serve(session: IHTTPSession?): Response {
        return runBlocking {
            val uri = session?.uri ?: "/"

            if (root.isFile()) {
                if (!root.exists()) return@runBlocking notFoundError()
                return@runBlocking serveFile(root)
            }

            var file = resolveRelativePath(root, uri) ?: return@runBlocking forbiddenError(root)
            if (file.isDirectory()) {
                file = file.getChildForName("index.html")
            }

            // Hook override
            if (session != null) {
                serveHook?.invoke(file, session)?.let {
                    return@runBlocking it
                }
            }

            if (!file.exists()) return@runBlocking notFoundError()

            return@runBlocking serveFile(file)
        }
    }

    private suspend fun serveFile(file: FileObject): Response {
        return if (Settings.inject_eruda) {
            serveFileWithEruda(file)
        } else {
            serveFileWithoutEruda(file)
        }
    }

    private suspend fun serveFileWithEruda(file: FileObject): Response {
        return try {
            val mime = resolveMimeType(file.getName())
            if (!mime.startsWith(MIME_HTML)) {
                return serveFileWithoutEruda(file)
            }

            val darkTheme = isDarkTheme(context)
            val erudaTheme = if (amoled.value && darkTheme) "AMOLED" else if (darkTheme) "Dark" else "Light"
            val erudaScript =
                """
                <!-- Injected code, this is not present in original code -->
                <script src="https://cdn.jsdelivr.net/npm/eruda"></script>
                <script>eruda.init({ theme: '$erudaTheme' });</script>
                <!-- Injected code, this is not present in original code -->
                """
                    .trimIndent()

            val html = file.getInputStream().bufferedReader().use { it.readText() }
            val injected =
                if (html.contains("<head>", ignoreCase = true)) {
                    html.replaceFirst("<head>", "<head>$erudaScript", ignoreCase = true)
                } else {
                    erudaScript + html
                }

            newFixedLengthResponse(Status.OK, mime, injected)
        } catch (_: SecurityException) {
            forbiddenError(file)
        } catch (e: Exception) {
            internalError(e)
        }
    }

    private suspend fun serveFileWithoutEruda(file: FileObject): Response {
        return try {
            newFixedLengthResponse(
                Status.OK,
                resolveMimeType(file.getName()),
                file.getInputStream(),
                file.length(),
            )
        } catch (_: SecurityException) {
            forbiddenError(file)
        } catch (e: Exception) {
            internalError(e)
        }
    }

    private fun notFoundError(): Response = newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "404 Not found")

    private fun forbiddenError(file: FileObject): Response =
        newFixedLengthResponse(Status.FORBIDDEN, "text/plain", "403 Forbidden: Cannot read file ${file.getName()}")

    private fun internalError(e: Exception): Response =
        newFixedLengthResponse(
            Status.INTERNAL_ERROR,
            "text/plain",
            "500 Internal server error: ${e.localizedMessage ?: strings.unknown.getString()}",
        )
}
