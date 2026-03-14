package io.github.proify.lyricon.lyric.view.yoyo

import android.animation.Animator
import android.os.SystemClock
import android.view.View
import com.daimajia.androidanimations.library.YoYo

object YoYoAnimation {

    private const val KEY_ANIM_LOCK = 0x7F_114514
    private const val KEY_ANIM_HANDLE = 0x7F_191981
    private const val KEY_ANIM_LAYER = 0x7F_233333
    private const val KEY_ANIM_PENDING = 0x7F_233334
    private const val KEY_ANIM_LAST_TS = 0x7F_233335
    private const val ANIM_THROTTLE_MS = 120L

    private data class PendingAnim(
        val outConfig: AnimConfig,
        val inConfig: AnimConfig,
        val action: (View) -> Unit
    )

    fun <T : View> switchContent(
        target: T,
        outConfig: AnimConfig,
        inConfig: AnimConfig,
        action: (T) -> Unit
    ) {
        val now = SystemClock.uptimeMillis()
        val lastTs = target.getTag(KEY_ANIM_LAST_TS) as? Long ?: 0L
        if (now - lastTs < ANIM_THROTTLE_MS) {
            cancelAnimation(target)
            target.setTag(KEY_ANIM_LAST_TS, now)
            action(target)
            return
        }
        if (target.getTag(KEY_ANIM_LOCK) == true) {
            target.setTag(KEY_ANIM_PENDING, PendingAnim(outConfig, inConfig) { action(it as T) })
            return
        }
        cancelAnimation(target)
        target.setTag(KEY_ANIM_LOCK, true)
        target.setTag(KEY_ANIM_LAST_TS, now)
        enableHardwareLayer(target)

        val outHandle = YoYo.with(outConfig.technique)
            .duration(outConfig.duration)
            .interpolate(outConfig.interpolator)
            .withListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(p0: Animator) {}
                override fun onAnimationRepeat(p0: Animator) {}
                override fun onAnimationCancel(p0: Animator) {
                    target.setTag(KEY_ANIM_LOCK, false)
                    restoreLayer(target)
                }

                override fun onAnimationEnd(p0: Animator) {
                    if (target.getTag(KEY_ANIM_LOCK) != true) return

                    // 执行内容更新
                    val pendingForIn = target.getTag(KEY_ANIM_PENDING) as? PendingAnim
                    if (pendingForIn != null) {
                        target.setTag(KEY_ANIM_PENDING, null)
                    }
                    val effectiveAction = pendingForIn?.action ?: action
                    val effectiveInConfig = pendingForIn?.inConfig ?: inConfig

                    effectiveAction(target)

                    target.postOnAnimation {
                        if (target.getTag(KEY_ANIM_LOCK) != true) {
                            restoreLayer(target)
                            return@postOnAnimation
                        }
                        val inHandle = YoYo.with(effectiveInConfig.technique)
                            .duration(effectiveInConfig.duration)
                            .interpolate(effectiveInConfig.interpolator)
                            .withListener(object : Animator.AnimatorListener {
                                override fun onAnimationStart(p0: Animator) {}
                                override fun onAnimationRepeat(p0: Animator) {}
                                override fun onAnimationCancel(p0: Animator) {
                                    target.setTag(KEY_ANIM_LOCK, false)
                                    restoreLayer(target)
                                }

                                override fun onAnimationEnd(p0: Animator) {
                                    target.setTag(KEY_ANIM_LOCK, false)
                                    target.setTag(KEY_ANIM_HANDLE, null)
                                    restoreLayer(target)
                                    val pending = target.getTag(KEY_ANIM_PENDING) as? PendingAnim
                                    if (pending != null) {
                                        target.setTag(KEY_ANIM_PENDING, null)
                                        switchContent(target, pending.outConfig, pending.inConfig) {
                                            pending.action(it)
                                        }
                                    }
                                }
                            })
                            .playOn(target)

                        target.setTag(KEY_ANIM_HANDLE, inHandle)
                    }
                }
            })
            .playOn(target)

        target.setTag(KEY_ANIM_HANDLE, outHandle)
    }

    fun cancelAnimation(target: View) {
        val handle = target.getTag(KEY_ANIM_HANDLE) as? YoYo.YoYoString
        handle?.stop(true)
        target.setTag(KEY_ANIM_HANDLE, null)
        target.setTag(KEY_ANIM_LOCK, false)
        target.setTag(KEY_ANIM_PENDING, null)
        restoreLayer(target)
    }

    private fun enableHardwareLayer(target: View) {
        if (target.getTag(KEY_ANIM_LAYER) != null) return
        target.setTag(KEY_ANIM_LAYER, target.layerType)
        target.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun restoreLayer(target: View) {
        val previous = target.getTag(KEY_ANIM_LAYER) as? Int ?: return
        target.setLayerType(previous, null)
        target.setTag(KEY_ANIM_LAYER, null)
    }
}

/**
 * 歌词行更新扩展
 * @param preset 使用 [YoYoPresets] 中的预设组合
 */
fun <T : View> T.animateUpdate(
    preset: Pair<AnimConfig, AnimConfig> = YoYoPresets.FadeOut_FadeIn,
    block: T.() -> Unit
) {
    YoYoAnimation.switchContent(this, preset.first, preset.second) {
        it.block()
    }
}
