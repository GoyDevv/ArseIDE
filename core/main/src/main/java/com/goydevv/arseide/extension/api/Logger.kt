package com.goydevv.arseide.extension.api
import android.util.Log
import com.goydevv.arseide.extension.ExtensionId
import com.goydevv.arseide.settings.debugOptions.LogCollector

fun ExtensionId.logDebug(msg: String) {
    Log.d(this, msg)
    LogCollector.reportDebug("[${this}]$msg")
}

fun ExtensionId.logInfo(msg: String) {
    Log.i(this, msg)
    LogCollector.reportInfo("[${this}]$msg")
}

fun ExtensionId.logWarn(msg: String) {
    Log.w(this, msg)
    LogCollector.reportWarn("[${this}]$msg")
}

fun ExtensionId.logError(msg: String) {
    Log.e(this, msg)
    LogCollector.reportError("[${this}]$msg")
}