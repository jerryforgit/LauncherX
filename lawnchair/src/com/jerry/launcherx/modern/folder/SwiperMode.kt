package com.jerry.launcherx.modern.folder

/**
 * How an inline large folder exposes apps that do not fit in its grid.
 *
 * [OFF] keeps the legacy stacked-deck "+N" cell (extra apps are only reachable by opening
 * the folder). [HORIZONTAL] and [VERTICAL] turn the card into a pager: every grid page holds
 * up to `columns * rows` apps and the user swipes in that axis to reach the next page.
 */
enum class SwiperMode {
    OFF,
    HORIZONTAL,
    VERTICAL;

    val isSwiper: Boolean
        get() = this != OFF

    companion object {
        fun fromPref(value: String?): SwiperMode = when (value) {
            LargeFolderBinder.SWIPER_HORIZONTAL -> HORIZONTAL
            LargeFolderBinder.SWIPER_VERTICAL -> VERTICAL
            else -> OFF
        }

        fun toPref(mode: SwiperMode): String = when (mode) {
            HORIZONTAL -> LargeFolderBinder.SWIPER_HORIZONTAL
            VERTICAL -> LargeFolderBinder.SWIPER_VERTICAL
            OFF -> LargeFolderBinder.SWIPER_OFF
        }
    }
}
