/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.content.SharedPreferences
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class AnimStyle(
    var enable: Boolean = Defaults.ENABLE,
    var enterId: String = Defaults.ENTER_ID,
    var exitId: String = Defaults.EXIT_ID
) : AbstractStyle(), Parcelable {

    object Defaults {
        const val ENABLE: Boolean = true
        const val ENTER_ID: String = "in_fade_in_up"
        const val EXIT_ID: String = "out_fade_out_up"
    }

    override fun onLoad(preferences: SharedPreferences) {
        enable = preferences.getBoolean("lyric_style_anim_enable", Defaults.ENABLE)
        enterId =
            preferences.getString("lyric_style_anim_enter_id", Defaults.ENTER_ID) ?: Defaults.ENTER_ID
        exitId =
            preferences.getString("lyric_style_anim_exit_id", Defaults.EXIT_ID) ?: Defaults.EXIT_ID
    }

    override fun onWrite(editor: SharedPreferences.Editor) {
        editor.putBoolean("lyric_style_anim_enable", enable)
        editor.putString("lyric_style_anim_enter_id", enterId)
        editor.putString("lyric_style_anim_exit_id", exitId)
    }
}
