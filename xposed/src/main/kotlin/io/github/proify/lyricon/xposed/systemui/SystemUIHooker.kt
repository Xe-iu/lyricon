/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.android.extensions.deflate
import io.github.proify.android.extensions.inflate
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.android.extensions.safeEncode
import io.github.proify.lyricon.app.bridge.AppBridgeConstants
import io.github.proify.lyricon.central.BridgeCentral
import io.github.proify.lyricon.central.provider.player.ActivePlayerDispatcher
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.common.util.ViewHierarchyParser
import io.github.proify.lyricon.xposed.systemui.lyric.LyricViewController
import io.github.proify.lyricon.xposed.systemui.lyric.StatusBarViewController
import io.github.proify.lyricon.xposed.systemui.lyric.StatusBarViewManager
import io.github.proify.lyricon.xposed.systemui.lyric.TranslationDebugReporter
import io.github.proify.lyricon.xposed.systemui.lyric.AutoTranslationManager
import io.github.proify.lyricon.xposed.systemui.util.CrashDetector
import io.github.proify.lyricon.xposed.systemui.util.LyricPrefs
import io.github.proify.lyricon.xposed.systemui.util.NotificationCoverHelper
import io.github.proify.lyricon.xposed.systemui.util.OplusCapsuleHooker
import io.github.proify.lyricon.xposed.systemui.util.StatusBarDisableHooker
import io.github.proify.lyricon.xposed.systemui.util.StatusBarDisableHooker.OnStatusBarDisableListener
import io.github.proify.lyricon.xposed.systemui.util.ViewVisibilityTracker

object SystemUIHooker : YukiBaseHooker() {
    private const val TEST_CRASH = false

    private var layoutInflaterResult: YukiMemberHookCreator.MemberHookCreator.Result? = null
    private var safeMode = false

    override fun onHook() {
        onAppLifecycle {
            onCreate { onPreAppCreate() }
        }
    }

    private fun onPreAppCreate() {
        YLog.info("onPreAppCreate")
        val context = appContext ?: return

        CrashDetector.getInstance(context).apply {
            record()
            if (isContinuousCrash()) {
                safeMode = true
                YLog.error("检测到连续崩溃，已停止hook")
            }
            if (safeMode) reset()
        }

        initCrashDataChannel()
        if (!safeMode) onAppCreate()
    }

    @SuppressLint("DiscouragedApi")
    private fun onAppCreate() {
        YLog.info("onAppCreate")
        val context = appContext ?: return

        initialize()

        val statusBarLayoutId =
            context.resources.getIdentifier("status_bar", "layout", context.packageName)

        layoutInflaterResult = LayoutInflater::class.resolve()
            .firstMethod {
                name = "inflate"; parameters(
                Int::class.java,
                ViewGroup::class.java,
                Boolean::class.java
            )
            }
            .hook {
                after {
                    if (args(0).int() != statusBarLayoutId) return@after
                    result<ViewGroup>()?.let { addStatusBarView(it) }
                }
            }
    }

    private fun initialize() {
        YLog.info("onInit")
        val context = appContext ?: return

        ScreenStateMonitor.initialize(context)
        OplusCapsuleHooker.initialize(context.classLoader)
        BridgeCentral.initialize(context)
        NotificationCoverHelper.initialize(context.classLoader)
        ViewVisibilityTracker.initialize(context.classLoader)
        initDataChannel()
        TranslationDebugReporter.initialize(dataChannel)
        ActivePlayerDispatcher.addActivePlayerListener(LyricViewController)

        StatusBarDisableHooker.inject(context.classLoader)
        StatusBarDisableHooker.addListener(object : OnStatusBarDisableListener {

            private var lastDisableStateChanged: Boolean? = null
            override fun onDisableStateChanged(
                shouldHide: Boolean,
                animate: Boolean
            ) {
                if (lastDisableStateChanged == shouldHide) return
                lastDisableStateChanged = shouldHide
                StatusBarViewManager.forEach {
                    it.onDisableStateChanged(shouldHide)
                }
            }
        })
    }

    private fun initDataChannel() {
        val channel = dataChannel
        channel.wait<ByteArray>(key = AppBridgeConstants.REQUEST_SYNC_SETTINGS_SNAPSHOT) { data ->
            runCatching {
                val jsonText = data.inflate().toString(Charsets.UTF_8)
                val snapshot = json.safeDecode<io.github.proify.lyricon.lyric.style.LyricSettingsSnapshot>(jsonText)
                LyricPrefs.updateSettingsSnapshot(snapshot)
                StatusBarViewManager.forEach { it.updateLyricStyle(LyricPrefs.getLyricStyle()) }
            }.onFailure {
                YLog.error("apply settings snapshot failed", it)
            }
        }
        channel.wait(key = AppBridgeConstants.REQUEST_UPDATE_LYRIC_STYLE) {
            StatusBarViewManager.forEach { it.updateLyricStyle(LyricPrefs.getLyricStyle()) }
        }
        channel.wait(key = AppBridgeConstants.REQUEST_TRANSLATION_CACHE_LIST) {
            val list = AutoTranslationManager.getCacheEntrySummaries()
            val payload = json.safeEncode(list).toByteArray(Charsets.UTF_8)
            channel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_LIST_CALLBACK, payload)
        }
        channel.wait<String>(key = AppBridgeConstants.REQUEST_TRANSLATION_CACHE_DETAIL) { id ->
            val detail = AutoTranslationManager.getCacheEntryDetail(id)
            val payload = json.safeEncode(detail).toByteArray(Charsets.UTF_8)
            channel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_DETAIL_CALLBACK, payload)
        }
        channel.wait<String>(key = AppBridgeConstants.REQUEST_TRANSLATION_CACHE_DELETE) { id ->
            AutoTranslationManager.deleteCacheEntry(id)
        }
        channel.wait(key = AppBridgeConstants.REQUEST_TRANSLATION_CACHE_CLEAR) {
            AutoTranslationManager.clearCacheEntries()
        }
        channel.wait(key = AppBridgeConstants.REQUEST_TRANSLATION_CACHE_EXPORT) {
            val payload = AutoTranslationManager.exportCacheEntries().toByteArray(Charsets.UTF_8)
            channel.put(AppBridgeConstants.REQUEST_TRANSLATION_CACHE_EXPORT_CALLBACK, payload)
        }
        channel.wait<String>(key = AppBridgeConstants.REQUEST_TRANSLATION_CACHE_IMPORT) { jsonText ->
            AutoTranslationManager.importCacheEntries(jsonText)
        }
        channel.wait<String>(key = AppBridgeConstants.REQUEST_HIGHLIGHT_VIEW) { id ->
            StatusBarViewManager.forEach { it.highlightView(id) }
        }
        channel.wait<String>(key = AppBridgeConstants.REQUEST_VIEW_TREE) {
            StatusBarViewManager.forEach { controller ->
                val data =
                    json.safeEncode(ViewHierarchyParser.buildNodeTree(controller.statusBarView))
                        .toByteArray(Charsets.UTF_8).deflate()
                channel.put(AppBridgeConstants.REQUEST_VIEW_TREE_CALLBACK, data)
                return@forEach
            }
        }
    }

    private fun addStatusBarView(view: ViewGroup) {
        view.doOnAttach {
            val controller = StatusBarViewController(view, LyricPrefs.getLyricStyle())
            StatusBarViewManager.add(controller)

            val isFirst = StatusBarViewManager.controllers.size == 1
            if (isFirst) {
                BridgeCentral.sendBootCompleted()
                if (TEST_CRASH) view.postDelayed({ error("test crash") }, 3000)
            }
        }
    }

    private fun initCrashDataChannel() {
        dataChannel.put(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE_CALLBACK, safeMode)
        dataChannel.wait(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE) {
            dataChannel.put(AppBridgeConstants.REQUEST_CHECK_SAFE_MODE_CALLBACK, safeMode)
        }
    }
}
