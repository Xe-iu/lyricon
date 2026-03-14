/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.highcapable.yukihookapi.YukiHookAPI.Status.Executor
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.highcapable.yukihookapi.hook.xposed.channel.YukiHookDataChannel
import io.github.proify.android.extensions.json
import io.github.proify.android.extensions.safeDecode
import io.github.proify.lyricon.app.util.AppLangUtils
import io.github.proify.lyricon.app.util.LyricPrefs
import io.github.proify.lyricon.app.util.TranslationDebugStore
import io.github.proify.lyricon.common.PackageNames
import io.github.proify.lyricon.common.util.safe
import io.github.proify.lyricon.lyric.style.TranslationDebugInfo
import io.github.proify.lyricon.app.bridge.AppBridgeConstants

class LyriconApp : ModuleApplication() {

    init {
        instance = this
    }

    override fun attachBaseContext(base: Context) {
        AppLangUtils.setDefaultLocale(base)
        super.attachBaseContext(AppLangUtils.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(
            "Lyricon",
            "name: ${Executor.name}" +
                    ", type: ${Executor.type}" +
                    ", apiLevel: ${Executor.apiLevel}" +
                    ", versionName: ${Executor.versionName}" +
                    ", versionCode: ${Executor.versionCode}"
        )
        runCatching {
            LyricPrefs.syncPrefsFromJsonOrInit()
            updateRemoteLyricStyle()
        }.onFailure {
            Log.w(TAG, "sync settings on app start failed", it)
        }
        systemUIChannel.wait<ByteArray>(AppBridgeConstants.REQUEST_TRANSLATION_DEBUG_INFO) { data ->
            val jsonText = data.toString(Charsets.UTF_8)
            val info = json.safeDecode<TranslationDebugInfo>(jsonText) ?: return@wait
            TranslationDebugStore.persist(this, info)
        }
    }

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        super.getSharedPreferences(name, mode).safe()

    companion object {
        const val TAG: String = "LyriconApp"

        @SuppressLint("StaticFieldLeak")
        lateinit var instance: LyriconApp
            private set

        fun get(): LyriconApp = instance

        val packageInfo: PackageInfo by lazy {
            instance.packageManager.getPackageInfo(
                instance.packageName, 0
            )
        }
        val versionCode: Long by lazy { PackageInfoCompat.getLongVersionCode(packageInfo) }

        val systemUIChannel: YukiHookDataChannel.NameSpace by lazy {
            instance.dataChannel(packageName = PackageNames.SYSTEM_UI)
        }

        private var _safeMode: Boolean = false

        val safeMode: Boolean get() = _safeMode

        fun updateSafeMode(safeMode: Boolean) {
            _safeMode = safeMode
        }
    }
}
