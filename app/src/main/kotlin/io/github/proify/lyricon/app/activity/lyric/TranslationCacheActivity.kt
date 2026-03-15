/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.activity.lyric

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.lyricon.app.LyriconApp.Companion.systemUIChannel
import io.github.proify.lyricon.app.R
import io.github.proify.lyricon.app.compose.AppToolBarListContainer
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperDialog
import io.github.proify.lyricon.app.compose.custom.miuix.extra.SuperArrow
import io.github.proify.lyricon.app.compose.custom.miuix.basic.AppBasicComponent
import io.github.proify.lyricon.app.activity.BaseActivity
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.lyric.style.TranslationCacheEntryDetail
import io.github.proify.lyricon.lyric.style.TranslationCacheEntrySummary
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import android.util.Base64
import android.graphics.BitmapFactory
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class TranslationCacheActivity : BaseActivity() {

    private val entriesState = mutableStateOf<List<TranslationCacheEntrySummary>>(emptyList())
    private val exportJsonState = mutableStateOf<String?>(null)
    private val detailState = mutableStateOf<TranslationCacheEntryDetail?>(null)
    private val showDetail = mutableStateOf(false)
    private val showExport = mutableStateOf(false)
    private val showImport = mutableStateOf(false)
    private val importText = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerChannel()
        requestList()
        setContent {
            Content(
                entriesState = entriesState,
                detailState = detailState,
                showDetail = showDetail,
                showExport = showExport,
                showImport = showImport,
                exportJsonState = exportJsonState,
                importText = importText,
                onRefresh = ::requestList,
                onExport = ::requestExport,
                onImport = ::requestImport,
                onClear = ::requestClear,
                onDelete = ::requestDelete,
                onDetail = ::requestDetail
            )
        }
    }

    private fun registerChannel() {
        systemUIChannel.wait<ByteArray>(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_LIST_CALLBACK) { data ->
            val jsonText = data.toString(Charsets.UTF_8)
            val list = json.safeDecode<List<TranslationCacheEntrySummary>>(jsonText).orEmpty()
            entriesState.value = list
        }
        systemUIChannel.wait<ByteArray>(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_DETAIL_CALLBACK) { data ->
            val jsonText = data.toString(Charsets.UTF_8)
            val detail = json.safeDecode<TranslationCacheEntryDetail>(jsonText)
            detailState.value = detail
            showDetail.value = detail != null
        }
        systemUIChannel.wait<ByteArray>(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_EXPORT_CALLBACK) { data ->
            exportJsonState.value = data.toString(Charsets.UTF_8)
            showExport.value = true
        }
    }

    private fun requestList() {
        systemUIChannel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_LIST)
    }

    private fun requestDetail(id: String) {
        systemUIChannel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_DETAIL, id)
    }

    private fun requestDelete(id: String) {
        systemUIChannel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_DELETE, id)
        requestList()
    }

    private fun requestClear() {
        systemUIChannel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_CLEAR)
        requestList()
    }

    private fun requestExport() {
        systemUIChannel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_EXPORT)
    }

    private fun requestImport(jsonText: String) {
        systemUIChannel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_IMPORT, jsonText)
        requestList()
    }
}

@Composable
private fun Content(
    entriesState: MutableState<List<TranslationCacheEntrySummary>>,
    detailState: MutableState<TranslationCacheEntryDetail?>,
    showDetail: MutableState<Boolean>,
    showExport: MutableState<Boolean>,
    showImport: MutableState<Boolean>,
    exportJsonState: MutableState<String?>,
    importText: MutableState<String>,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onImport: (String) -> Unit,
    onClear: () -> Unit,
    onDelete: (String) -> Unit,
    onDetail: (String) -> Unit
) {
    AppToolBarListContainer(
        title = stringResource(R.string.item_translation_cache_manager),
        actions = {},
    ) {
        item("actions") {
            Card(
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                SuperArrow(
                    title = stringResource(R.string.item_translation_cache_refresh),
                    onClick = onRefresh
                )
                SuperArrow(
                    title = stringResource(R.string.item_translation_cache_export),
                    onClick = onExport
                )
                SuperArrow(
                    title = stringResource(R.string.item_translation_cache_import),
                    onClick = { showImport.value = true }
                )
                SuperArrow(
                    title = stringResource(R.string.item_translation_cache_clear),
                    onClick = onClear
                )
            }
        }

        item("list_title") {
            AppBasicComponent(
                title = stringResource(R.string.item_translation_cache_list),
                summary = stringResource(R.string.item_translation_cache_list_summary)
            )
        }

        entriesState.value.forEach { entry ->
            item(entry.id) {
                val time = DateFormat.format("yyyy-MM-dd HH:mm", entry.createdAt).toString()
                val summary = buildString {
                    append(entry.songName ?: "-")
                    if (!entry.songArtist.isNullOrBlank()) append(" - ").append(entry.songArtist)
                    append("\n").append(entry.packageName)
                    append("\n").append(time)
                    append("\n").append("size=").append(entry.size)
                }
                Card(
                    modifier = Modifier
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                        .fillMaxWidth()
                ) {
                    SuperArrow(
                        title = entry.songName ?: entry.id.take(8),
                        summary = summary,
                        onClick = { onDetail(entry.id) }
                    )
                    SuperArrow(
                        title = stringResource(R.string.item_translation_cache_delete),
                        onClick = { onDelete(entry.id) }
                    )
                }
            }
        }
    }

    if (showDetail.value) {
        val detail = detailState.value
        SuperDialog(
            title = stringResource(R.string.item_translation_cache_detail),
            summary = detail?.entry?.songName ?: "",
            show = showDetail,
            onDismissRequest = { showDetail.value = false }
        ) {
            val cover = remember(detail?.coverBase64) {
                detail?.coverBase64?.let { decodeBitmap(it) }
            }
            if (cover != null) {
                androidx.compose.foundation.Image(
                    bitmap = cover,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp)
                )
            }
            val entry = detail?.entry
            val requestTime = entry?.createdAt?.let {
                DateFormat.format("yyyy-MM-dd HH:mm", it).toString()
            } ?: "-"
            Text(
                text = stringResource(R.string.item_translation_cache_field_platform) +
                        ": " + (entry?.packageName ?: "-")
            )
            Text(
                text = stringResource(R.string.item_translation_cache_field_provider) +
                        ": " + (entry?.provider ?: "-")
            )
            Text(
                text = stringResource(R.string.item_translation_cache_field_model) +
                        ": " + (entry?.model ?: "-")
            )
            Text(
                text = stringResource(R.string.item_translation_cache_field_target_language) +
                        ": " + (entry?.targetLanguage ?: "-")
            )
            Text(
                text = stringResource(R.string.item_translation_cache_field_song) +
                        ": " + (entry?.songName ?: "-")
            )
            Text(
                text = stringResource(R.string.item_translation_cache_field_artist) +
                        ": " + (entry?.songArtist ?: "-")
            )
            Text(
                text = stringResource(R.string.item_translation_cache_field_time) +
                        ": " + requestTime
            )
            Text(
                text = stringResource(R.string.item_translation_cache_field_size) +
                        ": " + (entry?.size?.toString() ?: "-")
            )
            Text(
                text = stringResource(R.string.item_translation_cache_field_lines),
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            val lines = detail?.entry?.lines.orEmpty()
            LazyColumn(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(lines.size) { idx ->
                    val line = lines[idx]
                    Text(text = line.source)
                    if (!line.translated.isNullOrBlank()) {
                        Text(text = line.translated!!)
                    }
                }
            }
            TextButton(
                text = stringResource(R.string.ok),
                onClick = { showDetail.value = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showExport.value) {
        SuperDialog(
            title = stringResource(R.string.item_translation_cache_export),
            summary = "",
            show = showExport,
            onDismissRequest = { showExport.value = false }
        ) {
            val exportText = exportJsonState.value.orEmpty()
            Text(text = exportText)
            TextButton(
                text = stringResource(R.string.item_translation_cache_copy),
                onClick = { copyText(LocalContext.current, exportText) },
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                text = stringResource(R.string.ok),
                onClick = { showExport.value = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showImport.value) {
        SuperDialog(
            title = stringResource(R.string.item_translation_cache_import),
            summary = "",
            show = showImport,
            onDismissRequest = { showImport.value = false }
        ) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = importText.value,
                onValueChange = { importText.value = it },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )
            TextButton(
                text = stringResource(R.string.item_translation_cache_import),
                onClick = {
                    onImport(importText.value)
                    showImport.value = false
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun decodeBitmap(base64: String): ImageBitmap? {
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.asImageBitmap()
    }.getOrNull()
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("translation_cache", text))
}
