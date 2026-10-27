package com.jerry.launcherx.glass

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.jerry.launcherx.modern.folder.GlassBackdrop
import com.android.launcher3.Utilities

/**
 * Reusable glass/liquid background renderer for rounded-rect cards.
 *
 * Mirrors the exact layer recipe used by [com.jerry.launcherx.modern.folder.LargeFolderView]
 * so the dock capsule, the opened folder window and large folders share one visual
 * language:
 *   blurred wallpaper slice -> tint -> frosted veil / liquid sheen+edge+specular -> noise -> rim.
 *
 * When neither glass mode is active it falls back to a flat colour card with the same
 * 1 dp rim, which is also what those surfaces render by default.
 */
class GlassCardBackground(context: Context) {

    private val appContext = context.applicationContext
    private val density = context.resources.displayMetrics.density
    private val oneDpPx = density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = oneDpPx
    }
    private val clipPath = Path()

    private var frosted = false
    private var liquid = false
    private var glassIsDark = false
    private var backdrop: Bitmap? = null
    private var glassGeneration = GlassBackdrop.generation()
    private val backdropListener = object : Runnable {
        override fun run() {
            backdrop = loadBackdrop()
            if (backdrop != null) {
                lastView?.get()?.postInvalidateOnAnimation()
            } else {
                GlassBackdrop.addListener(this)
            }
        }
    }
    private var noiseShader: Shader? = null
    private var specularAnimator: ValueAnimator? = null
    private var specularPhase = 0f
    private var animateSpecular = false
    private var lastView: java.lang.ref.WeakReference<View>? = null
    private var specularStartMs = 0L

    private var shaderW = -1
    private var shaderH = -1
    private var shaderDark = false
    private var frostedShader: LinearGradient? = null
    private var liquidShader: LinearGradient? = null
    private var liquidShader2: LinearGradient? = null
    private var edgeTopShader: LinearGradient? = null
    private var edgeBottomShader: LinearGradient? = null
    private var borderHighlightShader: LinearGradient? = null

    /** Flat card colour (RGB only; alpha comes from [cardAlpha]). */
    var cardColor: Int = Color.WHITE
        set(value) {
            field = value and 0xFFFFFF
        }

    /** 0–255 multiplier applied on top of the card colour in plain mode. */
    var cardAlpha: Int = 230

    /**
     * Overall opacity of the card (0–1). Used to fade a surface out of the way while it is
     * covered by a translucent panel (e.g. the dock capsule behind the all-apps drawer).
     */
    var surfaceAlpha: Float = 1f

    /** Corner radius in px. */
    var cornerRadius: Float = 0f

    /** Edge-refraction strength for liquid mode (0 disables it). */
    var refractionStrength: Float = GlassBackdrop.REFRACTION_STRENGTH

    /** Chromatic dispersion amount for liquid mode (0 disables the fringe). */
    var dispersion: Float = 1f

    /**
     * Shader-space blur for liquid mode, in screen dp. The historical value is [DEFAULT_LENS_BLUR_DP];
     * large panels (e.g. the opened folder) can lower it to look crisper without touching the dock.
     */
    var lensBlurDp: Float = DEFAULT_LENS_BLUR_DP

    /**
     * Wallpaper blur radius (screen dp) for the glass backdrop. When <= 0 the mode
     * default is used ([GlassBackdrop.FROSTED_RADIUS_DP] / [GlassBackdrop.LIQUID_RADIUS_DP]).
     */
    var blurRadiusDp: Float = -1f
        set(value) {
            if (field == value) return
            field = value
            if (frosted || liquid) {
                backdrop = null
                ensureGlassReady()
            }
        }

    fun setGlassMode(frostedGlass: Boolean, liquidGlass: Boolean) {
        val changed = frosted != frostedGlass || liquid != liquidGlass
        frosted = frostedGlass
        liquid = liquidGlass
        if (changed) {
            backdrop = null
            if (frosted || liquid) {
                ensureGlassReady()
                if (animateSpecular) {
                    specularStartMs = SystemClock.uptimeMillis()
                } else {
                    startSpecularFlow()
                }
            } else {
                specularAnimator?.cancel()
                specularAnimator = null
            }
        }
    }

    /**
     * When true the liquid specular keeps sweeping while the surface is visible (used by the
     * transient opened-folder window); when false it runs once and then rests.
     */
    fun setAnimateSpecular(value: Boolean) {
        animateSpecular = value
    }

    /** Draws the capsule at [rect] (view-local coordinates) behind the dock's children. */
    fun draw(view: View, canvas: Canvas, rect: RectF) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val layerAlpha = (surfaceAlpha.coerceIn(0f, 1f) * 255f).toInt()
        if (layerAlpha == 0) return
        val layer = if (layerAlpha < 255) canvas.saveLayerAlpha(rect, layerAlpha) else -1
        try {
            drawSurface(view, canvas, rect)
        } finally {
            if (layer >= 0) canvas.restoreToCount(layer)
        }
    }

    private fun drawSurface(view: View, canvas: Canvas, rect: RectF) {
        lastView = java.lang.ref.WeakReference(view)
        if (liquid && animateSpecular) {
            if (specularStartMs == 0L) specularStartMs = SystemClock.uptimeMillis()
            val elapsed = (SystemClock.uptimeMillis() - specularStartMs).toFloat()
            specularPhase = (elapsed % SPECULAR_DURATION_MS) / SPECULAR_DURATION_MS
        }
        val glass = frosted || liquid
        val radius = cornerRadius
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)

        if (glass) {
            drawGlassBackdrop(view, canvas, rect)
            if (frosted) {
                drawFrostedVeil(canvas, rect)
            }
            drawGlassNoise(canvas, rect)
        } else {
            bgPaint.shader = null
            bgPaint.color = cardColor
            bgPaint.alpha = (cardAlpha).coerceIn(0, 255)
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
        }
        canvas.restoreToCount(save)

        if (glass) {
            ensureGlassShader(view)
            if (frosted) {
                borderPaint.shader = null
                borderPaint.color = FROSTED_BORDER_COLOR
            } else {
                borderPaint.shader = borderHighlightShader
            }
            borderPaint.strokeWidth = oneDpPx
        } else {
            borderPaint.shader = null
            borderPaint.strokeWidth = oneDpPx
            borderPaint.color = Color.argb(0x33, 0xFF, 0xFF, 0xFF)
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Self-sustaining redraw loop for the flowing specular: it stops by itself once the
        // surface is detached (draw is no longer called), e.g. when the folder window closes.
        if (liquid && animateSpecular) {
            view.postInvalidateOnAnimation()
        }
    }

    fun onSizeChanged() {
        shaderW = -1
        shaderH = -1
    }

    fun release() {
        GlassBackdrop.removeListener(backdropListener)
        specularAnimator?.cancel()
        specularAnimator = null
    }

    private fun ensureGlassReady() {
        glassIsDark = Utilities.isDarkTheme(appContext)
        backdrop = loadBackdrop()
        if (backdrop == null) {
            GlassBackdrop.addListener(backdropListener)
            backdrop = loadBackdrop()
            if (backdrop != null) {
                GlassBackdrop.removeListener(backdropListener)
            }
        }
        if (noiseShader == null) noiseShader = buildNoiseShader()
    }

    private fun loadBackdrop(): Bitmap? =
        if (liquid) {
            GlassBackdrop.getLens(appContext)
        } else {
            val radiusDp = if (blurRadiusDp > 0f) {
                blurRadiusDp
            } else {
                GlassBackdrop.FROSTED_RADIUS_DP
            }
            GlassBackdrop.get(appContext, radiusDp)
        }

    private fun drawGlassBackdrop(view: View, canvas: Canvas, rect: RectF) {
        val gen = GlassBackdrop.generation()
        if (gen != glassGeneration) {
            glassGeneration = gen
            backdrop = null
            ensureGlassReady()
        }
        val bmp = backdrop ?: return
        GlassBackdrop.draw(
            view, canvas, bmp,
            rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt(),
            if (liquid) refractionStrength else 0f,
            cornerRadius,
            dispersion,
            blurPx = lensBlurDp * density,
            specularPhase = if (liquid && animateSpecular) specularPhase else -1f,
        )
    }

    private fun drawGlassTint(canvas: Canvas, rect: RectF) {
        val alpha = if (glassIsDark) 0x1F else 0x33
        bgPaint.shader = null
        bgPaint.color = if (glassIsDark) {
            ColorUtils.setAlphaComponent(Color.BLACK, alpha)
        } else {
            ColorUtils.setAlphaComponent(Color.WHITE, alpha)
        }
        canvas.drawRect(rect, bgPaint)
    }

    private fun drawGlassNoise(canvas: Canvas, rect: RectF) {
        val noise = noiseShader ?: return
        glassPaint.shader = noise
        glassPaint.alpha = 0x07
        canvas.drawRect(rect, glassPaint)
    }

    private fun drawFrostedVeil(canvas: Canvas, rect: RectF) {
        ensureGlassShader(null, rect.width().toInt(), rect.height().toInt())
        glassPaint.shader = frostedShader
        glassPaint.alpha = 0xFF
        canvas.drawRect(rect, glassPaint)
    }


    private fun drawLiquidGlassSheen(canvas: Canvas, rect: RectF) {
        ensureGlassShader(null, rect.width().toInt(), rect.height().toInt())
        glassPaint.shader = liquidShader
        glassPaint.alpha = 160
        canvas.drawRect(rect, glassPaint)
        glassPaint.shader = liquidShader2
        glassPaint.alpha = 70
        canvas.drawRect(rect, glassPaint)
    }

    private fun drawLiquidGlassEdgeHighlight(view: View, canvas: Canvas, rect: RectF) {
        ensureGlassShader(view)
        glassPaint.shader = edgeTopShader
        glassPaint.alpha = 120
        canvas.drawRect(rect, glassPaint)
        glassPaint.shader = edgeBottomShader
        glassPaint.alpha = 60
        canvas.drawRect(rect, glassPaint)
    }

    private fun drawLiquidSpecular(canvas: Canvas, rect: RectF) {
        val w = rect.width()
        val h = rect.height()
        if (w <= 0f || h <= 0f) return
        val centre = (-0.2f + specularPhase * 1.4f) * w
        val band = w * 0.16f
        glassPaint.shader = safeGradient(
            rect.left + centre - band, rect.top - h * 0.2f,
            rect.left + centre + band, rect.top + h * 1.2f,
            0x8CFFFFFF.toInt(), 0x00FFFFFF,
        )
        glassPaint.alpha = 150
        canvas.drawRect(rect, glassPaint)
    }

    private fun startSpecularFlow() {
        if (!liquid) return
        specularAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SPECULAR_DURATION_MS
            interpolator = LinearInterpolator()
            repeatCount = 1
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                specularPhase = it.animatedValue as Float
                lastView?.get()?.postInvalidateOnAnimation()
            }
        }
        specularAnimator = animator
        animator.start()
    }

    private fun buildNoiseShader(): Shader {
        val size = 96
        val pixels = IntArray(size * size)
        val random = java.util.Random(0x5DEECE66L)
        for (i in pixels.indices) {
            val n = random.nextInt(96) + 80
            pixels[i] = Color.argb(0xFF, n, n, n)
        }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun ensureGlassShader(view: View? = null, w: Int = 0, h: Int = 0) {
        val width = if (w > 0) w else view?.width ?: return
        val height = if (h > 0) h else view?.height ?: return
        if (shaderW == width && shaderH == height && shaderDark == glassIsDark) return
        shaderW = width
        shaderH = height
        shaderDark = glassIsDark
        val wf = width.toFloat()
        val hf = height.toFloat()
        frostedShader = if (glassIsDark) {
            safeGradient(0f, 0f, 0f, hf, 0x33000000, 0x00000000)
        } else {
            safeGradient(0f, 0f, 0f, hf, 0x22FFFFFF, 0x00FFFFFF)
        }
        liquidShader = safeGradient(-wf * 0.1f, -hf * 0.1f, wf * 0.8f, hf * 0.8f,
            0x8CFFFFFF.toInt(), 0x00FFFFFF)
        liquidShader2 = safeGradient(wf * 1.1f, hf * 1.1f, wf * 0.2f, hf * 0.2f,
            0x4DFFFFFF, 0x00FFFFFF)
        edgeTopShader = safeGradient(0f, 0f, 0f, hf * 0.12f, 0xB3FFFFFF.toInt(), 0x00FFFFFF)
        edgeBottomShader = safeGradient(0f, hf * 0.88f, 0f, hf, 0x00000000, 0x1A000000)
        borderHighlightShader = safeGradient(0f, 0f, wf, hf, 0x4DFFFFFF.toInt(), 0x0DFFFFFF)
    }

    private fun safeGradient(
        x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int,
    ): LinearGradient {
        return try {
            LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP)
        } catch (_: IllegalArgumentException) {
            LinearGradient(
                x0, y0, x1, y1,
                Color.argb((c0 shr 24) and 0xFF, (c0 shr 16) and 0xFF, (c0 shr 8) and 0xFF, c0 and 0xFF),
                Color.argb((c1 shr 24) and 0xFF, (c1 shr 16) and 0xFF, (c1 shr 8) and 0xFF, c1 and 0xFF),
                Shader.TileMode.CLAMP,
            )
        }
    }

    companion object {
        private const val SPECULAR_DURATION_MS = 3600L
        private const val FROSTED_BORDER_COLOR = 0x1AFFFFFF

        /** Default liquid shader blur; matches the value used before it became configurable. */
        const val DEFAULT_LENS_BLUR_DP = 2.4f
    }
}
