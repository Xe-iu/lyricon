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
import io.github.proify.android.extensions.safeEncode
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
import java.io.File

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
    private const val KEY_TRANSLATION_BILINGUAL = "lyric_translation_bilingual"
    private const val KEY_TRANSLATION_ONLY = "lyric_translation_only_show"
    private const val KEY_TRANSLATION_WAIT_READY = "lyric_translation_wait_ready"

    private const val SNAPSHOT_DIR = "lyricon"
    private const val SNAPSHOT_FILE = "settings_snapshot.json"

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

    fun persistSettingsSnapshot(snapshot: LyricSettingsSnapshot) {
        val file = getSnapshotFile()
        val jsonText = json.safeEncode(snapshot)
        file.writeText(jsonText, Charsets.UTF_8)
    }

    fun loadSettingsSnapshot(): LyricSettingsSnapshot? {
        val file = getSnapshotFile()
        if (!file.exists()) return null
        return runCatching {
            val jsonText = file.readText(Charsets.UTF_8)
            json.safeDecode<LyricSettingsSnapshot>(jsonText)
        }.getOrNull()
    }

    fun getEffectiveSettingsSnapshot(): LyricSettingsSnapshot {
        return loadSettingsSnapshot() ?: buildSettingsSnapshot().also {
            persistSettingsSnapshot(it)
        }
    }

    fun syncPrefsFromJsonOrInit(): LyricSettingsSnapshot {
        val snapshot = getEffectiveSettingsSnapshot()
        applySnapshotToPrefs(snapshot)
        return snapshot
    }

    fun refreshSnapshotFromPrefs(): LyricSettingsSnapshot {
        val snapshot = buildSettingsSnapshot()
        persistSettingsSnapshot(snapshot)
        return snapshot
    }

    private fun getSnapshotFile(): File {
        val dir = File(LyriconApp.instance.filesDir, SNAPSHOT_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, SNAPSHOT_FILE)
    }

    fun applySnapshotToPrefs(snapshot: LyricSettingsSnapshot) {
        basicStylePrefs.editCommit {
            clear()
            snapshot.baseStyle.write(this)
        }

        val configuredPackages =
            snapshot.packageStyles.keys
                .filter { it != DEFAULT_PACKAGE_NAME }
                .toSet()
        packageStyleManager.editCommit {
            putStringSet(KEY_ENABLED_PACKAGES, snapshot.enabledPackages)
            putString(KEY_CONFIGURED_PACKAGES, configuredPackages.toJson())
        }

        snapshot.packageStyles.forEach { (packageName, style) ->
            val prefs = getSharedPreferences(getPackagePrefName(packageName))
            val translationConfig =
                snapshot.translationConfigs[packageName] ?: TranslationConfig()
            prefs.editCommit {
                clear()
                style.write(this)
                writeTranslationConfig(this, translationConfig)
            }
        }
    }

    private fun writeTranslationConfig(
        editor: SharedPreferences.Editor,
        config: TranslationConfig
    ) {
        editor.putBoolean(KEY_TRANSLATION_ENABLED, config.enabled)
        editor.putString(KEY_TRANSLATION_PROVIDER, config.provider)
        editor.putString(KEY_TRANSLATION_TARGET_LANGUAGE, config.targetLanguage)
        editor.putString(KEY_TRANSLATION_OPENAI_API_KEY, config.apiKey)
        editor.putString(KEY_TRANSLATION_OPENAI_MODEL, config.model)
        editor.putString(KEY_TRANSLATION_OPENAI_BASE_URL, config.baseUrl)
        editor.putString(KEY_TRANSLATION_CACHE_SIZE, config.maxCacheSize.toString())
        editor.putString(KEY_TRANSLATION_IGNORE_REGEX, config.ignoreRegex)
        editor.putString(KEY_TRANSLATION_CUSTOM_PROMPT, config.customPrompt)
        editor.putBoolean(KEY_TRANSLATION_BILINGUAL, config.bilingualEnabled)
        editor.putBoolean(KEY_TRANSLATION_ONLY, config.onlyShowTranslation)
        editor.putBoolean(KEY_TRANSLATION_WAIT_READY, config.waitTranslationReady)
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
        val bilingualEnabled = prefs.getBoolean(KEY_TRANSLATION_BILINGUAL, true)
        val onlyShowTranslation = prefs.getBoolean(KEY_TRANSLATION_ONLY, false)
        val waitTranslationReady =
            if (onlyShowTranslation) {
                prefs.getBoolean(KEY_TRANSLATION_WAIT_READY, true)
            } else {
                false
            }

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
            customPrompt = customPrompt,
            bilingualEnabled = bilingualEnabled,
            onlyShowTranslation = onlyShowTranslation,
            waitTranslationReady = waitTranslationReady
        )
    }
}
