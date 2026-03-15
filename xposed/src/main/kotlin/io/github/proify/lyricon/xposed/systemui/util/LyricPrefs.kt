/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("MemberVisibilityCanBePrivate")

package io.github.proify.lyricon.xposed.systemui.util

import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricSettingsSnapshot
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.style.PackageStyle
import io.github.proify.lyricon.lyric.style.TranslationConfig
import io.github.proify.lyricon.lyric.style.TranslationDefaults

object LyricPrefs {
    data class TranslationSettings(
        val enabled: Boolean,
        val provider: String,
        val targetLanguage: String,
        val apiKey: String,
        val model: String,
        val baseUrl: String,
        val maxCacheSize: Int,
        val ignoreRegex: String,
        val customPrompt: String,
        val onlyShowTranslation: Boolean,
        val waitTranslationReady: Boolean
    ) {
        val isUsable: Boolean
            get() = enabled
                    && provider in TranslationDefaults.SUPPORTED_PROVIDERS
                    && apiKey.isNotBlank()
    }

    @Volatile
    private var settingsSnapshot: LyricSettingsSnapshot? = null

    @Volatile
    var activePackageName: String? = null

    fun updateSettingsSnapshot(snapshot: LyricSettingsSnapshot?) {
        settingsSnapshot = snapshot
    }

    fun getActivePackageStyle(): PackageStyle {
        val snapshot = settingsSnapshot
        val defaultStyle = snapshot?.packageStyles?.get(snapshot.defaultPackageName) ?: PackageStyle()
        val activePackage = activePackageName
        return if (snapshot != null && activePackage != null &&
            snapshot.enabledPackages.contains(activePackage)
        ) {
            snapshot.packageStyles[activePackage] ?: defaultStyle
        } else {
            defaultStyle
        }
    }

    fun getLyricStyle(packageName: String? = null): LyricStyle {
        val snapshot = settingsSnapshot
        val baseStyle = snapshot?.baseStyle ?: BasicStyle()
        if (packageName == null) {
            return LyricStyle(baseStyle, getActivePackageStyle())
        }
        val defaultStyle = snapshot?.packageStyles?.get(snapshot.defaultPackageName) ?: PackageStyle()
        return LyricStyle(
            baseStyle,
            snapshot?.packageStyles?.get(packageName) ?: defaultStyle
        )
    }

    fun getActiveTranslationSettings(): TranslationSettings {
        val snapshot = settingsSnapshot
        val activePackage = activePackageName
        val defaultConfig =
            snapshot?.translationConfigs?.get(snapshot.defaultPackageName) ?: TranslationConfig()
        val config =
            if (snapshot != null && activePackage != null &&
                snapshot.enabledPackages.contains(activePackage)
            ) {
                snapshot.translationConfigs[activePackage] ?: defaultConfig
            } else {
                defaultConfig
            }
        return config.toTranslationSettings()
    }

    private fun TranslationConfig.toTranslationSettings(): TranslationSettings {
        return TranslationSettings(
            enabled = enabled,
            provider = provider,
            targetLanguage = targetLanguage,
            apiKey = apiKey,
            model = model,
            baseUrl = baseUrl,
            maxCacheSize = maxCacheSize,
            ignoreRegex = ignoreRegex,
            customPrompt = customPrompt,
            onlyShowTranslation = onlyShowTranslation,
            waitTranslationReady = waitTranslationReady
        )
    }
}
