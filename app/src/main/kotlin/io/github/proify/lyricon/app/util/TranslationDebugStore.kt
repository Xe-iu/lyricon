/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.content.Context
import io.github.proify.android.extensions.defaultSharedPreferences
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.safeEncode
import io.github.proify.lyricon.lyric.style.TranslationDebugInfo

object TranslationDebugStore {
    const val PREF_KEY_DEBUG_INFO: String = "translation_debug_info"

    fun persist(context: Context, info: TranslationDebugInfo) {
        val prefs = context.defaultSharedPreferences
        prefs.editCommit {
            putString(PREF_KEY_DEBUG_INFO, json.safeEncode(info))
        }
    }

    fun read(context: Context): TranslationDebugInfo? {
        val prefs = context.defaultSharedPreferences
        val raw = prefs.getString(PREF_KEY_DEBUG_INFO, null)
        return json.safeDecode<TranslationDebugInfo>(raw)
    }
}
