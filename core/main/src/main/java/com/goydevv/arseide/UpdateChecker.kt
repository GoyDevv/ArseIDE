package com.goydevv.arseide

import com.goydevv.arseide.components.ChangelogInfo
import com.goydevv.arseide.settings.Settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Checks `https://api.github.com/repos/GoyDevv/ArseIDE/releases` for newer releases.
 * UI presentation is handled by [com.goydevv.arseide.components.ReleaseChangelogGate] in Compose
 * with full Markdown rendering.
 */
object UpdateChecker {

    @OptIn(DelicateCoroutinesApi::class)
    fun checkForUpdates(branch: String = "main") {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                if (!Settings.check_for_update) return@launch
                ChangelogInfo.fetchReleases()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                Settings.last_update_check_timestamp = System.currentTimeMillis()
            }
        }
    }
}
