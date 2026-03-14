/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app

import android.util.Log
import io.github.proify.android.extensions.deflate
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeEncode
import io.github.proify.lyricon.app.LyriconApp.Companion.systemUIChannel
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.app.util.LyricPrefs

fun updateRemoteLyricStyle() {
    fun getCallSourceMethod(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return if (stackTrace.size > 3) "${stackTrace[3].className}.${stackTrace[3].methodName}" else "Unknown"
    }
    Log.d(LyriconApp.TAG, "updateRemoteLyricStyle called from ${getCallSourceMethod()}")
    runCatching {
        val snapshot = LyricPrefs.buildSettingsSnapshot()
        val payload = json.safeEncode(snapshot).toByteArray(Charsets.UTF_8).deflate()
        systemUIChannel.put(AppBridgeConstants.REQUEST_SYNC_SETTINGS_SNAPSHOT, payload)
    }.onFailure {
        Log.w(LyriconApp.TAG, "sync settings snapshot failed", it)
    }
    systemUIChannel.put(AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE)
}
