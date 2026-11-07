package com.goydevv.arseide.runner

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.goydevv.arseide.extension.api.XedExtensionPoint
import com.goydevv.arseide.file.FileObject
import com.goydevv.arseide.icons.Icon
import com.goydevv.arseide.runner.runners.UniversalRunner
import com.goydevv.arseide.runner.runners.web.html.HtmlRunner
import com.goydevv.arseide.runner.runners.web.markdown.MarkdownRunner
import com.goydevv.arseide.settings.Preference
import com.goydevv.arseide.utils.errorDialog

abstract class Runner {
    abstract val id: String
    abstract val label: String
    open val description: String? = null
    open val onConfigure: (() -> Unit)? = null

    abstract fun getIcon(context: Context): Icon?

    abstract fun matcher(fileObject: FileObject): Boolean

    abstract suspend fun run(activity: Activity, fileObject: FileObject)

    abstract suspend fun isRunning(): Boolean

    abstract suspend fun stop()

    fun isEnabled(): Boolean {
        return Preference.getBoolean("runner_$id", true)
    }

    fun setEnabled(enabled: Boolean) {
        Preference.setBoolean("runner_$id", enabled)
    }
}

object RunnerManager {

    private val _extensionRunners = mutableStateListOf<Runner>()

    val extensionRunners: List<Runner>
        get() = _extensionRunners.toList()

    val builtinRunners = listOf(HtmlRunner, MarkdownRunner, UniversalRunner)

    @XedExtensionPoint
    fun registerRunner(runner: Runner) {
        if (!_extensionRunners.contains(runner)) {
            _extensionRunners.add(runner)
        }
    }

    @XedExtensionPoint
    fun unregisterRunner(runner: Runner) {
        _extensionRunners.remove(runner)
    }

    fun isRunnable(fileObject: FileObject): Boolean {
        return getAvailableRunners(fileObject).isNotEmpty()
    }

    fun getAvailableRunners(fileObject: FileObject): List<Runner> {
        val result = mutableListOf<Runner>()

        val runners = builtinRunners + extensionRunners + ShellBasedRunners.runners
        runners.forEach {
            if (it.isEnabled() && it.matcher(fileObject)) {
                result.add(it)
            }
        }

        return result
    }

    suspend fun run(activity: Activity, fileObject: FileObject, onMultipleRunners: (List<Runner>) -> Unit) {
        val availableRunners = getAvailableRunners(fileObject)

        if (availableRunners.isEmpty()) {
            errorDialog(activity, msg = "No runners available")
            return
        }

        if (availableRunners.size == 1) {
            availableRunners[0].run(activity, fileObject)
        } else {
            onMultipleRunners.invoke(availableRunners)
        }
    }
}
