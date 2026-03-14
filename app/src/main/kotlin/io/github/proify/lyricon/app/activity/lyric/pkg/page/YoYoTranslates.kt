package io.github.proify.lyricon.app.activity.lyric.pkg.page

import android.content.Context
import io.github.proify.lyricon.app.R

object YoYoTranslates {

    fun getLabel(context: Context, id: String?): String {
        val resId = getLabelResId(id)
        if (resId == -1) return id ?: ""
        return context.getString(resId)
    }

    private fun getLabelResId(id: String?): Int = when (id) {
        "default" -> R.string.yoyo_default
        "fade_out_fade_in" -> R.string.yoyo_fade_out_fade_in
        "fade_out_up_fade_in_up" -> R.string.yoyo_fade_out_up_fade_in_up
        "fade_out_down_fade_in_down" -> R.string.yoyo_fade_out_down_fade_in_down
        "fade_out_left_fade_in_right" -> R.string.yoyo_fade_out_left_fade_in_right
        "fade_out_left_fade_in_up" -> R.string.yoyo_fade_out_left_fade_in_up
        "fade_out_left_zoom_in" -> R.string.yoyo_fade_out_left_zoom_in
        "fade_out_left_landing" -> R.string.yoyo_fade_out_left_landing
        "fade_out_right_fade_in_left" -> R.string.yoyo_fade_out_right_fade_in_left
        "fade_out_right_fade_in_up" -> R.string.yoyo_fade_out_right_fade_in_up
        "fade_out_right_zoom_in" -> R.string.yoyo_fade_out_right_zoom_in
        "fade_out_right_landing" -> R.string.yoyo_fade_out_right_landing
        "slide_out_left_slide_in_right" -> R.string.yoyo_slide_out_left_slide_in_right
        "slide_out_left_fade_in_up" -> R.string.yoyo_slide_out_left_fade_in_up
        "slide_out_left_zoom_in" -> R.string.yoyo_slide_out_left_zoom_in
        "slide_out_left_landing" -> R.string.yoyo_slide_out_left_landing
        "slide_out_right_slide_in_left" -> R.string.yoyo_slide_out_right_slide_in_left
        "slide_out_right_fade_in_up" -> R.string.yoyo_slide_out_right_fade_in_up
        "slide_out_right_zoom_in" -> R.string.yoyo_slide_out_right_zoom_in
        "slide_out_right_landing" -> R.string.yoyo_slide_out_right_landing
        "flip_out_x_flip_in_x" -> R.string.yoyo_flip_out_x_flip_in_x
        "flip_out_y_flip_in_y" -> R.string.yoyo_flip_out_y_flip_in_y
        "rotate_out_rotate_in" -> R.string.yoyo_rotate_out_rotate_in
        "zoom_out_zoom_in" -> R.string.yoyo_zoom_out_zoom_in
        "fade_out_left_zoom_in_right" -> R.string.yoyo_fade_out_left_zoom_in_right
        "fade_out_right_zoom_in_left" -> R.string.yoyo_fade_out_right_zoom_in_left
        "out_fade_out_up" -> R.string.yoyo_out_fade_out_up
        "out_fade_out" -> R.string.yoyo_out_fade_out
        "out_fade_out_left" -> R.string.yoyo_out_fade_out_left
        "out_fade_out_right" -> R.string.yoyo_out_fade_out_right
        "out_fade_out_down" -> R.string.yoyo_out_fade_out_down
        "out_slide_out_left" -> R.string.yoyo_out_slide_out_left
        "out_slide_out_right" -> R.string.yoyo_out_slide_out_right
        "out_flip_out_x" -> R.string.yoyo_out_flip_out_x
        "out_flip_out_y" -> R.string.yoyo_out_flip_out_y
        "out_rotate_out" -> R.string.yoyo_out_rotate_out
        "out_zoom_out" -> R.string.yoyo_out_zoom_out
        "in_fade_in_up" -> R.string.yoyo_in_fade_in_up
        "in_fade_in" -> R.string.yoyo_in_fade_in
        "in_fade_in_left" -> R.string.yoyo_in_fade_in_left
        "in_fade_in_right" -> R.string.yoyo_in_fade_in_right
        "in_fade_in_down" -> R.string.yoyo_in_fade_in_down
        "in_slide_in_left" -> R.string.yoyo_in_slide_in_left
        "in_slide_in_right" -> R.string.yoyo_in_slide_in_right
        "in_flip_in_x" -> R.string.yoyo_in_flip_in_x
        "in_flip_in_y" -> R.string.yoyo_in_flip_in_y
        "in_rotate_in" -> R.string.yoyo_in_rotate_in
        "in_zoom_in" -> R.string.yoyo_in_zoom_in
        "in_zoom_in_left" -> R.string.yoyo_in_zoom_in_left
        "in_zoom_in_right" -> R.string.yoyo_in_zoom_in_right
        "in_landing_soft" -> R.string.yoyo_in_landing_soft
        else -> -1
    }
}
