/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import kotlinx.serialization.Serializable

@Serializable
data class TranslationCacheLine(
    val source: String,
    val translated: String?
)

@Serializable
data class TranslationCacheEntrySummary(
    val id: String,
    val packageName: String,
    val provider: String,
    val model: String,
    val targetLanguage: String,
    val songName: String? = null,
    val songArtist: String? = null,
    val createdAt: Long,
    val size: Int,
    val coverFileName: String? = null
)

@Serializable
data class TranslationCacheEntry(
    val id: String,
    val packageName: String,
    val provider: String,
    val model: String,
    val targetLanguage: String,
    val songName: String? = null,
    val songArtist: String? = null,
    val createdAt: Long,
    val size: Int,
    val coverFileName: String? = null,
    val lines: List<TranslationCacheLine>
)

@Serializable
data class TranslationCacheEntryDetail(
    val entry: TranslationCacheEntry,
    val coverBase64: String? = null
)
