/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.os.SystemClock
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeEncode
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.style.TranslationDebugInfo
import io.github.proify.lyricon.xposed.systemui.util.LyricPrefs
import com.highcapable.yukihookapi.hook.xposed.channel.YukiHookDataChannel

object TranslationDebugReporter {
    private const val MAX_LOG_LINES = 120

    @Volatile
    private var channel: YukiHookDataChannel.NameSpace? = null

    private val logBuffer = ArrayDeque<String>()
    private var state: String = "Idle"
    private var detail: String? = null
    private var provider: String? = null
    private var model: String? = null
    private var targetLanguage: String? = null
    private var songName: String? = null
    private var songArtist: String? = null
    private var lastRequestDurationMs: Long? = null
    private var lastPromptTokens: Int? = null
    private var lastCompletionTokens: Int? = null
    private var lastTotalTokens: Int? = null
    private var updatedAt: Long = 0L

    fun initialize(channel: YukiHookDataChannel.NameSpace) {
        this.channel = channel
        emit()
    }

    fun updateState(
        state: String,
        detail: String? = null,
        song: Song? = null,
        settings: LyricPrefs.TranslationSettings? = null
    ) {
        this.state = state
        this.detail = detail
        this.updatedAt = SystemClock.uptimeMillis()
        if (song != null) {
            songName = song.name
            songArtist = song.artist
        }
        if (settings != null) {
            provider = settings.provider
            model = settings.model
            targetLanguage = settings.targetLanguage
        }
        emit()
    }

    fun appendLog(message: String) {
        if (message.isBlank()) return
        if (logBuffer.size >= MAX_LOG_LINES) {
            while (logBuffer.size >= MAX_LOG_LINES) {
                logBuffer.removeFirstOrNull()
            }
        }
        logBuffer.addLast("${SystemClock.uptimeMillis()}: $message")
        updatedAt = SystemClock.uptimeMillis()
        emit()
    }

    fun updateRequestStats(
        durationMs: Long?,
        promptTokens: Int?,
        completionTokens: Int?,
        totalTokens: Int?
    ) {
        lastRequestDurationMs = durationMs
        lastPromptTokens = promptTokens
        lastCompletionTokens = completionTokens
        lastTotalTokens = totalTokens
        updatedAt = SystemClock.uptimeMillis()
        emit()
    }

    private fun emit() {
        val snapshot = TranslationDebugInfo(
            state = state,
            detail = detail,
            provider = provider,
            model = model,
            targetLanguage = targetLanguage,
            songName = songName,
            songArtist = songArtist,
            lastRequestDurationMs = lastRequestDurationMs,
            lastPromptTokens = lastPromptTokens,
            lastCompletionTokens = lastCompletionTokens,
            lastTotalTokens = lastTotalTokens,
            updatedAt = updatedAt,
            logLines = logBuffer.toList()
        )
        val payload = json.safeEncode(snapshot).toByteArray(Charsets.UTF_8)
        channel?.put(AppBridgeConstants.REQUEST_TRANSLATION_DEBUG_INFO, payload)
    }
}
