/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.app.bridge

object AppBridgeConstants {
    const val REQUEST_UPDATE_LYRIC_STYLE: String = "request_update_lyric_style"
    const val REQUEST_SYNC_SETTINGS_SNAPSHOT: String = "request_sync_settings_snapshot"
    const val REQUEST_HIGHLIGHT_VIEW: String = "request_highlight_view"
    const val REQUEST_TRANSLATION_DEBUG_INFO: String = "request_translation_debug_info"
    const val REQUEST_TRANSLATION_CACHE_LIST: String = "request_translation_cache_list"
    const val REQUEST_TRANSLATION_CACHE_LIST_CALLBACK: String = REQUEST_TRANSLATION_CACHE_LIST + "_callback"
    const val REQUEST_TRANSLATION_CACHE_DETAIL: String = "request_translation_cache_detail"
    const val REQUEST_TRANSLATION_CACHE_DETAIL_CALLBACK: String = REQUEST_TRANSLATION_CACHE_DETAIL + "_callback"
    const val REQUEST_TRANSLATION_CACHE_DELETE: String = "request_translation_cache_delete"
    const val REQUEST_TRANSLATION_CACHE_CLEAR: String = "request_translation_cache_clear"
    const val REQUEST_TRANSLATION_CACHE_EXPORT: String = "request_translation_cache_export"
    const val REQUEST_TRANSLATION_CACHE_EXPORT_CALLBACK: String = REQUEST_TRANSLATION_CACHE_EXPORT + "_callback"
    const val REQUEST_TRANSLATION_CACHE_IMPORT: String = "request_translation_cache_import"

    const val REQUEST_CHECK_SAFE_MODE: String = "request_check_safe_mode"
    const val REQUEST_CHECK_SAFE_MODE_CALLBACK: String = REQUEST_CHECK_SAFE_MODE + "_callback"

    const val REQUEST_VIEW_TREE: String = "request_view_tree"
    const val REQUEST_VIEW_TREE_CALLBACK: String = REQUEST_VIEW_TREE + "_callback"
}
