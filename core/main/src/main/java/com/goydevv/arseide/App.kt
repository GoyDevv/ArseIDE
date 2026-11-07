package com.goydevv.arseide

import android.app.Application
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.LocaleListCompat
import com.github.anrwatchdog.ANRWatchDog
import com.goydevv.arseide.activities.main.SessionManager
import com.goydevv.arseide.commands.CommandProvider
import com.goydevv.arseide.commands.KeybindingsManager
import com.goydevv.arseide.commands.ToolbarConfiguration
import com.goydevv.arseide.crashhandler.CrashHandler
import com.goydevv.arseide.editor.CodeHighlighter
import com.goydevv.arseide.editor.FontCache
import com.goydevv.arseide.editor.KeywordManager
import com.goydevv.arseide.editor.LanguageManager
import com.goydevv.arseide.extension.ActivityProvider
import com.goydevv.arseide.extension.manager.ExtensionAPIManager
import com.goydevv.arseide.extension.loader.loadAllExtensions
import com.goydevv.arseide.extension.manager.ExtensionManager
import com.goydevv.arseide.icons.pack.IconPackManager
import com.goydevv.arseide.lsp.FileIconProvider
import com.goydevv.arseide.lsp.LspPersistence
import com.goydevv.arseide.lsp.MarkdownImageProvider
import com.goydevv.arseide.resources.Res
import com.goydevv.arseide.settings.Preference
import com.goydevv.arseide.settings.Settings
import com.goydevv.arseide.settings.debugOptions.startThemeFlipperIfNotRunning
import com.goydevv.arseide.settings.editor.DEFAULT_APP_FONT_PATH
import com.goydevv.arseide.settings.editor.DEFAULT_EDITOR_FONT_PATH
import com.goydevv.arseide.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.goydevv.arseide.theme.updateThemes
import com.goydevv.arseide.utils.application
import com.goydevv.arseide.utils.getTempDir
import com.goydevv.arseide.BuildConfig
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class App : Application() {
    companion object {
        val versionCode: Long by lazy {
            val app = application ?: throw IllegalStateException("Application is not initialized yet")
            PackageInfoCompat.getLongVersionCode(
                app.packageManager.getPackageInfo(app.packageName, 0)
            )
        }

        private var _extensionManager: ExtensionManager? = null
        val extensionManager: ExtensionManager
            get() {
                if (_extensionManager == null) {
                    _extensionManager = ExtensionManager(application!!)
                }

                return _extensionManager!!
            }

        private var _iconPackManager: IconPackManager? = null
        val iconPackManager: IconPackManager
            get() {
                if (_iconPackManager == null) {
                    _iconPackManager = IconPackManager(application!!)
                }

                return _iconPackManager!!
            }
    }

    init {
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        application = this
        Res.application = this

        updateThemes()
        LspPersistence.restoreServers()

        MarkdownImageProvider.register()
        FileIconProvider.register()

        CommandProvider.buildCommands()
        // One-time: make sure existing users (who already have a saved toolbar layout) get the new
        // "Show all files" toggle in their editor toolbar / overflow menu.
        if (!Settings.migrated_show_all_files_toolbar) {
            if (!Settings.action_items.split("|").contains("editor.show_all_files")) {
                ToolbarConfiguration.addEditorToolbarCommand("editor.show_all_files")
            }
            Settings.migrated_show_all_files_toolbar = true
        }

        // One-time: inject the Android "Gradle sync" button right after Run for existing users.
        if (!Settings.migrated_sync_toolbar) {
            val items = Settings.action_items.split("|").toMutableList()
            if (!items.contains("editor.sync")) {
                val runIdx = items.indexOf("editor.run")
                if (runIdx >= 0) items.add(runIdx + 1, "editor.sync") else items.add("editor.sync")
                Settings.action_items = items.joinToString("|")
            }
            Settings.migrated_sync_toolbar = true
        }

        // One-time: add the "Extra keys" toggle to the editor toolbar for existing users.
        if (!Settings.migrated_extra_keys_toolbar) {
            if (!Settings.action_items.split("|").contains("editor.toggle_extra_keys")) {
                ToolbarConfiguration.addEditorToolbarCommand("editor.toggle_extra_keys")
            }
            Settings.migrated_extra_keys_toolbar = true
        }
        KeybindingsManager.loadKeybindings()

        val currentLocale = Locale.forLanguageTag(Settings.current_lang)
        val appLocale = LocaleListCompat.create(currentLocale)
        AppCompatDelegate.setApplicationLocales(appLocale)

        GlobalScope.launch(Dispatchers.IO) {
            launch(Dispatchers.IO) {
                extensionManager.indexLocalExtensions()
                extensionManager.loadAllExtensions()
                registerActivityLifecycleCallbacks(ExtensionAPIManager)
                registerActivityLifecycleCallbacks(ActivityProvider)
            }

            launch(Dispatchers.IO) { iconPackManager.indexIconPacks() }

            launch { LanguageManager.initGrammarRegistry() }

            launch { KeywordManager.initKeywordRegistry(this@App) }

            launch { CodeHighlighter.registerMarkdownCodeHighlighter(this@App) }

            launch(Dispatchers.IO) { SessionManager.preloadSession() }

            launch(Dispatchers.IO) {
                val editorFontPath = Settings.editor_font_path.ifEmpty { DEFAULT_EDITOR_FONT_PATH }
                val isEditorAsset = if (editorFontPath.isNotEmpty()) Settings.is_editor_font_asset else true

                val appFontPath = Settings.app_font_path.ifEmpty { DEFAULT_APP_FONT_PATH }
                val isAppAsset = if (editorFontPath.isNotEmpty()) Settings.is_app_font_asset else true

                val terminalFontPath = Settings.terminal_font_path.ifEmpty { DEFAULT_TERMINAL_FONT_PATH }
                val isTerminalAsset = if (terminalFontPath.isNotEmpty()) Settings.is_terminal_font_asset else true

                FontCache.loadFont(this@App, editorFontPath, isEditorAsset)
                FontCache.loadFont(this@App, appFontPath, isAppAsset)
                FontCache.loadFont(this@App, terminalFontPath, isTerminalAsset)
            }

            launch(Dispatchers.IO) { Preference.preloadAllSettings() }

            launch { DocumentProvider.setDocumentProviderEnabled(this@App, Settings.expose_home_dir) }

            launch(Dispatchers.IO) {
                getTempDir().apply {
                    if (exists() && listFiles().isNullOrEmpty().not()) {
                        deleteRecursively()
                    }
                }
            }

            launch { runCatching { UpdateChecker.checkForUpdates("main") } }

            // wait until UpdateManager is done, it should only take few milliseconds
            UpdateManager.inspect()

            // debug options
            startThemeFlipperIfNotRunning()
        }

        if (BuildConfig.DEBUG || Settings.anr_watchdog) {
            ANRWatchDog().start()
        }

        if (BuildConfig.DEBUG || Settings.strict_mode) {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .apply {
                        detectAll()
                        penaltyLog()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                                violation.printStackTrace()
                                violation.cause?.let { throw it }
                            }
                        }
                    }
                    .build()
            )
        }
    }
}
