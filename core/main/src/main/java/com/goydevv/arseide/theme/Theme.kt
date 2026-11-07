package com.goydevv.arseide.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.MaterialColors
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.settings.editor.rememberAppTypography
import com.goydevv.arseide.settings.theme.themes
import com.goydevv.arseide.utils.isDarkTheme
import com.goydevv.arseide.utils.toast

val currentTheme = mutableStateOf<ThemeHolder?>(null)
val dynamicTheme = mutableStateOf(Settings.monet)
val amoled = mutableStateOf(Settings.amoled)

val LocalThemeHolder = staticCompositionLocalOf<ThemeHolder> { error("No ThemeHolder state provided") }

/**
 * Semantic tokens for ArseIDE's frosted and liquid glass surfaces.
 */
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
            borderBottom = Color(0x0DFFFFFF),
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
                    surfaceTop = Color(0xD610131C),
                    surfaceBottom = Color(0xEC06080C),
                    borderTop = scheme.primary.copy(alpha = 0.34f),
                    borderMid = Color.White.copy(alpha = 0.14f),
                    borderBottom = Color.White.copy(alpha = 0.05f),
                    specularHighlight = scheme.primary,
                    activePillContainer = scheme.primaryContainer.copy(alpha = 0.72f),
                    activePillContent = scheme.onPrimaryContainer,
                )
            darkTheme ->
                GlassColors(
                    surfaceTop = scheme.surfaceContainerHigh.copy(alpha = 0.84f),
                    surfaceBottom = scheme.surfaceContainer.copy(alpha = 0.92f),
                    borderTop = Color.White.copy(alpha = 0.22f),
                    borderMid = scheme.primary.copy(alpha = 0.14f),
                    borderBottom = Color.White.copy(alpha = 0.06f),
                    specularHighlight = Color.White,
                    activePillContainer = scheme.primaryContainer.copy(alpha = 0.75f),
                    activePillContent = scheme.onPrimaryContainer,
                )
            else ->
                GlassColors(
                    surfaceTop = scheme.surfaceContainerLowest.copy(alpha = 0.86f),
                    surfaceBottom = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    borderTop = Color.White.copy(alpha = 0.75f),
                    borderMid = scheme.primary.copy(alpha = 0.20f),
                    borderBottom = scheme.outlineVariant.copy(alpha = 0.35f),
                    specularHighlight = Color.White,
                    activePillContainer = scheme.primaryContainer.copy(alpha = 0.82f),
                    activePillContent = scheme.onPrimaryContainer,
                )
        }
    }

@Composable
fun XedTheme(
    darkTheme: Boolean = isDarkTheme(LocalContext.current),
    highContrastDarkTheme: Boolean = amoled.value,
    dynamicColor: Boolean = dynamicTheme.value,
    content: @Composable () -> Unit,
) {
    var themeHolder = blueberry
    val colorScheme =
        if (dynamicColor && supportsDynamicTheming()) {
            val context = LocalContext.current
            val baseColorScheme =
                when {
                    darkTheme && highContrastDarkTheme ->
                        dynamicDarkColorScheme(context)
                            .copy(
                                background = Color.Black,
                                surface = Color.Black,
                                surfaceDim = Color.Black,
                                surfaceContainerLowest = Color.Black,
                                surfaceContainerLow = Color(0xFF0B0D13),
                                surfaceContainer = Color(0xFF10131B),
                            )

                    darkTheme -> dynamicDarkColorScheme(context)
                    else -> dynamicLightColorScheme(context)
                }

            themeHolder = blueberry
            baseColorScheme
        } else {
            if (currentTheme.value == null) {
                themeHolder = themes.find { it.id == Settings.theme } ?: themeHolder
                currentTheme.value = themeHolder
            } else {
                themeHolder = currentTheme.value ?: themeHolder
            }

            val theme =
                if (darkTheme) {
                    if (highContrastDarkTheme) {
                        themeHolder.darkScheme.copy(
                            background = Color.Black,
                            surface = Color.Black,
                            surfaceDim = Color.Black,
                            surfaceContainerLowest = Color.Black,
                            surfaceContainerLow = Color(0xFF0B0D13),
                            surfaceContainer = Color(0xFF10131B),
                        )
                    } else {
                        themeHolder.darkScheme
                    }
                } else {
                    themeHolder.lightScheme
                }

            if (currentTheme.value == null) {
                LaunchedEffect(theme) { toast("No theme selected") }
                if (darkTheme) {
                    blueberry.darkScheme
                } else {
                    blueberry.lightScheme
                }
            } else {
                theme
            }
        }

    val glassColors = rememberGlassColors(colorScheme, darkTheme, highContrastDarkTheme)

    CompositionLocalProvider(
        LocalThemeHolder provides themeHolder,
        LocalGlassColors provides glassColors,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = rememberAppTypography(LocalContext.current)) {
            Surface(color = MaterialTheme.colorScheme.background) { content() }
        }
    }
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION_CODES.S <= Build.VERSION.SDK_INT

@Composable
fun harmonize(color: Long): Int {
    val context = LocalContext.current
    return MaterialColors.harmonizeWithPrimary(context, color.toInt())
}

// Custom warning colors
val ColorScheme.warningSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFF633F00)) else Color(harmonize(0xFFFFDDB4))

val ColorScheme.onWarningSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFFFDDB4)) else Color(harmonize(0xFF633F00))

// Status colors
val ColorScheme.greenStatus: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFA6DA95)) else Color(harmonize(0xFF44842E))

val ColorScheme.yellowStatus: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFFFE082)) else Color(harmonize(0xFFE6AC00))

// Git change colors
val ColorScheme.gitAdded: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFF81C784)) else Color(harmonize(0xFF2E7D32))

val ColorScheme.gitModified: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFF64B5F6)) else Color(harmonize(0xFF1565C0))

val ColorScheme.gitDeleted: Color
    get() = this.onSurface.copy(alpha = 0.6f)

val ColorScheme.gitConflicted: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(harmonize(0xFFE57373)) else Color(harmonize(0xFFC62828))
