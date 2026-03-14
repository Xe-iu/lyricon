/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import kotlinx.serialization.Serializable

@Serializable
data class TranslationDebugInfo(
    val state: String,
    val detail: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val targetLanguage: String? = null,
    val songName: String? = null,
    val songArtist: String? = null,
    val lastRequestDurationMs: Long? = null,
    val lastPromptTokens: Int? = null,
    val lastCompletionTokens: Int? = null,
    val lastTotalTokens: Int? = null,
    val updatedAt: Long = 0L,
    val logLines: List<String> = emptyList()
)
