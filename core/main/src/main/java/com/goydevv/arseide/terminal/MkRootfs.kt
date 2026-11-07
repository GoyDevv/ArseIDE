package com.goydevv.arseide.terminal

import android.content.Context
import com.goydevv.arseide.file.child
import com.goydevv.arseide.file.sandboxDir
import com.goydevv.arseide.file.sandboxHomeDir
import com.goydevv.arseide.utils.getTempDir
import com.goydevv.arseide.utils.isMainThread
import java.io.File
import kotlinx.coroutines.CoroutineScope

enum class NEXT_STAGE {
    NONE,
    EXTRACTION,
}

suspend fun CoroutineScope.getNextStage(context: Context): NEXT_STAGE {
    if (isMainThread()) {
        throw RuntimeException("IO operation on the main thread")
    }

    val sandboxFile = File(getTempDir(), "sandbox.tar.gz")
    val rootfsFiles =
        sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath &&
                it.absolutePath != sandboxDir().child("tmp").absolutePath
        } ?: emptyList()

    return if (sandboxFile.exists().not() || rootfsFiles.isEmpty().not()) {
        NEXT_STAGE.NONE
    } else {
        NEXT_STAGE.EXTRACTION
    }
}
