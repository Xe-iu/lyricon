/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import kotlinx.serialization.Serializable

@Serializable
data class TranslationConfig(
    val enabled: Boolean = false,
    val provider: String = TranslationDefaults.PROVIDER_OPENAI,
    val targetLanguage: String = TranslationDefaults.DEFAULT_TARGET_LANGUAGE,
    val apiKey: String = "",
    val model: String = TranslationDefaults.DEFAULT_OPENAI_MODEL,
    val baseUrl: String = TranslationDefaults.DEFAULT_OPENAI_BASE_URL,
    val maxCacheSize: Int = TranslationDefaults.DEFAULT_CACHE_SIZE,
    val ignoreRegex: String = TranslationDefaults.DEFAULT_IGNORE_REGEX,
    val customPrompt: String = io.github.proify.lyricon.common.Constants.DEFAULT_TRANSLATION_CUSTOM_PROMPT
)
