package com.jerry.launcherx.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.jerry.launcherx.modern.folder.GlassBackdrop
import com.jerry.launcherx.preferences2.PreferenceManager2

/**
 * Java-friendly facade over [GlassBackdrop] so the AOSP `src/` sources (the folder
 * preview background, the opened folder window) can reuse the same blurred-wallpaper
 * backdrop the Kotlin large-folder/dock glass uses.
 */
object GlassBackdropBridge {

    /** Blurred wallpaper bitmap of roughly [radiusDp] screen-dp, or null while it builds. */
    @JvmStatic
    fun get(context: Context, radiusDp: Float): Bitmap? = GlassBackdrop.get(context, radiusDp)

    @JvmStatic
    fun getLens(context: Context): Bitmap? = GlassBackdrop.getLens(context)

    @JvmStatic
    fun addListener(listener: Runnable) = GlassBackdrop.addListener(listener)

    @JvmStatic
    fun removeListener(listener: Runnable) = GlassBackdrop.removeListener(listener)

    @JvmStatic
    fun generation(): Int = GlassBackdrop.generation()

    @JvmStatic
    fun setWallpaperOffset(offset: Float) = GlassBackdrop.setWallpaperOffset(offset)

    /**
     * Repaints every glass surface. Call while the workspace scrolls: the pages move but their
     * cached display lists do not, so position-dependent glass would keep a stale wallpaper slice.
     */
    @JvmStatic
    fun refreshSurfaces() = GlassBackdrop.refreshSurfaces()

    /** Current liquid-glass quality level (see [PreferenceManager2.LIQUID_GLASS_QUALITY_*]). */
    @JvmStatic
    fun qualityLevel(context: Context): Int = GlassBackdrop.qualityLevel(context)

    /** True when the quality level deliberately uses the cheaper, softer backdrop pipeline. */
    @JvmStatic
    fun isThriftyQuality(context: Context): Boolean =
        GlassBackdrop.qualityLevel(context) == PreferenceManager2.LIQUID_GLASS_QUALITY_SAVER

    /** Rebuilds the cached backdrops after the quality preference changed. */
    @JvmStatic
    fun invalidateQuality() = GlassBackdrop.invalidateQuality()

    /** Refraction strength for liquid mode; pass as the `refraction` argument of [draw]. */
    @JvmStatic
    fun refractionStrength(): Float = GlassBackdrop.REFRACTION_STRENGTH

    /** Draws the slice of the blurred wallpaper behind the given local rect into that rect. */
    @JvmStatic
    fun draw(
        view: View,
        canvas: Canvas,
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        refraction: Float,
        cornerRadiusPx: Float,
        dispersion: Float,
    ) = GlassBackdrop.draw(
        view, canvas, bitmap, left, top, right, bottom, refraction, cornerRadiusPx, dispersion,
    )

    @JvmStatic
    fun draw(
        view: View,
        canvas: Canvas,
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        refraction: Float,
        cornerRadiusPx: Float,
        dispersion: Float,
        blurPx: Float,
        specularPhase: Float,
    ) = GlassBackdrop.draw(
        view, canvas, bitmap, left, top, right, bottom, refraction, cornerRadiusPx, dispersion,
        blurPx, specularPhase,
    )
}
