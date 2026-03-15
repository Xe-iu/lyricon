/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.xposed.systemui.lyric

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.android.extensions.dp
import io.github.proify.android.extensions.setColorAlpha
import io.github.proify.lyricon.common.util.ResourceMapper
import io.github.proify.lyricon.common.util.ScreenStateMonitor
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric
import io.github.proify.lyricon.xposed.systemui.util.ClockColorMonitor
import io.github.proify.lyricon.xposed.systemui.util.OnColorChangeListener
import io.github.proify.lyricon.xposed.systemui.util.TextViewColorMonitor
import io.github.proify.lyricon.xposed.systemui.util.ViewVisibilityController
import io.github.proify.lyricon.xposed.systemui.util.LyricPrefs

/**
 * 状态栏歌词视图控制器：负责歌词视图的注入、位置锚定及显隐逻辑
 */
@SuppressLint("DiscouragedApi")
class StatusBarViewController(
    val statusBarView: ViewGroup,
    var currentLyricStyle: LyricStyle
) : ScreenStateMonitor.ScreenStateListener {

    val context: Context = statusBarView.context.applicationContext
    val visibilityController = ViewVisibilityController(statusBarView)
    val lyricView: StatusBarLyric by lazy { createLyricView(currentLyricStyle) }

    private val clockId: Int by lazy { ResourceMapper.getIdByName(context, "clock") }
    private var lastAnchor = ""
    private var lastInsertionOrder = -1
    private var internalRemoveLyricViewFlag = false
    private var lastHighlightView: View? = null
    private var userShowClock = false
    private var doubleTapSwitchEnabled = false
    private var clockView: TextView? = null
    private var lyricDoubleTapDetector: GestureDetector? = null
    private var clockDoubleTapDetector: GestureDetector? = null
    private var statusBarTouchListener: View.OnTouchListener? = null

    private var colorMonitorView: View? = null
    private var colorMonitorEnabled: Boolean = true
    private var colorMonitorSource: Int = BasicStyle.Defaults.STATUS_BAR_COLOR_SOURCE
    private val colorChangeListener = object : OnColorChangeListener {
        override fun onColorChanged(color: Int, darkIntensity: Float) {
            lyricView.apply {
                setStatusBarColor(currentStatusColor.apply {
                    this.color = color
                    this.darkIntensity = darkIntensity
                    translucentColor = color.setColorAlpha(0.5f)
                })
            }
        }
    }

    // --- 生命周期与初始化 ---
    fun onCreate() {
        statusBarView.addOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.addOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.addListener(this)
        lyricView.onPlayingChanged = { playing ->
            if (!playing) {
                setUserShowClock(false)
            }
        }

        setupDoubleTapHandlers()
        updateColorMonitor(currentLyricStyle.basicStyle)

        statusBarView.doOnAttach { checkLyricViewExists() }
        YLog.info("Lyric view created for $statusBarView")
    }

    fun onDestroy() {
        statusBarView.removeOnAttachStateChangeListener(statusBarAttachListener)
        statusBarView.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)
        lyricView.removeOnAttachStateChangeListener(lyricAttachListener)
        ScreenStateMonitor.removeListener(this)
        colorMonitorView?.let { ClockColorMonitor.setListener(it, null) }
        YLog.info("Lyric view destroyed for $statusBarView")
    }

    // --- 核心业务逻辑 ---

    /**
     * 更新歌词样式及位置，若锚点或顺序变化则重新注入视图
     */
    fun updateLyricStyle(lyricStyle: LyricStyle) {
        this.currentLyricStyle = lyricStyle
        val basicStyle = lyricStyle.basicStyle
        doubleTapSwitchEnabled = basicStyle.doubleTapSwitchClock
        updateColorMonitor(basicStyle)
        if (!doubleTapSwitchEnabled) {
            setUserShowClock(false)
        }

        val needUpdateLocation = lastAnchor != basicStyle.anchor
                || lastInsertionOrder != basicStyle.insertionOrder
                || !lyricView.isAttachedToWindow

        if (needUpdateLocation) {
            YLog.info("Lyric location changed: ${basicStyle.anchor}, order ${basicStyle.insertionOrder}")
            updateLocation(basicStyle)
        } else {
            //YLog.info("Lyric location unchanged: $lastAnchor")
        }
        lyricView.updateStyle(lyricStyle)
        val translationSettings = LyricPrefs.getActiveTranslationSettings()
        lyricView.updateDisplayTranslation(displayTranslation = translationSettings.bilingualEnabled)
        lyricView.updateTranslationDisplayConfig(
            onlyShowTranslation = translationSettings.onlyShowTranslation,
            waitReady = translationSettings.waitTranslationReady
        )
    }

    /**
     * 处理视图注入逻辑：根据 BasicStyle 寻找锚点并插入歌词视图
     */
    private fun updateLocation(baseStyle: BasicStyle) {
        val anchor = baseStyle.anchor
        val anchorId = context.resources.getIdentifier(anchor, "id", context.packageName)
        val anchorView = statusBarView.findViewById<View>(anchorId) ?: return run {
            YLog.error("Lyric anchor view $anchor not found")
        }

        val anchorParent = anchorView.parent as? ViewGroup ?: return run {
            YLog.error("Lyric anchor parent not found")
        }

        // 标记内部移除，避免触发冗余的 detach 逻辑
        internalRemoveLyricViewFlag = true

        (lyricView.parent as? ViewGroup)?.removeView(lyricView)

        val anchorIndex = anchorParent.indexOfChild(anchorView)
        val lp = lyricView.layoutParams ?: ViewGroup.LayoutParams(
            baseStyle.width.dp,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 执行插入：在前或在后
        val targetIndex =
            if (baseStyle.insertionOrder == BasicStyle.INSERTION_ORDER_AFTER) anchorIndex + 1 else anchorIndex
        anchorParent.addView(lyricView, targetIndex, lp)

        lyricView.updateVisibility()
        lastAnchor = anchor
        lastInsertionOrder = baseStyle.insertionOrder
        internalRemoveLyricViewFlag = false

        YLog.info("Lyric injected: anchor $anchor, index $targetIndex")
    }

    fun checkLyricViewExists() {
        if (lyricView.isAttachedToWindow) return
        lastAnchor = ""
        lastInsertionOrder = -1
        updateLyricStyle(currentLyricStyle)
    }

    // --- 辅助方法 ---

    private fun getClockView(): View? = statusBarView.findViewById(clockId)

    private fun createLyricView(style: LyricStyle) =
        StatusBarLyric(context, style, getClockView() as? TextView)

    private fun setupDoubleTapHandlers() {
        clockView = getClockView() as? TextView
        if (lyricDoubleTapDetector == null) {
            lyricDoubleTapDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (!doubleTapSwitchEnabled || !LyricViewController.isPlaying) return false
                        setUserShowClock(true)
                        return true
                    }
                }
            )
        }

        if (clockDoubleTapDetector == null) {
            clockDoubleTapDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (!doubleTapSwitchEnabled || !LyricViewController.isPlaying) return false
                        setUserShowClock(false)
                        return true
                    }
                }
            )
        }

        if (statusBarTouchListener == null) {
            statusBarTouchListener = View.OnTouchListener { _, event ->
                if (!doubleTapSwitchEnabled || !LyricViewController.isPlaying) return@OnTouchListener false
                if (isTouchInside(lyricView, event)) {
                    lyricDoubleTapDetector?.onTouchEvent(event)
                } else {
                    clockView?.let { clock ->
                        if (isTouchInside(clock, event)) {
                            clockDoubleTapDetector?.onTouchEvent(event)
                        }
                    }
                }
                false
            }
            statusBarView.setOnTouchListener(statusBarTouchListener)
        }
    }

    private fun updateColorMonitor(basicStyle: BasicStyle) {
        colorMonitorEnabled = basicStyle.listenStatusBarColor
        colorMonitorSource = basicStyle.statusBarColorSource
        if (!colorMonitorEnabled) {
            (colorMonitorView as? TextView)?.let {
                ClockColorMonitor.setListener(it, null)
                TextViewColorMonitor.setListener(it, null)
            }
            colorMonitorView = null
            return
        }

        val target = when (colorMonitorSource) {
            BasicStyle.COLOR_SOURCE_ANCHOR -> findAnchorTextView(basicStyle.anchor)
            else -> getClockView() as? TextView
        } ?: getClockView() as? TextView ?: return

        if (colorMonitorSource == BasicStyle.COLOR_SOURCE_ANCHOR) {
            TextViewColorMonitor.hook()
        } else {
            ClockColorMonitor.hook()
        }

        if (colorMonitorView === target) return
        (colorMonitorView as? TextView)?.let {
            ClockColorMonitor.setListener(it, null)
            TextViewColorMonitor.setListener(it, null)
        }
        colorMonitorView = target
        if (colorMonitorSource == BasicStyle.COLOR_SOURCE_ANCHOR) {
            TextViewColorMonitor.setListener(target, colorChangeListener)
        } else {
            ClockColorMonitor.setListener(target, colorChangeListener)
        }
    }

    private fun findAnchorTextView(anchor: String?): TextView? {
        if (anchor.isNullOrBlank()) return null
        val anchorId = context.resources.getIdentifier(anchor, "id", context.packageName)
        if (anchorId == 0) return null
        return statusBarView.findViewById(anchorId) as? TextView
    }

    private fun setUserShowClock(show: Boolean) {
        if (userShowClock == show) return
        userShowClock = show
        lyricView.setUserHideLyric(show)
        lyricView.updateVisibility()
        if (show) {
            visibilityController.applyVisibilityRules(
                rules = currentLyricStyle.basicStyle.visibilityRules,
                isPlaying = false
            )
        }
    }

    private fun isTouchInside(view: View, event: MotionEvent): Boolean {
        if (!view.isShown) return false
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return false

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        val right = left + width
        val bottom = top + height
        return event.rawX in left..right && event.rawY in top..bottom
    }

    fun highlightView(idName: String?) {
        lastHighlightView?.background = null
        if (idName.isNullOrBlank()) return

        val id = ResourceMapper.getIdByName(context, idName)
        statusBarView.findViewById<View>(id)?.let { view ->
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor("#FF3582FF".toColorInt())
                cornerRadius = 20.dp.toFloat()
            }
            lastHighlightView = view
        } ?: YLog.error("Highlight target $idName not found")
    }

    // --- 监听器实现 ---

    private val onGlobalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        private var lastVisible: Boolean? = null

        override fun onGlobalLayout() {
            if (clockView == null) {
                setupDoubleTapHandlers()
            }
            if (colorMonitorEnabled && colorMonitorView == null) {
                updateColorMonitor(currentLyricStyle.basicStyle)
            }
            val shouldLyricViewVisible = lyricView.isVisible

            var visible = LyricViewController.isPlaying && when {
                lyricView.isDisabledVisible -> !lyricView.isHideOnLockScreen()
                shouldLyricViewVisible -> true
                else -> false
            }

            if (lastVisible == false && !visible) return

            visibilityController.applyVisibilityRules(
                rules = currentLyricStyle.basicStyle.visibilityRules,
                isPlaying = if (userShowClock) false else visible
            )
            lastVisible = if (userShowClock) false else visible
            //YLog.info("applyVisibilityRules: $visible")
        }
    }

    private val lyricAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            YLog.info("LyricView attached")
        }

        override fun onViewDetachedFromWindow(v: View) {
            YLog.info("LyricView detached")
            if (!internalRemoveLyricViewFlag) {
                checkLyricViewExists()
            } else {
                YLog.info("LyricView detached by internal flag")
            }
        }
    }

    private val statusBarAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) {}
    }

    override fun onScreenOn() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
    }

    override fun onScreenOff() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = true
    }

    override fun onScreenUnlocked() {
        lyricView.updateVisibility()
        lyricView.isSleepMode = false
        if (LyricViewController.isPlaying && lyricView.isVisible) {
            LyricViewController.getLastPositionSnapshot()?.let { lyricView.setPosition(it) }
        }
    }

    fun onDisableStateChanged(shouldHide: Boolean) {
        lyricView.isDisabledVisible = shouldHide
    }

    override fun equals(other: Any?): Boolean =
        (this === other) || (other is StatusBarViewController && statusBarView === other.statusBarView)

    override fun hashCode(): Int = 31 * 17 + statusBarView.hashCode()
}
