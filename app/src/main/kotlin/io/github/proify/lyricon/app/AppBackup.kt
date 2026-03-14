/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app

import android.util.Log
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.safeEncode
import io.github.proify.lyricon.app.util.LyricPrefs
import io.github.proify.lyricon.lyric.style.LyricSettingsSnapshot
import java.io.InputStream
import java.io.OutputStream

object AppBackup {

    private const val TAG = "BackupManager"

    fun export(outputStream: OutputStream): Boolean {
        return runCatching {
            val snapshot = LyricPrefs.getEffectiveSettingsSnapshot()
            val jsonText = json.safeEncode(snapshot)
            outputStream.use { it.write(jsonText.toByteArray(Charsets.UTF_8)) }
            true
        }.onFailure {
            Log.e(TAG, "Export failed", it)
        }.getOrDefault(false)
    }

    fun restore(input: InputStream): Boolean {
        return runCatching {
            val jsonText = input.use { it.readBytes() }.toString(Charsets.UTF_8)
            val snapshot = json.safeDecode<LyricSettingsSnapshot>(jsonText)
            LyricPrefs.persistSettingsSnapshot(snapshot)
            LyricPrefs.applySnapshotToPrefs(snapshot)
            true
        }.onFailure {
            Log.e(TAG, "Restore failed", it)
        }.getOrDefault(false)
    }
}
