package com.goydevv.arseide.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.goydevv.arseide.XedConstants
import com.goydevv.arseide.resources.drawables
import com.goydevv.arseide.settings.Preference
import com.goydevv.arseide.utils.application
import com.goydevv.arseide.utils.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

data class GitHubReleaseInfo(
    val tagName: String,
    val normalizedVersion: String,
    val title: String,
    val markdownBody: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val prerelease: Boolean,
)

object ChangelogInfo {

    fun installedVersionName(): String {
        val raw =
            runCatching {
                    val app = application ?: return "5.0.0"
                    app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "5.0.0"
                }
                .getOrDefault("5.0.0")
        return normalizeVersion(raw)
    }

    fun normalizeVersion(raw: String): String =
        raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-DEBUG")
            .substringBefore("+")
            .trim()

    /**
     * Semantic version comparison:
     * returns < 0 if [v1] < [v2], 0 if equal, > 0 if [v1] > [v2].
     */
    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = normalizeVersion(v1).substringBefore("-")
        val clean2 = normalizeVersion(v2).substringBefore("-")
        val parts1 = clean1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val a = parts1.getOrElse(i) { 0 }
            val b = parts2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun seenKey(version: String) = "changelog_seen_${normalizeVersion(version)}"

    fun isSeen(version: String): Boolean = Preference.getBoolean(seenKey(version), false)

    fun markSeen(version: String) = Preference.setBoolean(seenKey(version), true)

    /**
     * Fetches real-time releases from `https://api.github.com/repos/GoyDevv/ArseIDE/releases`.
     */
    suspend fun fetchReleases(): List<GitHubReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                    val request =
                        Request.Builder()
                            .url(XedConstants.GITHUB_RELEASES_API)
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", "ArseIDE-Android")
                            .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext emptyList()
                        val bodyStr = response.body.string()
                        val arr = JSONArray(bodyStr)
                        val list = mutableListOf<GitHubReleaseInfo>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            if (obj.optBoolean("draft", false)) continue
                            val tag = obj.optString("tag_name", "").trim()
                            if (tag.isEmpty()) continue
                            val norm = normalizeVersion(tag)
                            val name = obj.optString("name", "").trim().ifEmpty { tag }
                            val body = obj.optString("body", "").trim()
                            val htmlUrl = obj.optString("html_url", XedConstants.GITHUB_RELEASES_URL)
                            val prerelease = obj.optBoolean("prerelease", false)

                            var apkUrl: String? = null
                            val assets = obj.optJSONArray("assets")
                            if (assets != null) {
                                for (j in 0 until assets.length()) {
                                    val asset = assets.optJSONObject(j) ?: continue
                                    val assetName = asset.optString("name", "")
                                    val dl = asset.optString("browser_download_url", "")
                                    if (assetName.endsWith(".apk", ignoreCase = true) && dl.isNotBlank()) {
                                        apkUrl = dl
                                        break
                                    }
                                }
                            }

                            list +=
                                GitHubReleaseInfo(
                                    tagName = tag,
                                    normalizedVersion = norm,
                                    title = name,
                                    markdownBody = body,
                                    htmlUrl = htmlUrl,
                                    apkDownloadUrl = apkUrl,
                                    prerelease = prerelease,
                                )
                        }
                        list
                    }
                }
                .getOrDefault(emptyList())
        }
}

private sealed interface ReleaseDialogMode {
    data object None : ReleaseDialogMode
    data class WhatIsNew(val release: GitHubReleaseInfo) : ReleaseDialogMode
    data class UpdateAvailable(val installedVersion: String, val latestRelease: GitHubReleaseInfo) : ReleaseDialogMode
}

/**
 * Evaluates live GitHub Releases from `https://github.com/GoyDevv/ArseIDE`:
 * - If a newer release exists on GitHub than the installed version -> shows the Update Available dialog
 *   with the release's Markdown changelog and direct download/release link.
 * - Else if the installed version exists in GitHub Releases and hasn't been dismissed -> shows the
 *   What's New dialog with that version's Markdown changelog from GitHub Releases.
 * - If the version is NOT present in GitHub Releases -> shows NO dialog at all.
 */
@Composable
fun ReleaseChangelogGate() {
    var dialogMode by remember { mutableStateOf<ReleaseDialogMode>(ReleaseDialogMode.None) }
    var dismissedThisSession by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(dismissedThisSession) {
        if (dismissedThisSession) return@LaunchedEffect
        val installed = ChangelogInfo.installedVersionName()
        val releases = ChangelogInfo.fetchReleases()
        if (releases.isEmpty()) {
            dialogMode = ReleaseDialogMode.None
            return@LaunchedEffect
        }

        val latestStable = releases.firstOrNull { !it.prerelease } ?: releases.first()
        if (ChangelogInfo.compareVersions(installed, latestStable.normalizedVersion) < 0) {
            dialogMode = ReleaseDialogMode.UpdateAvailable(installed, latestStable)
            return@LaunchedEffect
        }

        val matchingRelease =
            releases.firstOrNull { rel ->
                rel.normalizedVersion.equals(installed, ignoreCase = true) ||
                    ChangelogInfo.compareVersions(installed, rel.normalizedVersion) == 0
            }

        dialogMode =
            if (matchingRelease != null && matchingRelease.markdownBody.isNotBlank() && !ChangelogInfo.isSeen(installed)) {
                ReleaseDialogMode.WhatIsNew(matchingRelease)
            } else {
                ReleaseDialogMode.None
            }
    }

    if (dismissedThisSession) return

    when (val mode = dialogMode) {
        is ReleaseDialogMode.None -> Unit
        is ReleaseDialogMode.WhatIsNew -> {
            ChangelogDialog(
                release = mode.release,
                onClose = {
                    dismissedThisSession = true
                    dialogMode = ReleaseDialogMode.None
                },
            )
        }
        is ReleaseDialogMode.UpdateAvailable -> {
            ReleaseUpdateDialog(
                installedVersion = mode.installedVersion,
                latestRelease = mode.latestRelease,
                onDismiss = {
                    dismissedThisSession = true
                    dialogMode = ReleaseDialogMode.None
                },
            )
        }
    }
}

@Composable
fun ChangelogDialog(
    release: GitHubReleaseInfo,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var dontShowAgain by remember { mutableStateOf(true) }

    fun close() {
        if (dontShowAgain) ChangelogInfo.markSeen(release.normalizedVersion)
        onClose()
    }

    AlertDialog(
        onDismissRequest = { close() },
        icon = { Icon(painterResource(drawables.bolt), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "What's New in ${release.tagName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (release.title.isNotBlank() && release.title != release.tagName) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = release.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkdownReleaseNotes(markdown = release.markdownBody)

                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { dontShowAgain = !dontShowAgain }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Don't show again for ${release.tagName}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri()))
                    }
                }
            ) {
                Text("View on GitHub")
            }
        },
        confirmButton = {
            Button(onClick = { close() }) {
                Text("Got It")
            }
        },
    )
}

@Composable
fun ReleaseUpdateDialog(
    installedVersion: String,
    latestRelease: GitHubReleaseInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(drawables.bolt), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Update Available (${latestRelease.tagName})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = "v$installedVersion → ${latestRelease.tagName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (latestRelease.markdownBody.isNotBlank()) {
                    MarkdownReleaseNotes(markdown = latestRelease.markdownBody)
                } else {
                    Text(
                        text = "A newer release of Arse IDE is available on GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetUrl = latestRelease.apkDownloadUrl ?: latestRelease.htmlUrl.ifBlank { XedConstants.GITHUB_RELEASES_URL }
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, targetUrl.toUri()))
                    }
                    onDismiss()
                }
            ) {
                Text("Update Now")
            }
        },
    )
}

/**
 * Renders GitHub Release Markdown (`#`, `##`, `###`, `-`/`*` bullets, numbered lists,
 * `> blockquotes`, ```code blocks```, `**bold**`, `*italic*`, `` `code` ``, and `[links](url)`)
 * using Material 3 typography and surfaces.
 */
@Composable
fun MarkdownReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val lines = remember(markdown) { markdown.replace("\r\n", "\n").lines() }
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            when {
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(2.dp))
                    i++
                }

                trimmed.startsWith("```") -> {
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines += lines[i]
                        i++
                    }
                    if (i < lines.size) i++ // skip closing ```
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(scheme.surfaceContainerHighest)
                                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                    ) {
                        Text(
                            text = codeLines.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = scheme.onSurface,
                        )
                    }
                }

                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = scheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    i++
                }

                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# ").trim(), scheme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                    i++
                }

                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## ").trim(), scheme),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                    i++
                }

                trimmed.startsWith("### ") || trimmed.startsWith("#### ") -> {
                    val content = trimmed.replaceFirst(Regex("^#{3,4}\\s+"), "")
                    Text(
                        text = parseInlineMarkdown(content, scheme),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    i++
                }

                trimmed.startsWith("> ") -> {
                    val quote = trimmed.removePrefix("> ").trim()
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.secondaryContainer.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.width(3.dp)
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(scheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        MarkdownInlineText(
                            annotated = parseInlineMarkdown(quote, scheme),
                            onLinkClick = { url ->
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                            },
                        )
                    }
                    i++
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    val itemText = trimmed.substring(2).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            MarkdownInlineText(
                                annotated = parseInlineMarkdown(itemText, scheme),
                                onLinkClick = { url ->
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                                },
                            )
                        }
                    }
                    i++
                }

                Regex("^\\d+\\.\\s+.*").matches(trimmed) -> {
                    val num = trimmed.substringBefore(".") + "."
                    val itemText = trimmed.substringAfter(".").trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = num,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            MarkdownInlineText(
                                annotated = parseInlineMarkdown(itemText, scheme),
                                onLinkClick = { url ->
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                                },
                            )
                        }
                    }
                    i++
                }

                else -> {
                    MarkdownInlineText(
                        annotated = parseInlineMarkdown(trimmed, scheme),
                        onLinkClick = { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        },
                    )
                    i++
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MarkdownInlineText(
    annotated: AnnotatedString,
    onLinkClick: (String) -> Unit,
) {
    val style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    ClickableText(
        text = annotated,
        style = style,
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { onLinkClick(it.item) }
        },
    )
}

private fun parseInlineMarkdown(
    text: String,
    scheme: androidx.compose.material3.ColorScheme,
): AnnotatedString =
    buildAnnotatedString {
        val tokenRegex =
            Regex("""(\[([^\]]+)\]\(([^)]+)\))|(`([^`]+)`)|(\*\*([^*]+)\*\*)|(__([^_]+)__)|(\*([^*]+)\*)""")
        var cursor = 0
        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            when {
                match.groups[1] != null -> {
                    val label = match.groups[2]?.value ?: ""
                    val url = match.groups[3]?.value ?: ""
                    pushStringAnnotation(tag = "URL", annotation = url)
                    pushStyle(
                        SpanStyle(
                            color = scheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                        )
                    )
                    append(label)
                    pop()
                    pop()
                }
                match.groups[4] != null -> {
                    val code = match.groups[5]?.value ?: ""
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = scheme.surfaceContainerHighest,
                            color = scheme.primary,
                            fontSize = 12.sp,
                        )
                    )
                    append(" $code ")
                    pop()
                }
                match.groups[6] != null || match.groups[8] != null -> {
                    val boldText = match.groups[7]?.value ?: match.groups[9]?.value ?: ""
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }
                match.groups[10] != null -> {
                    val italicText = match.groups[11]?.value ?: ""
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
