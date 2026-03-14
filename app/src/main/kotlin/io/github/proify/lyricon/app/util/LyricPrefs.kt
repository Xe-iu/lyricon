/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.util

import android.content.SharedPreferences
import io.github.proify.android.extensions.fromJson
import io.github.proify.android.extensions.getWorldReadableSharedPreferences
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.toJson
import io.github.proify.lyricon.app.LyriconApp
import io.github.proify.lyricon.app.bridge.AppBridge.LyricStylePrefs
import io.github.proify.lyricon.app.bridge.AppBridge.LyricStylePrefs.KEY_ENABLED_PACKAGES
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricSettingsSnapshot
import io.github.proify.lyricon.lyric.style.PackageStyle
import io.github.proify.lyricon.lyric.style.TranslationConfig
import io.github.proify.lyricon.lyric.style.TranslationDefaults
import io.github.proify.lyricon.lyric.style.VisibilityRule

/**
 * 歌词样式偏好管理工具类。
 *
 * 提供全局基础样式、包级样式及可视化规则的持久化读写接口。
 * 使用 [SharedPreferences] 存储数据，并支持 JSON 序列化。
 */
object LyricPrefs {

    /** 默认应用包名 */
    const val DEFAULT_PACKAGE_NAME: String = LyricStylePrefs.DEFAULT_PACKAGE_NAME

    /** 已配置的包名存储键 */
    private const val KEY_CONFIGURED_PACKAGES: String = "configured"

    /** 管理包级样式配置的 SharedPreferences */
    private val packageStyleManager: SharedPreferences =
        getSharedPreferences(LyricStylePrefs.PREF_PACKAGE_STYLE_MANAGER)

    /** 基础样式偏好 SharedPreferences */
    val basicStylePrefs: SharedPreferences
        get() = getSharedPreferences(LyricStylePrefs.PREF_NAME_BASE_STYLE)

    /** 获取指定名称的 SharedPreferences*/
    fun getSharedPreferences(name: String): SharedPreferences {
        return LyriconApp.instance.getWorldReadableSharedPreferences(name)
    }

    /** 获取指定包名对应的样式配置偏好名称 */
    fun getPackagePrefName(packageName: String): String =
        LyricStylePrefs.getPackageStylePreferenceName(packageName)

    /** 设置启用的包名集合 */
    fun setEnabledPackageNames(names: Set<String>) {
        packageStyleManager.editCommit {
            putStringSet(KEY_ENABLED_PACKAGES, names)
        }
    }

    /** 获取启用的包名集合 */
    fun getEnabledPackageNames(): Set<String> {
        return packageStyleManager
            .getStringSet(KEY_ENABLED_PACKAGES, null)?.toSet() ?: emptySet()
    }

    /** 设置已配置的包名集合 */
    fun setConfiguredPackageNames(names: Set<String>) {
        packageStyleManager.editCommit {
            putString(KEY_CONFIGURED_PACKAGES, names.toJson())
        }
    }

    /** 获取已配置的包名集合 */
    fun getConfiguredPackageNames(): Set<String> {
        val jsonData = packageStyleManager.getString(KEY_CONFIGURED_PACKAGES, null)
        return json.safeDecode<List<String>>(jsonData).toSet()
    }

    /** 设置歌词显示可视化规则 */
    fun setViewVisibilityRule(rules: List<VisibilityRule>?) {
        basicStylePrefs.editCommit {
            if (rules.isNullOrEmpty()) {
                remove("lyric_style_base_visibility_rules")
            } else {
                putString("lyric_style_base_visibility_rules", rules.toJson())
            }
        }
    }

    /** 获取歌词显示可视化规则 */
    fun getViewVisibilityRule(): List<VisibilityRule> {
        val json = basicStylePrefs.getString("lyric_style_base_visibility_rules", null)
        return json?.fromJson<List<VisibilityRule>>() ?: emptyList()
    }

    private const val KEY_TRANSLATION_ENABLED = "lyric_translation_enabled"
    private const val KEY_TRANSLATION_PROVIDER = "lyric_translation_api_provider"
    private const val KEY_TRANSLATION_TARGET_LANGUAGE = "lyric_translation_target_language"
    private const val KEY_TRANSLATION_OPENAI_API_KEY = "lyric_translation_openai_api_key"
    private const val KEY_TRANSLATION_OPENAI_MODEL = "lyric_translation_openai_model"
    private const val KEY_TRANSLATION_OPENAI_BASE_URL = "lyric_translation_openai_base_url"
    private const val KEY_TRANSLATION_CACHE_SIZE = "lyric_translation_cache_size"
    private const val KEY_TRANSLATION_IGNORE_REGEX = "lyric_translation_ignore_regex"
    private const val KEY_TRANSLATION_CUSTOM_PROMPT = "lyric_translation_custom_prompt"

    fun buildSettingsSnapshot(): LyricSettingsSnapshot {
        val baseStyle = BasicStyle().apply { load(basicStylePrefs) }
        val enabledPackages = getEnabledPackageNames()
        val configuredPackages =
            getConfiguredPackageNames()
                .toMutableSet()
                .apply {
                    add(DEFAULT_PACKAGE_NAME)
                    addAll(enabledPackages)
                }

        val packageStyles = configuredPackages.associateWith { packageName ->
            val prefs = getSharedPreferences(getPackagePrefName(packageName))
            PackageStyle().apply { load(prefs) }
        }

        val translationConfigs = configuredPackages.associateWith { packageName ->
            val prefs = getSharedPreferences(getPackagePrefName(packageName))
            readTranslationConfig(prefs)
        }

        return LyricSettingsSnapshot(
            defaultPackageName = DEFAULT_PACKAGE_NAME,
            baseStyle = baseStyle,
            packageStyles = packageStyles,
            enabledPackages = enabledPackages,
            translationConfigs = translationConfigs
        )
    }

    private fun readTranslationConfig(prefs: SharedPreferences): TranslationConfig {
        val provider =
            prefs.getString(KEY_TRANSLATION_PROVIDER, TranslationDefaults.PROVIDER_OPENAI)
                ?: TranslationDefaults.PROVIDER_OPENAI

        val model =
            prefs.getString(KEY_TRANSLATION_OPENAI_MODEL, null)
                ?: TranslationDefaults.defaultModel(provider)

        val baseUrl =
            prefs.getString(KEY_TRANSLATION_OPENAI_BASE_URL, null)
                ?: TranslationDefaults.defaultBaseUrl(provider)

        val maxCacheSize = prefs.getString(KEY_TRANSLATION_CACHE_SIZE, null)
            ?.toIntOrNull() ?: TranslationDefaults.DEFAULT_CACHE_SIZE

        val ignoreRegex =
            prefs.getString(KEY_TRANSLATION_IGNORE_REGEX, null)
                ?: TranslationDefaults.DEFAULT_IGNORE_REGEX

        val customPrompt =
            prefs.getString(KEY_TRANSLATION_CUSTOM_PROMPT, null)
                ?: io.github.proify.lyricon.common.Constants.DEFAULT_TRANSLATION_CUSTOM_PROMPT

        return TranslationConfig(
            enabled = prefs.getBoolean(KEY_TRANSLATION_ENABLED, false),
            provider = provider,
            targetLanguage = prefs.getString(
                KEY_TRANSLATION_TARGET_LANGUAGE,
                TranslationDefaults.DEFAULT_TARGET_LANGUAGE
            ) ?: TranslationDefaults.DEFAULT_TARGET_LANGUAGE,
            apiKey = prefs.getString(KEY_TRANSLATION_OPENAI_API_KEY, null) ?: "",
            model = model,
            baseUrl = baseUrl,
            maxCacheSize = maxCacheSize,
            ignoreRegex = ignoreRegex,
            customPrompt = customPrompt
        )
    }
}
