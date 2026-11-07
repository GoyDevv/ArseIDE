package com.goydevv.arseide.editor

import android.content.Context
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.theme.currentTheme
import com.goydevv.arseide.utils.isDarkTheme
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource

private var selectionColor: Color? = null

@Suppress("ComposableNaming")
@Composable
fun preloadSelectionColor() {
    val selectionColors = LocalTextSelectionColors.current
    val selectionBackground = selectionColors.backgroundColor
    selectionColor = selectionBackground
}

fun getSelectionColor(): Color? {
    return selectionColor
}

object ThemeManager {
    private val colorSchemeCache = hashMapOf<String, TextMateColorScheme>()

    fun invalidateCache() {
        colorSchemeCache.clear()
    }

    suspend fun createColorScheme(context: Context, patchArgs: Editor.PatchArgs?) =
        withContext(Dispatchers.IO) {
            val cacheKey = getCacheKey(context)

            colorSchemeCache[cacheKey]?.let { cached ->
                if (patchArgs != null) {
                    XedColorScheme.applyPatchesTo(cached, patchArgs)
                }
                return@withContext cached
            }

            val darkTheme = isDarkTheme(context)
            val amoled = Settings.amoled

            val themeModel =
                when {
                    darkTheme && amoled ->
                        buildThemeModel(
                            context = context,
                            basePath = TEXTMATE_AMOLED_PREFIX + DARCULA_THEME,
                            baseName = DARCULA_THEME,
                            darkTheme = true,
                        )
                    darkTheme ->
                        buildThemeModel(
                            context = context,
                            basePath = TEXTMATE_PREFIX + DARCULA_THEME,
                            baseName = DARCULA_THEME,
                            darkTheme = true,
                        )
                    else ->
                        buildThemeModel(
                            context = context,
                            basePath = TEXTMATE_PREFIX + QUIETLIGHT_THEME,
                            baseName = QUIETLIGHT_THEME,
                            darkTheme = false,
                        )
                }

            XedColorScheme(patchArgs, themeModel).also {
                colorSchemeCache[cacheKey] = it
                return@withContext it
            }
        }

    fun createColorSchemeBlocking(context: Context, patchArgs: Editor.PatchArgs?): TextMateColorScheme = runBlocking {
        createColorScheme(context, patchArgs)
    }

    private suspend fun buildThemeModel(context: Context, basePath: String, baseName: String, darkTheme: Boolean) =
        withContext(Dispatchers.IO) {
            val inputStream = context.assets.open(basePath)
            InputStreamReader(inputStream).use { reader ->
                val jsonElement = JsonParser.parseReader(reader)
                val jsonObject = jsonElement.asJsonObject

                val selectedTheme = if (Settings.monet) null else currentTheme.value
                val tokenArray =
                    when {
                        selectedTheme == null -> JsonArray()
                        darkTheme -> selectedTheme.darkTokenColors
                        else -> selectedTheme.lightTokenColors
                    }

                val arrayName =
                    if (jsonObject.has("settings")) {
                        "settings"
                    } else if (jsonObject.has("tokenColors")) {
                        "tokenColors"
                    } else null

                selectedTheme?.let { jsonObject.add("name", JsonPrimitive(it.name)) }

                if (!tokenArray.isEmpty) {
                    if (arrayName != null) {
                        if (selectedTheme!!.inheritBase) {
                            val existingTokenColors = jsonObject[arrayName].asJsonArray
                            existingTokenColors.addAll(tokenArray)
                        } else {
                            jsonObject.remove(arrayName)
                            jsonObject.add(arrayName, tokenArray)
                        }
                    } else {
                        jsonObject.add("tokenColors", tokenArray)
                    }
                }

                val bytes = jsonObject.toString().toByteArray(Charsets.UTF_8)
                val bais = ByteArrayInputStream(bytes)
                ThemeModel(IThemeSource.fromInputStream(bais, baseName, null))
            }
        }
}
