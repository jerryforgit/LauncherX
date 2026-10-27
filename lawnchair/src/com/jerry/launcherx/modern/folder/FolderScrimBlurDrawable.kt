package com.jerry.launcherx.modern.folder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.preferences2.firstCached

/**
 * Full-screen blurred-wallpaper backdrop used as the drag-layer background behind an opened
 * folder when the "background blur" option is enabled.
 *
 * It samples the shared [GlassBackdrop] cache and fills its bounds with the blurred wallpaper
 * (no glass sheen or refraction). Because it only replaces the wallpaper layer instead of covering
 * the whole drag layer, the desktop content underneath keeps drawing on top of it and stays
 * visible. It is applied via [android.view.View.setBackground] rather than by adding/removing a
 * child view, so it never mutates the drag layer's child list while that list is being dispatched.
 */
class FolderScrimBlurDrawable(private val appContext: Context) : Drawable() {

    private var backdrop: Bitmap? = null
    private var radiusDp = GlassBackdrop.FROSTED_RADIUS_DP
    private var generation = GlassBackdrop.generation()
    private var released = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backdropListener = object : Runnable {
        override fun run() {
            if (released || backdrop != null) return
            backdrop = GlassBackdrop.get(appContext, radiusDp, GlassBackdrop.FROSTED_SAMPLE)
            if (backdrop != null) {
                invalidateSelf()
            } else {
                GlassBackdrop.addListener(this)
            }
        }
    }

    /** Reads the folder-background blur preferences and (re)requests the blurred wallpaper. */
    fun configure() {
        val prefs = PreferenceManager2.getInstance(appContext)
        generation = GlassBackdrop.generation()
        val configured = prefs.folderPopupBackgroundBlurStrength.firstCached()
        radiusDp = if (configured > 0f) configured else GlassBackdrop.FROSTED_RADIUS_DP
        released = false
        GlassBackdrop.removeListener(backdropListener)
        backdrop = GlassBackdrop.get(appContext, radiusDp, GlassBackdrop.FROSTED_SAMPLE)
        if (backdrop == null) {
            GlassBackdrop.addListener(backdropListener)
            backdrop = GlassBackdrop.get(appContext, radiusDp, GlassBackdrop.FROSTED_SAMPLE)
            if (backdrop != null) {
                GlassBackdrop.removeListener(backdropListener)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        if (generation != GlassBackdrop.generation()) {
            configure()
        }
        val bitmap = backdrop ?: return
        if (bounds.isEmpty) {
            return
        }
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    /**
     * The blur radius (screen dp) for blurring the live desktop content, matching the configured
     * background-blur intensity.
     */
    fun getContentBlurRadiusDp(): Float = radiusDp

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    /** Drops the bitmap reference; the shared cache keeps owning it. */
    fun release() {
        released = true
        GlassBackdrop.removeListener(backdropListener)
        backdrop = null
    }
}
