/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.statusbarlyric

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.contains
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import io.github.proify.android.extensions.dp
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.lyric.model.interfaces.IRichLyricLine
import io.github.proify.lyricon.lyric.style.BasicStyle
import io.github.proify.lyricon.lyric.style.LogoStyle
import io.github.proify.lyricon.lyric.style.LyricStyle
import io.github.proify.lyricon.lyric.view.LayoutTransitionX
import io.github.proify.lyricon.lyric.view.LyricPlayerView
import io.github.proify.lyricon.lyric.view.visibleIfChanged
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric.LyricType.NONE
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric.LyricType.SONG
import io.github.proify.lyricon.statusbarlyric.StatusBarLyric.LyricType.TEXT
import kotlin.math.max

@SuppressLint("ViewConstructor")
class StatusBarLyric(
    context: Context,
    initialStyle: LyricStyle,
    linkedTextView: TextView?
) : LinearLayout(context) {

    companion object {
        const val VIEW_TAG: String = "lyricon:lyric_view"
        private const val TAG = "StatusBarLyric"
        private const val JANK_FRAME_THRESHOLD_NS: Long = 48_000_000L
        private const val JANK_CLEAR_COOLDOWN_MS: Long = 300L
        private const val JANK_CLEAR_AFTER_FRAMES: Int = 3
        private const val WORKER_THREAD_NAME = "LyriconStatusBarWorker"
        @Volatile private var workerThread: HandlerThread? = null
        @Volatile private var workerHandler: Handler? = null
        @Volatile private var workerRefCount: Int = 0

        private fun acquireWorker(): Handler {
            val existing = workerHandler
            if (existing != null) {
                workerRefCount++
                return existing
            }
            val thread = HandlerThread(WORKER_THREAD_NAME).apply { start() }
            workerThread = thread
            val handler = Handler(thread.looper)
            workerHandler = handler
            workerRefCount = 1
            return handler
        }

        private fun releaseWorker() {
            val count = workerRefCount - 1
            workerRefCount = count
            if (count > 0) return
            workerRefCount = 0
            workerHandler = null
            workerThread?.quitSafely()
            workerThread = null
        }
    }

    val logoView: SuperLogo = SuperLogo(context).apply {
        this.linkedTextView = linkedTextView
    }

    val textView: SuperText = SuperText(context).apply {
        this.linkedTextView = linkedTextView
        eventListener = object : SuperText.EventListener {
            override fun enteringInterludeMode(duration: Long) {
                logoView.syncProgress(0, duration)
            }

            override fun exitInterludeMode() {
                logoView.clearProgress()
            }
        }
    }

    // --- 对外状态 ---

    var currentStatusColor: StatusColor = StatusColor()
        private set

    var isSleepMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            Log.d(TAG, "休眠模式：$value")
            if (value) {
                pendingSleepData = PendingData()
                textView.setAnimationsEnabled(false)
            } else {
                pendingSleepData?.let { seekTo(it.position) }
                pendingSleepData = null
                textView.setAnimationsEnabled(true)
            }
        }

    // --- 样式 / 播放状态 ---

    private var currentStyle: LyricStyle = initialStyle
    private var isPlaying: Boolean = false
    private var isOplusCapsuleShowing: Boolean = false
    private var userHideLyric: Boolean = false

    // 上一次 Logo gravity，用于避免重复重排
    private var lastLogoGravity: Int = -114

    // 休眠期间缓存的进度数据
    private var pendingSleepData: PendingData? = null

    // --- 歌词内容与超时状态 ---

    private var hasLyricContent: Boolean = false
    private var lyricTimedOut: Boolean = false
    private var currentLyric: String? = null

    // 主线程调度器
    private val mainHandler: Handler = Handler(context.mainLooper)
    private var localWorkerHandler: Handler? = null

    // 当前生效的超时 Runnable
    private var lyricTimeoutTask: Runnable? = null
    @Volatile private var lyricTimeoutVersion: Int = 0

    // 跟随系统隐藏状态栏内容
    var isDisabledVisible = false
        set(value) {
            field = value
            updateVisibility()
        }

    var onPlayingChanged: ((Boolean) -> Unit)? = null

    private val driftHandler: Handler = Handler(context.mainLooper)
    private var driftTask: Runnable? = null
    private var driftSeed: Int = SystemClock.uptimeMillis().toInt()
    private var driftEnabled: Boolean = BasicStyle.Defaults.OLED_SHIFT_ENABLED
    private var driftMode: Int = BasicStyle.Defaults.OLED_SHIFT_MODE
    private var driftRangeDp: Float = BasicStyle.Defaults.OLED_SHIFT_RANGE_DP
    private var driftIntervalSec: Int = BasicStyle.Defaults.OLED_SHIFT_INTERVAL_SEC
    private var driftRandomIntervalMinSec: Int = BasicStyle.Defaults.OLED_SHIFT_RANDOM_MIN_SEC
    private var driftRandomIntervalMaxSec: Int = BasicStyle.Defaults.OLED_SHIFT_RANDOM_MAX_SEC
    private var lastDriftX: Float = 0f
    private var lastDriftY: Float = 0f
    private var pendingDriftX: Float = 0f
    private var pendingDriftY: Float = 0f
    private var driftApplyScheduled: Boolean = false
    private var lastLyricDriftKey: String? = null
    private var lastLineKeyForDrift: String? = null

    private val choreographer: Choreographer = Choreographer.getInstance()
    private var lastFrameTimeNs: Long = 0L
    private var lastJankClearMs: Long = 0L
    private var jankConsecutiveCount: Int = 0
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            if (lastFrameTimeNs != 0L && isPlaying && isShown) {
                val deltaNs = frameTimeNanos - lastFrameTimeNs
                if (deltaNs > JANK_FRAME_THRESHOLD_NS) {
                    jankConsecutiveCount++
                    val nowMs = SystemClock.uptimeMillis()
                    if (jankConsecutiveCount >= JANK_CLEAR_AFTER_FRAMES
                        && nowMs - lastJankClearMs > JANK_CLEAR_COOLDOWN_MS
                    ) {
                        textView.clearAnimationsNow()
                        lastJankClearMs = nowMs
                        jankConsecutiveCount = 0
                    }
                } else {
                    jankConsecutiveCount = 0
                }
            }
            lastFrameTimeNs = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }

    // --- 系统 / 辅助组件 ---

    private val keyguardManager: KeyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    /**
     * 单次布局变更动画
     * 用于样式或尺寸突变时的过渡
     */
    private val singleLayoutTransition: LayoutTransition = LayoutTransitionX().apply {
        addTransitionListener(object : LayoutTransition.TransitionListener {

            override fun startTransition(
                transition: LayoutTransition?, container: ViewGroup?,
                view: View?, transitionType: Int
            ) = Unit

            override fun endTransition(
                transition: LayoutTransition?, container: ViewGroup?,
                view: View?, transitionType: Int
            ) {
                disableTransitionType(LayoutTransition.CHANGING)
                layoutTransition = null
            }
        })
    }

    // TextView 子视图结构变化监听，用于刷新可见性
    private val textHierarchyChangeListener = object : OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) = updateVisibility()
        override fun onChildViewRemoved(parent: View?, child: View?) = updateVisibility()
    }

    // 歌词变化监听，用于重置超时逻辑
    private val lyricCountChangeListener =
        object : LyricPlayerView.LyricCountChangeListener {

            override fun onLyricTextChanged(old: String, new: String) {
                currentLyric = new
                refreshLyricTimeoutState()
            }

            override fun onLyricChanged(
                news: List<IRichLyricLine>,
                removes: List<IRichLyricLine>
            ) {
                val last = news.lastOrNull()
                currentLyric = last?.text
                refreshLyricTimeoutState()
            }

            override fun onCurrentLineChanged(line: IRichLyricLine?) {
                if (line == null) return
                if (!driftEnabled || driftMode != BasicStyle.OLED_SHIFT_MODE_ON_LYRIC_CHANGE) return
                if (!isPlaying) return
                val key = "${line.begin}:${line.end}:${line.text}"
                val lastKey = lastLineKeyForDrift
                if (lastKey != null && key != lastKey) {
                    applyRandomDrift()
                }
                lastLineKeyForDrift = key
            }
        }

    init {
        tag = VIEW_TAG
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        layoutTransition = null

        addView(
            textView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                weight = 1f
            }
        )

        updateLogoLocation()
        applyInitialStyle(initialStyle)

        textView.setOnHierarchyChangeListener(textHierarchyChangeListener)
        textView.lyricCountChangeListeners += lyricCountChangeListener


    }

    // --- 公开 API ---

    fun updateStyle(style: LyricStyle) {
        triggerSingleTransition()
        currentStyle = style
        logoView.applyStyle(style)
        updateLogoLocation()
        textView.applyStyle(style)
        updateLayoutConfig(style)
        updateDriftConfig(style.basicStyle)

        refreshLyricTimeoutState()
        requestLayout()
    }

    fun setStatusBarColor(color: StatusColor) {
        currentStatusColor = color
        logoView.setStatusBarColor(color)
        textView.setStatusBarColor(color)
    }

    private var lastPlaying: Boolean? = null
    private var lastSong: Song? = null
    private var lastText: String? = null
    private var lyricType = NONE
    private var translationOnly: Boolean = false
    private var waitTranslationReady: Boolean = true

    fun setPlaying(playing: Boolean) {
        if (lastPlaying == playing) return
        Log.d(TAG, "setPlaying: $playing")

        lastPlaying = playing
        isPlaying = playing
        onPlayingChanged?.invoke(playing)

        if (!playing) {
            textView.reset()
            setUserHideLyric(false)
            resetDrift()
        } else {
            when (lyricType) {
                NONE -> Unit
                SONG -> setSong(lastSong)
                TEXT -> setText(lastText)
            }
            scheduleDriftIfNeeded()
        }

        refreshLyricTimeoutState()
        updateVisibility()
    }

    fun isHideOnLockScreen() =
        currentStyle.basicStyle.hideOnLockScreen && keyguardManager.isKeyguardLocked

    fun updateVisibility() {
        val shouldShow = isPlaying
                && !isHideOnLockScreen()
                && textView.shouldShow()
                && !lyricTimedOut
                && !isDisabledVisible
                && !userHideLyric

        visibleIfChanged = shouldShow

        Log.d(TAG, "updateVisibility: $shouldShow")
        Log.d(TAG, "textVisibility: ${textView.isVisible}")
    }

    fun refreshLyricState() {
        refreshLyricTimeoutState()
        textView.updateViewsVisibility()
        updateVisibility()
    }

    fun setSong(song: Song?) {
        lyricType = SONG
        lastSong = song
        textView.song = song
        hasLyricContent = !song?.lyrics.isNullOrEmpty()
        refreshLyricTimeoutState()
    }

    fun setText(text: String?) {
        lyricType = TEXT
        lastText = text

        textView.text = text
        hasLyricContent = !text.isNullOrBlank()
        refreshLyricTimeoutState()
    }

    fun seekTo(position: Long) {
        if (isSleepMode) {
            pendingSleepData?.position = position
            return
        }
        textView.seekTo(position)
        refreshLyricTimeoutState()
    }

    fun setPosition(position: Long) {
        if (isSleepMode) {
            pendingSleepData?.position = position
            return
        }
        textView.setPosition(position)
    }

    fun updateDisplayTranslation(
        displayTranslation: Boolean = textView.isDisplayTranslation,
        displayRoma: Boolean = textView.isDisplayRoma
    ) {
        textView.updateDisplayTranslation(displayTranslation, displayRoma)
    }

    fun updateTranslationDisplayConfig(onlyShowTranslation: Boolean, waitReady: Boolean) {
        if (translationOnly == onlyShowTranslation && waitTranslationReady == waitReady) return
        translationOnly = onlyShowTranslation
        waitTranslationReady = waitReady
        textView.setTranslationDisplayMode(onlyShowTranslation, waitReady)
        updateVisibility()
    }

    fun setOplusCapsuleVisibility(visible: Boolean) {
        isOplusCapsuleShowing = visible
        triggerSingleTransition()
        updateWidthInternal(currentStyle)
        logoView.oplusCapsuleShowing = visible
    }

    fun setUserHideLyric(hide: Boolean) {
        if (userHideLyric == hide) return
        userHideLyric = hide
        updateVisibility()
    }

    // --- 内部逻辑 ---

    private fun applyInitialStyle(style: LyricStyle) {
        currentStyle = style
        logoView.applyStyle(style)
        textView.applyStyle(style)
        updateLayoutConfig(style)
        updateDriftConfig(style.basicStyle)
    }

    private fun updateLogoLocation() {
        val logoStyle = currentStyle.packageStyle.logo
        val gravity = logoStyle.gravity
        if (gravity == lastLogoGravity) return
        lastLogoGravity = gravity

        if (contains(logoView)) removeView(logoView)
        val textIndex = indexOfChild(textView).coerceAtLeast(0)

        when (gravity) {
            LogoStyle.GRAVITY_START -> addView(logoView, textIndex)
            LogoStyle.GRAVITY_END -> addView(logoView, textIndex + 1)
            else -> addView(logoView, textIndex)
        }
    }

    private fun updateLayoutConfig(style: LyricStyle) {
        val basic = style.basicStyle
        val margins = basic.margins
        val paddings = basic.paddings

        ensureMarginLayoutParams().apply {
            width = calculateTargetWidth(basic).dp
            leftMargin = margins.left.dp
            topMargin = margins.top.dp
            rightMargin = margins.right.dp
            bottomMargin = margins.bottom.dp
        }

        updatePadding(
            paddings.left.dp,
            paddings.top.dp,
            paddings.right.dp,
            paddings.bottom.dp
        )
    }

    private fun updateWidthInternal(style: LyricStyle) {
        val width = calculateTargetWidth(style.basicStyle).dp
        ensureMarginLayoutParams().width = width
        requestLayout()
        Log.d(TAG, "updateWidthInternal: $width")
    }

    private fun calculateTargetWidth(basicStyle: BasicStyle): Float {
        return if (isOplusCapsuleShowing) {
            basicStyle.widthInColorOSCapsuleMode
        } else {
            basicStyle.width
        }
    }

    private fun ensureMarginLayoutParams(): MarginLayoutParams {
        val lp = layoutParams as? MarginLayoutParams
            ?: MarginLayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            )
        if (layoutParams == null) layoutParams = lp
        return lp
    }

    private fun triggerSingleTransition() {
        singleLayoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        layoutTransition = singleLayoutTransition
    }

    private fun updateDriftConfig(style: BasicStyle) {
        driftEnabled = style.oledShiftEnabled
        driftMode = style.oledShiftMode
        driftRangeDp = style.oledShiftRangeDp
        driftIntervalSec = style.oledShiftIntervalSec
        driftRandomIntervalMinSec = style.oledShiftRandomIntervalMinSec
        driftRandomIntervalMaxSec = style.oledShiftRandomIntervalMaxSec
        if (!driftEnabled) {
            resetDrift()
        } else {
            scheduleDriftIfNeeded()
        }
    }

    private fun applyDriftOnLyricChange(key: String?) {
        if (!driftEnabled || driftMode != BasicStyle.OLED_SHIFT_MODE_ON_LYRIC_CHANGE) return
        if (!isPlaying) return
        if (key != null && key == lastLyricDriftKey) return
        lastLyricDriftKey = key
        applyRandomDrift()
    }

    private fun scheduleDriftIfNeeded() {
        cancelDriftSchedule()
        if (!driftEnabled || !isPlaying) return
        when (driftMode) {
            BasicStyle.OLED_SHIFT_MODE_INTERVAL -> scheduleDrift(driftIntervalSec * 1000L)
            BasicStyle.OLED_SHIFT_MODE_RANDOM_INTERVAL -> scheduleDrift(nextRandomIntervalMs())
        }
    }

    private fun scheduleDrift(delayMs: Long) {
        if (delayMs <= 0L) return
        val task = Runnable {
            if (!driftEnabled || !isPlaying) return@Runnable
            applyRandomDrift()
            when (driftMode) {
                BasicStyle.OLED_SHIFT_MODE_INTERVAL -> scheduleDrift(driftIntervalSec * 1000L)
                BasicStyle.OLED_SHIFT_MODE_RANDOM_INTERVAL -> scheduleDrift(nextRandomIntervalMs())
            }
        }
        driftTask = task
        driftHandler.postDelayed(task, delayMs)
    }

    private fun nextRandomIntervalMs(): Long {
        val minSec = max(0, driftRandomIntervalMinSec)
        val maxSec = max(minSec, driftRandomIntervalMaxSec)
        val next = if (maxSec == minSec) minSec else nextInt(minSec, maxSec + 1)
        return next * 1000L
    }

    private fun applyRandomDrift() {
        val rangeDp = driftRangeDp.coerceAtLeast(0f)
        val rangePx = rangeDp.dp.toFloat()
        if (rangePx <= 0f) {
            scheduleDriftApply(0f, 0f)
            return
        }
        val offsetX = (nextFloat() * 2f - 1f) * rangePx
        val offsetY = (nextFloat() * 2f - 1f) * rangePx
        scheduleDriftApply(offsetX, offsetY)
    }

    private fun scheduleDriftApply(offsetX: Float, offsetY: Float) {
        pendingDriftX = offsetX
        pendingDriftY = offsetY
        if (driftApplyScheduled) return
        driftApplyScheduled = true
        postOnAnimation {
            driftApplyScheduled = false
            if (pendingDriftX == lastDriftX && pendingDriftY == lastDriftY) return@postOnAnimation
            translationX = pendingDriftX
            translationY = pendingDriftY
            lastDriftX = pendingDriftX
            lastDriftY = pendingDriftY
        }
    }

    private fun nextInt(min: Int, maxExclusive: Int): Int {
        val bound = maxExclusive - min
        if (bound <= 0) return min
        val r = nextInt()
        val m = r % bound
        return min + if (m < 0) m + bound else m
    }

    private fun nextFloat(): Float {
        val v = nextInt().ushr(1)
        return v / Int.MAX_VALUE.toFloat()
    }

    private fun nextInt(): Int {
        var x = driftSeed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        driftSeed = x
        return x
    }

    private fun resetDrift() {
        cancelDriftSchedule()
        translationX = 0f
        translationY = 0f
        lastLyricDriftKey = null
        lastLineKeyForDrift = null
    }

    private fun cancelDriftSchedule() {
        driftTask?.let { driftHandler.removeCallbacks(it) }
        driftTask = null
    }

    private fun refreshLyricTimeoutState() {
        val version = ++lyricTimeoutVersion
        val basicStyleConfig = currentStyle.basicStyle
        val hasLyric = hasLyricContent
        val lyric = currentLyric
        val keywordPatterns = basicStyleConfig.keywordsHidePattern.orEmpty()

        val handler = localWorkerHandler ?: mainHandler
        handler.post {
            val timeoutSec = computeTimeoutSec(
                basicStyleConfig = basicStyleConfig,
                hasLyric = hasLyric,
                lyric = lyric,
                keywordPatterns = keywordPatterns
            )

            mainHandler.post {
                if (lyricTimeoutVersion != version) return@post
                applyTimeoutState(timeoutSec)
            }
        }
    }

    private fun computeTimeoutSec(
        basicStyleConfig: BasicStyle,
        hasLyric: Boolean,
        lyric: String?,
        keywordPatterns: List<Regex>
    ): Int {
        val noLyricTimeoutSec = basicStyleConfig.noLyricHideTimeout
        val noUpdateTimeoutSec = basicStyleConfig.noUpdateHideTimeout
        val keywordTimeoutSec = basicStyleConfig.keywordHideTimeout

        val shouldHideWhenNoLyric = noLyricTimeoutSec > 0
        val shouldHideWhenNoUpdate = noUpdateTimeoutSec > 0
        val shouldHideWhenKeywordMatched = keywordTimeoutSec > 0

        return when {
            shouldHideWhenNoLyric && !hasLyric -> noLyricTimeoutSec
            hasLyric -> {
                val keywordMatched =
                    shouldHideWhenKeywordMatched && !lyric.isNullOrEmpty() &&
                            keywordPatterns.any { it.containsMatchIn(lyric) }

                when {
                    keywordMatched -> keywordTimeoutSec
                    shouldHideWhenNoUpdate -> noUpdateTimeoutSec
                    else -> -1
                }
            }
            else -> -1
        }
    }

    private fun applyTimeoutState(timeoutSec: Int) {
        resetLyricTimeout()
        if (timeoutSec > 0) {
            val timeoutRunnable = Runnable {
                lyricTimedOut = true
                updateVisibility()
            }
            lyricTimeoutTask = timeoutRunnable
            mainHandler.postDelayed(timeoutRunnable, timeoutSec * 1000L)
        }
        updateVisibility()
    }

    private fun resetLyricTimeout() {
        lyricTimedOut = false
        lyricTimeoutTask?.let { mainHandler.removeCallbacks(it) }
        lyricTimeoutTask = null
    }

    private class PendingData(var position: Long = 0)

    private enum class LyricType {
        NONE, SONG, TEXT
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (localWorkerHandler == null) {
            localWorkerHandler = acquireWorker()
        }
        lastFrameTimeNs = 0L
        lastJankClearMs = 0L
        jankConsecutiveCount = 0
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        resetLyricTimeout()
        cancelDriftSchedule()
        textView.setOnHierarchyChangeListener(null)
        textView.lyricCountChangeListeners -= lyricCountChangeListener
        choreographer.removeFrameCallback(frameCallback)
        localWorkerHandler = null
        releaseWorker()
    }
}
