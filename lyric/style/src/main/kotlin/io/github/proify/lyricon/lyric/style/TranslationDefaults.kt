/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

object TranslationDefaults {
    const val PROVIDER_OPENAI: String = "openai"
    const val PROVIDER_GEMINI: String = "gemini"
    const val PROVIDER_CLAUDE: String = "claude"
    const val PROVIDER_DEEPSEEK: String = "deepseek"
    const val PROVIDER_QWEN: String = "qwen"

    val SUPPORTED_PROVIDERS: Set<String> = setOf(
        PROVIDER_OPENAI,
        PROVIDER_GEMINI,
        PROVIDER_CLAUDE,
        PROVIDER_DEEPSEEK,
        PROVIDER_QWEN
    )

    const val DEFAULT_TARGET_LANGUAGE: String = "简体中文"
    const val DEFAULT_OPENAI_MODEL: String = "gpt-4o-mini"
    const val DEFAULT_GEMINI_MODEL: String = "gemini-2.0-flash"
    const val DEFAULT_CLAUDE_MODEL: String = "claude-3-5-haiku-latest"
    const val DEFAULT_DEEPSEEK_MODEL: String = "deepseek-chat"
    const val DEFAULT_QWEN_MODEL: String = "qwen-plus"

    const val DEFAULT_OPENAI_BASE_URL: String = "https://api.openai.com/v1/chat/completions"
    const val DEFAULT_GEMINI_BASE_URL: String = "https://generativelanguage.googleapis.com/v1beta/models"
    const val DEFAULT_CLAUDE_BASE_URL: String = "https://api.anthropic.com/v1/messages"
    const val DEFAULT_DEEPSEEK_BASE_URL: String = "https://api.deepseek.com/v1/chat/completions"
    const val DEFAULT_QWEN_BASE_URL: String =
        "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

    const val DEFAULT_CACHE_SIZE: Int = 5000
    const val DEFAULT_IGNORE_REGEX: String = "^[\\p{Han}\\p{P}\\s]+$"

    fun defaultModel(provider: String): String =
        when (provider) {
            PROVIDER_GEMINI -> DEFAULT_GEMINI_MODEL
            PROVIDER_CLAUDE -> DEFAULT_CLAUDE_MODEL
            PROVIDER_DEEPSEEK -> DEFAULT_DEEPSEEK_MODEL
            PROVIDER_QWEN -> DEFAULT_QWEN_MODEL
            else -> DEFAULT_OPENAI_MODEL
        }

    fun defaultBaseUrl(provider: String): String =
        when (provider) {
            PROVIDER_GEMINI -> DEFAULT_GEMINI_BASE_URL
            PROVIDER_CLAUDE -> DEFAULT_CLAUDE_BASE_URL
            PROVIDER_DEEPSEEK -> DEFAULT_DEEPSEEK_BASE_URL
            PROVIDER_QWEN -> DEFAULT_QWEN_BASE_URL
            else -> DEFAULT_OPENAI_BASE_URL
        }
}
