package com.goydevv.arseide.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.google.gson.JsonArray
import com.goydevv.arseide.color.toHex
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.settings.editor.rememberAppTypography
import com.goydevv.arseide.settings.theme.themes
import com.goydevv.arseide.utils.isDarkTheme
import com.goydevv.arseide.utils.toast
import java.util.Properties

val currentTheme = mutableStateOf<ThemeHolder?>(null)
val dynamicTheme = mutableStateOf(Settings.monet)
val amoled = mutableStateOf(Settings.amoled)

val LocalThemeHolder = staticCompositionLocalOf<ThemeHolder> { error("No ThemeHolder state provided") }

@Immutable
data class GlassColors(
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val borderTop: Color,
    val borderMid: Color,
    val borderBottom: Color,
    val specularHighlight: Color,
    val activePillContainer: Color,
    val activePillContent: Color,
)

val LocalGlassColors =
    staticCompositionLocalOf {
        GlassColors(
            surfaceTop = Color(0xD91E2332),
            surfaceBottom = Color(0xEB151924),
            borderTop = Color(0x40FFFFFF),
            borderMid = Color(0x1FFFFFFF),
            borderBottom = Color.White.copy(alpha = 0.05f),
            specularHighlight = Color(0xFFFFFFFF),
            activePillContainer = Color(0xAD2B4678),
            activePillContent = Color(0xFFD8E2FF),
        )
    }

@Composable
fun rememberGlassColors(scheme: ColorScheme, darkTheme: Boolean, highContrastDarkTheme: Boolean): GlassColors =
    remember(scheme, darkTheme, highContrastDarkTheme) {
        when {
            darkTheme && highContrastDarkTheme ->
                GlassColors(
                    surfaceTop = scheme.surfaceContainerLow,
                    surfaceBottom = Color.Black,
                    borderTop = scheme.primary.copy(alpha = 0.34f),
                    borderMid = scheme.outlineVariant.copy(alpha = 0.35f),
                    borderBottom = Color.White.copy(alpha = 0.05f),
                    specularHighlight = scheme.primary,
                    activePillContainer = scheme.secondaryContainer,
                    activePillContent = scheme.onSecondaryContainer,
                )
            darkTheme ->
                GlassColors(
                    surfaceTop = scheme.surfaceContainerHigh,
                    surfaceBottom = scheme.surfaceContainer,
                    borderTop = scheme.outlineVariant.copy(alpha = 0.45f),
                    borderMid = scheme.primary.copy(alpha = 0.18f),
                    borderBottom = scheme.outlineVariant.copy(alpha = 0.2f),
                    specularHighlight = scheme.primary,
                    activePillContainer = scheme.secondaryContainer,
                    activePillContent = scheme.onSecondaryContainer,
                )
            else ->
                GlassColors(
                    surfaceTop = scheme.surfaceContainerLowest,
                    surfaceBottom = scheme.surfaceContainer,
                    borderTop = scheme.outlineVariant.copy(alpha = 0.45f),
                    borderMid = scheme.primary.copy(alpha = 0.18f),
                    borderBottom = scheme.outlineVariant.copy(alpha = 0.25f),
                    specularHighlight = scheme.primary,
                    activePillContainer = scheme.secondaryContainer,
                    activePillContent = scheme.onSecondaryContainer,
                )
        }
    }

/**
 * Ensures any ColorScheme (especially dynamic Material You schemes on OEM ROMs or AMOLED overrides)
 * has a well-ordered, distinct tonal hierarchy across surface and container tokens so cards,
 * bottom sheets, editor gutters, and navigation bars never blend together or lose contrast.
 */
private fun normalizeColorScheme(raw: ColorScheme, darkTheme: Boolean, highContrastDarkTheme: Boolean): ColorScheme {
    val primaryArgb = raw.primary.toArgb()
    fun tone(baseHex: Int, primaryRatio: Float): Color =
        Color(ColorUtils.blendARGB(baseHex, primaryArgb, primaryRatio))

    return when {
        darkTheme && highContrastDarkTheme ->
            raw.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceDim = Color.Black,
                surfaceBright = tone(0xFF242834.toInt(), 0.10f),
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = tone(0xFF0A0C10.toInt(), 0.06f),
                surfaceContainer = tone(0xFF11141C.toInt(), 0.08f),
                surfaceContainerHigh = tone(0xFF191D28.toInt(), 0.10f),
                surfaceContainerHighest = tone(0xFF222736.toInt(), 0.12f),
            )
        darkTheme -> {
            // Guarantee clean separation if OEM dynamicDarkColorScheme collapses surfaceContainer tokens
            val bgLum = raw.background.luminance()
            val contLum = raw.surfaceContainer.luminance()
            if (kotlin.math.abs(contLum - bgLum) < 0.008f) {
                raw.copy(
                    surfaceDim = tone(0xFF0B0E14.toInt(), 0.06f),
                    background = tone(0xFF0E1118.toInt(), 0.07f),
                    surface = tone(0xFF0E1118.toInt(), 0.07f),
                    surfaceContainerLowest = tone(0xFF090B10.toInt(), 0.05f),
                    surfaceContainerLow = tone(0xFF141822.toInt(), 0.08f),
                    surfaceContainer = tone(0xFF191E2A.toInt(), 0.10f),
                    surfaceContainerHigh = tone(0xFF212736.toInt(), 0.12f),
                    surfaceContainerHighest = tone(0xFF2A3144.toInt(), 0.14f),
                )
            } else {
                raw
            }
        }
        else -> {
            val bgLum = raw.background.luminance()
            val contLum = raw.surfaceContainer.luminance()
            if (kotlin.math.abs(contLum - bgLum) < 0.008f) {
                raw.copy(
                    surfaceContainerLowest = Color.White,
                    background = tone(0xFFF8F9FE.toInt(), 0.03f),
                    surface = tone(0xFFF8F9FE.toInt(), 0.03f),
                    surfaceContainerLow = tone(0xFFF2F4FB.toInt(), 0.05f),
                    surfaceContainer = tone(0xFFECEEF7.toInt(), 0.07f),
                    surfaceContainerHigh = tone(0xFFE5E8F2.toInt(), 0.09f),
                    surfaceContainerHighest = tone(0xFFDFE2EE.toInt(), 0.11f),
                )
            } else {
                raw
            }
        }
    }
}

private fun buildDynamicThemeHolder(light: ColorScheme, dark: ColorScheme): ThemeHolder {
    val lightProps =
        Properties().apply {
            putAll(blueberry.lightTerminalColors)
            this["foreground"] = light.onSurface.toHex()
            this["background"] = light.surface.toHex()
            this["cursor"] = light.primary.toHex()
        }
    val darkProps =
        Properties().apply {
            putAll(blueberry.darkTerminalColors)
            this["foreground"] = dark.onSurface.toHex()
            this["background"] = dark.surface.toHex()
            this["cursor"] = dark.primary.toHex()
        }
    return ThemeHolder(
        id = "material-you-dynamic",
        name = "Material You (Dynamic)",
        inheritBase = true,
        lightScheme = light,
        darkScheme = dark,
        lightTokenColors = JsonArray(),
        darkTokenColors = JsonArray(),
        lightEditorColors = emptyList(),
        darkEditorColors = emptyList(),
        lightTerminalColors = lightProps,
        darkTerminalColors = darkProps,
    )
}

@Composable
fun XedTheme(
    darkTheme: Boolean = isDarkTheme(LocalContext.current),
    highContrastDarkTheme: Boolean = amoled.value,
    dynamicColor: Boolean = dynamicTheme.value,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolvedPair: Pair<ThemeHolder, ColorScheme> =
        if (dynamicColor && supportsDynamicTheming()) {
            val rawLight = normalizeColorScheme(dynamicLightColorScheme(context), darkTheme = false, highContrastDarkTheme = false)
            val rawDark = normalizeColorScheme(dynamicDarkColorScheme(context), darkTheme = true, highContrastDarkTheme = highContrastDarkTheme)
            val holder = remember(rawLight, rawDark) { buildDynamicThemeHolder(rawLight, rawDark) }
            holder to (if (darkTheme) rawDark else rawLight)
        } else {
            val selectedHolder =
                currentTheme.value
                    ?: themes.find { it.id == Settings.theme }?.also { currentTheme.value = it }
                    ?: blueberry.also { currentTheme.value = it }

            val baseScheme = if (darkTheme) selectedHolder.darkScheme else selectedHolder.lightScheme
            val normalizedScheme = normalizeColorScheme(baseScheme, darkTheme = darkTheme, highContrastDarkTheme = highContrastDarkTheme)
            selectedHolder to normalizedScheme
        }

    val themeHolder = resolvedPair.first
    val colorScheme = resolvedPair.second
    val glassColors = rememberGlassColors(colorScheme, darkTheme, highContrastDarkTheme)

    CompositionLocalProvider(
        LocalThemeHolder provides themeHolder,
        LocalGlassColors provides glassColors,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = rememberAppTypography(context)) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    }
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION_CODES.S <= Build.VERSION.SDK_INT

@Composable
fun harmonize(color: Long): Int {
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    return ColorUtils.blendARGB(color.toInt(), primaryArgb, 0.18f)
}

val ColorScheme.warningSurface: Color
    @Composable get() = if (isDarkTheme(LocalContext.current)) Color(harmonize(0xFF633F00)) else Color(harmonize(0xFFFFDDB4))

val ColorScheme.onWarningSurface: Color
    @Composable get() = if (isDarkTheme(LocalContext.current)) Color(harmonize(0xFFFFDDB4)) else Color(harmonize(0xFF633F00))

val ColorScheme.greenStatus: Color
    @Composable get() = if (isDarkTheme(LocalContext.current)) Color(harmonize(0xFFA6DA95)) else Color(harmonize(0xFF2E7D32))

val ColorScheme.yellowStatus: Color
    @Composable get() = if (isDarkTheme(LocalContext.current)) Color(harmonize(0xFFFFE082)) else Color(harmonize(0xFFE6AC00))

val ColorScheme.gitAdded: Color
    @Composable get() = if (isDarkTheme(LocalContext.current)) Color(harmonize(0xFF81C784)) else Color(harmonize(0xFF2E7D32))

val ColorScheme.gitModified: Color
    @Composable get() = if (isDarkTheme(LocalContext.current)) Color(harmonize(0xFF64B5F6)) else Color(harmonize(0xFF1565C0))

val ColorScheme.gitDeleted: Color
    get() = this.onSurface.copy(alpha = 0.6f)

val ColorScheme.gitConflicted: Color
    @Composable get() = if (isDarkTheme(LocalContext.current)) Color(harmonize(0xFFE57373)) else Color(harmonize(0xFFC62828))
