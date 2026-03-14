/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import android.content.SharedPreferences
import android.os.Parcelable
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.toJson
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Parcelize
data class BasicStyle(
    var anchor: String = Defaults.ANCHOR,
    var insertionOrder: Int = Defaults.INSERTION_ORDER,
    var width: Float = Defaults.WIDTH,
    var widthInColorOSCapsuleMode: Float = Defaults.WIDTH_IN_COLOROS_CAPSULE_MODE,
    var margins: RectF = Defaults.MARGINS,
    var paddings: RectF = Defaults.PADDINGS,
    var visibilityRules: List<VisibilityRule> = Defaults.VISIBILITY_RULES,
    var hideOnLockScreen: Boolean = Defaults.HIDE_ON_LOCK_SCREEN,
    var noLyricHideTimeout: Int = Defaults.NO_LYRIC_HIDE_TIMEOUT,
    var noUpdateHideTimeout: Int = Defaults.NO_UPDATE_HIDE_TIMEOUT,
    var keywordHideTimeout: Int = Defaults.KEYWORD_HIDE_TIMEOUT,
    var keywordHideMatches: List<String> = Defaults.KEYWORD_HIDE_MATCH,
    var doubleTapSwitchClock: Boolean = Defaults.DOUBLE_TAP_SWITCH_CLOCK,
    var listenStatusBarColor: Boolean = Defaults.LISTEN_STATUS_BAR_COLOR,
    var statusBarColorSource: Int = Defaults.STATUS_BAR_COLOR_SOURCE,
    var statusBarColorAnchorId: String? = Defaults.STATUS_BAR_COLOR_ANCHOR_ID,
    var oledShiftEnabled: Boolean = Defaults.OLED_SHIFT_ENABLED,
    var oledShiftMode: Int = Defaults.OLED_SHIFT_MODE,
    var oledShiftRangeDp: Float = Defaults.OLED_SHIFT_RANGE_DP,
    var oledShiftIntervalSec: Int = Defaults.OLED_SHIFT_INTERVAL_SEC,
    var oledShiftRandomIntervalMinSec: Int = Defaults.OLED_SHIFT_RANDOM_MIN_SEC,
    var oledShiftRandomIntervalMaxSec: Int = Defaults.OLED_SHIFT_RANDOM_MAX_SEC
) : AbstractStyle(), Parcelable {

    @IgnoredOnParcel
    @Transient
    var keywordsHidePattern: List<Regex>? = mutableListOf()
        get() = if (field == null) {
            val list = keywordHideMatches.mapNotNull {
                try {
                    Regex(it)
                } catch (_: Exception) {
                    null
                }
            }
            field = list
            field
        } else {
            field
        }

    override fun onLoad(preferences: SharedPreferences) {
        anchor =
            preferences.getString("lyric_style_base_anchor", Defaults.ANCHOR) ?: Defaults.ANCHOR
        insertionOrder =
            preferences.getInt("lyric_style_base_insertion_order", Defaults.INSERTION_ORDER)
        width = preferences.getFloat("lyric_style_base_width", Defaults.WIDTH)
        widthInColorOSCapsuleMode = preferences.getFloat(
            "lyric_style_base_width_in_coloros_capsule_mode",
            Defaults.WIDTH_IN_COLOROS_CAPSULE_MODE
        )

        margins = json.safeDecode<RectF>(
            preferences.getString("lyric_style_base_margins", null),
            Defaults.MARGINS
        )
        paddings = json.safeDecode<RectF>(
            preferences.getString("lyric_style_base_paddings", null),
            Defaults.PADDINGS
        )
        visibilityRules = json.safeDecode<MutableList<VisibilityRule>>(
            preferences.getString("lyric_style_base_visibility_rules", null),
            Defaults.VISIBILITY_RULES.toMutableList()
        )
        hideOnLockScreen = preferences.getBoolean(
            "lyric_style_base_hide_on_lock_screen",
            Defaults.HIDE_ON_LOCK_SCREEN
        )

        noLyricHideTimeout = preferences.getInt(
            "lyric_style_base_no_lyric_hide_timeout",
            Defaults.NO_LYRIC_HIDE_TIMEOUT
        )
        noUpdateHideTimeout = preferences.getInt(
            "lyric_style_base_no_update_hide_timeout",
            Defaults.NO_UPDATE_HIDE_TIMEOUT
        )
        keywordHideTimeout = preferences.getInt(
            "lyric_style_base_keyword_hide_timeout",
            Defaults.KEYWORD_HIDE_TIMEOUT
        )

        preferences.getString("lyric_style_base_timeout_hide_keywords", null)
            ?.trim()
            ?.lines()
            .let {
                keywordHideMatches = it ?: emptyList()
                keywordsHidePattern = null
            }

        doubleTapSwitchClock = preferences.getBoolean(
            "lyric_style_base_double_tap_switch_clock",
            Defaults.DOUBLE_TAP_SWITCH_CLOCK
        )
        listenStatusBarColor = preferences.getBoolean(
            "lyric_style_base_listen_statusbar_color",
            Defaults.LISTEN_STATUS_BAR_COLOR
        )
        statusBarColorSource = preferences.getInt(
            "lyric_style_base_statusbar_color_source",
            Defaults.STATUS_BAR_COLOR_SOURCE
        )
        statusBarColorAnchorId = preferences.getString(
            "lyric_style_base_statusbar_color_anchor_id",
            Defaults.STATUS_BAR_COLOR_ANCHOR_ID
        )
        oledShiftEnabled = preferences.getBoolean(
            "lyric_style_base_oled_shift_enable",
            Defaults.OLED_SHIFT_ENABLED
        )
        oledShiftMode = preferences.getInt(
            "lyric_style_base_oled_shift_mode",
            Defaults.OLED_SHIFT_MODE
        )
        oledShiftRangeDp = preferences.getFloat(
            "lyric_style_base_oled_shift_range",
            Defaults.OLED_SHIFT_RANGE_DP
        )
        oledShiftIntervalSec = preferences.getInt(
            "lyric_style_base_oled_shift_interval",
            Defaults.OLED_SHIFT_INTERVAL_SEC
        )
        oledShiftRandomIntervalMinSec = preferences.getInt(
            "lyric_style_base_oled_shift_random_min",
            Defaults.OLED_SHIFT_RANDOM_MIN_SEC
        )
        oledShiftRandomIntervalMaxSec = preferences.getInt(
            "lyric_style_base_oled_shift_random_max",
            Defaults.OLED_SHIFT_RANDOM_MAX_SEC
        )
    }

    override fun onWrite(editor: SharedPreferences.Editor) {
        editor.putString("lyric_style_base_anchor", anchor)
        editor.putInt("lyric_style_base_insertion_order", insertionOrder)
        editor.putFloat("lyric_style_base_width", width)
        editor.putFloat("lyric_style_base_width_in_coloros_capsule_mode", widthInColorOSCapsuleMode)
        editor.putString("lyric_style_base_margins", margins.toJson())
        editor.putString("lyric_style_base_paddings", paddings.toJson())
        editor.putString("lyric_style_base_visibility_rules", visibilityRules.toJson())
        editor.putBoolean("lyric_style_base_hide_on_lock_screen", hideOnLockScreen)
        editor.putInt(
            "lyric_style_base_no_lyric_hide_timeout",
            noLyricHideTimeout
        )
        editor.putInt(
            "lyric_style_base_no_update_hide_timeout",
            noUpdateHideTimeout
        )
        editor.putInt(
            "lyric_style_base_keyword_hide_timeout",
            keywordHideTimeout
        )
        editor.putString("lyric_style_base_timeout_hide_keywords", keywordHideMatches.toJson())
        editor.putBoolean("lyric_style_base_double_tap_switch_clock", doubleTapSwitchClock)
        editor.putBoolean("lyric_style_base_listen_statusbar_color", listenStatusBarColor)
        editor.putInt("lyric_style_base_statusbar_color_source", statusBarColorSource)
        editor.putString("lyric_style_base_statusbar_color_anchor_id", statusBarColorAnchorId)
        editor.putBoolean("lyric_style_base_oled_shift_enable", oledShiftEnabled)
        editor.putInt("lyric_style_base_oled_shift_mode", oledShiftMode)
        editor.putFloat("lyric_style_base_oled_shift_range", oledShiftRangeDp)
        editor.putInt("lyric_style_base_oled_shift_interval", oledShiftIntervalSec)
        editor.putInt("lyric_style_base_oled_shift_random_min", oledShiftRandomIntervalMinSec)
        editor.putInt("lyric_style_base_oled_shift_random_max", oledShiftRandomIntervalMaxSec)
    }

    object Defaults {

        const val HIDE_ON_LOCK_SCREEN: Boolean = true
        const val ANCHOR: String = "clock"
        const val INSERTION_ORDER: Int = INSERTION_ORDER_BEFORE
        const val WIDTH: Float = 100f
        const val WIDTH_IN_COLOROS_CAPSULE_MODE: Float = 70f
        val MARGINS: RectF = RectF()
        val PADDINGS: RectF = RectF()
        val VISIBILITY_RULES: List<VisibilityRule> = emptyList()
        const val NO_LYRIC_HIDE_TIMEOUT: Int = 0
        const val NO_UPDATE_HIDE_TIMEOUT = 0
        const val KEYWORD_HIDE_TIMEOUT: Int = 0
        val KEYWORD_HIDE_MATCH: List<String> = listOf()
        const val DOUBLE_TAP_SWITCH_CLOCK: Boolean = false
        const val LISTEN_STATUS_BAR_COLOR: Boolean = true
        const val STATUS_BAR_COLOR_SOURCE: Int = COLOR_SOURCE_CLOCK
        val STATUS_BAR_COLOR_ANCHOR_ID: String? = null
        const val OLED_SHIFT_ENABLED: Boolean = false
        const val OLED_SHIFT_MODE: Int = OLED_SHIFT_MODE_ON_LYRIC_CHANGE
        const val OLED_SHIFT_RANGE_DP: Float = 2.0f
        const val OLED_SHIFT_INTERVAL_SEC: Int = 120
        const val OLED_SHIFT_RANDOM_MIN_SEC: Int = 60
        const val OLED_SHIFT_RANDOM_MAX_SEC: Int = 180
    }

    companion object {
        const val INSERTION_ORDER_BEFORE: Int = 0
        const val INSERTION_ORDER_AFTER: Int = 1
        const val OLED_SHIFT_MODE_ON_LYRIC_CHANGE: Int = 0
        const val OLED_SHIFT_MODE_INTERVAL: Int = 1
        const val OLED_SHIFT_MODE_RANDOM_INTERVAL: Int = 2
        const val COLOR_SOURCE_CLOCK: Int = 0
        const val COLOR_SOURCE_ANCHOR: Int = 1
        const val COLOR_SOURCE_CUSTOM_ANCHOR: Int = 2
    }
}
