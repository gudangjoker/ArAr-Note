package com.gudangjoker.ararnote.data

import com.gudangjoker.ararnote.R

enum class NoteColor(
    val index: Int,
    val nameResId: Int,
    val bgResId: Int,
    val headerResId: Int,
    val textResId: Int
) {
    YELLOW(0, R.string.color_yellow, R.color.note_yellow_bg, R.color.note_yellow_header, R.color.note_yellow_text),
    BLUE(1, R.string.color_blue, R.color.note_blue_bg, R.color.note_blue_header, R.color.note_blue_text),
    RED(2, R.string.color_red, R.color.note_red_bg, R.color.note_red_header, R.color.note_red_text),
    ORANGE(3, R.string.color_orange, R.color.note_orange_bg, R.color.note_orange_header, R.color.note_orange_text),
    GREEN(4, R.string.color_green, R.color.note_green_bg, R.color.note_green_header, R.color.note_green_text),
    PURPLE(5, R.string.color_purple, R.color.note_purple_bg, R.color.note_purple_header, R.color.note_purple_text),
    CYAN(6, R.string.color_cyan, R.color.note_cyan_bg, R.color.note_cyan_header, R.color.note_cyan_text),
    GRAY(7, R.string.color_gray, R.color.note_gray_bg, R.color.note_gray_header, R.color.note_gray_text),
    DARK(8, R.string.color_dark, R.color.note_dark_bg, R.color.note_dark_header, R.color.note_dark_text);

    companion object {
        fun fromIndex(index: Int): NoteColor {
            return entries.firstOrNull { it.index == index } ?: YELLOW
        }
    }
}
