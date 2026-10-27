package com.jerry.launcherx.modern.folder

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.jerry.launcherx.preferences2.PreferenceManager2
import com.jerry.launcherx.preferences2.firstCached
import com.hoko.blur.HokoBlur
import java.util.WeakHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Blurred wallpaper backdrop behind the large folder glass cards.
 *
 * The wallpaper lives behind the launcher window, so it is not part of the view tree and
 * [android.view.View.setRenderEffect] cannot reach it — that only blurs a view's own content.
 * Instead the wallpaper drawable is rasterised once at a fraction of the display size, blurred
 * there (cheap, and visually the same as blurring at full size), and each card samples the
 * slice of it that sits directly behind itself.
 *
 * Everything is best-effort: if wallpaper access is denied or the blur fails the cache stays
 * empty and callers keep their painted fallback.
 */
internal object GlassBackdrop {

    private const val TAG = "GlassBackdrop"

    /** Wallpaper is rasterised at 1/SAMPLE of the display; blur radii are scaled to match. */
    private const val SAMPLE = 6
    private const val LENS_BLUR_DP = 0f

    /**
     * Higher-resolution sampling for the popup-folder scrim. The frosted cache is normally
     * built at 1/6 of the display, which is fine for a soft glass card but visibly blocky when
     * a full-screen blurred wallpaper is stretched behind a folder. 1/2 keeps it smooth at
     * ~5 MB for a 2944x1840 display and is only requested while that option is enabled.
     */
    const val FROSTED_SAMPLE = 2

    /** Default blur radii in screen dp, matching the iOS system-material and liquid-glass recipes. */
    const val FROSTED_RADIUS_DP = 28f
    const val LIQUID_RADIUS_DP = 20f

    /** Blur radii are quantised to this step (dp) so user-tuned intensities share a small cache. */
    private const val RADIUS_QUANTUM_DP = 4f

    /** Upper bound on distinct cached (radius -> bitmap) entries. */
    private const val MAX_CACHED = 8

    /**
     * Backdrop quality profile for one quality level: the display sampling divisor for the lens
     * backdrop, the longest edge of the retained wallpaper source bitmap, and the pre-blur applied
     * to the lens backdrop (in screen dp, on the downsampled bitmap).
     */
    private class Quality(val sample: Int, val sourceDimension: Float, val preBlurDp: Float)

    private fun qualityFor(context: Context): Quality {
        val level = runCatching {
            PreferenceManager2.getInstance(context).liquidGlassQuality.firstCached()
        }.getOrDefault(PreferenceManager2.LIQUID_GLASS_QUALITY_MAX)
        return when (level) {
            PreferenceManager2.LIQUID_GLASS_QUALITY_SAVER -> Quality(2, 2048f, 1.5f)
            PreferenceManager2.LIQUID_GLASS_QUALITY_BALANCED -> Quality(1, 2048f, 0f)
            PreferenceManager2.LIQUID_GLASS_QUALITY_HIGH -> Quality(1, 3072f, 0f)
            else -> Quality(1, 4096f, 0f)
        }
    }

    /**
     * Normalised amount of edge refraction. 0 disables it; ~0.75 reads as a thick, bevelled
     * glass rim (the sample is pulled inward near the edges, like a real meniscus lens).
     */
    const val REFRACTION_STRENGTH = 0.75f

    private val lock = Any()
    private val cache = LinkedHashMap<Int, Bitmap>()
    private var lensBackdrop: Bitmap? = null
    private val listeners = LinkedHashSet<Runnable>()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GlassBackdrop").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val srcRect = Rect()
    private val dstRect = RectF()
    private val renderStates = WeakHashMap<View, LensRenderState>()
    private val refreshViews = WeakHashMap<View, Boolean>()
    private var loading = false
    private var lensLoading = false
    private var shaderBroken = false
    private var receiverRegistered = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var appContext: Context? = null
    private var componentCallbacksRegistered = false
    private var frostedUsed = false
    private var lensUsed = false
    private var refreshScheduled = false

    @Volatile
    private var generation = 0

    @Volatile
    private var lensQualityLevel = -1

    @Volatile
    private var sourceMaxDimensionUsed = 0f

    @Volatile
    private var wallpaperOffset = 0f

    @Volatile
    private var hasHorizontalParallax = false

    private class LensRenderState {
        var shader: RuntimeShader? = null
        var inputBitmap: Bitmap? = null
        var inputShader: BitmapShader? = null
        val inputMatrix = Matrix()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }

    private val wallpaperReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = invalidate()
    }

    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val app = appContext ?: return
            if (displayWidth == 0) return
            ensureDisplaySize(app)
            flushListeners()
            if (frostedUsed) get(app, FROSTED_RADIUS_DP)
            if (lensUsed) getLens(app)
        }

        override fun onLowMemory() = Unit
    }

    /** The current cache generation; surfaces reload when it changes. */
    fun generation(): Int = generation

    /**
     * Tracks the wallpaper's parallax offset (0 = left edge, 1 = right edge). The cached
     * backdrop is built wide enough to cover every offset, so this only repaints the
     * active glass surfaces instead of rebuilding the blur.
     */
    fun setWallpaperOffset(offset: Float) {
        val clamped = if (offset.isFinite()) offset.coerceIn(0f, 1f) else 0f
        if (abs(wallpaperOffset - clamped) < 0.001f) return
        wallpaperOffset = clamped
        if (hasHorizontalParallax) {
            scheduleRefresh()
        }
    }

    /** Drops every cached backdrop, e.g. after the wallpaper changed. */
    fun invalidate() {
        synchronized(lock) {
            sourceWallpaper = null
            sourceMaxDimensionUsed = 0f
        }
        mainHandler.post {
            invalidateNow()
            flushListeners()
        }
    }

    /** The liquid-glass quality level currently in effect. */
    fun qualityLevel(context: Context): Int = runCatching {
        PreferenceManager2.getInstance(context).liquidGlassQuality.firstCached()
    }.getOrDefault(PreferenceManager2.LIQUID_GLASS_QUALITY_MAX)

    /** Rebuilds the backdrops after the quality preference changed; keeps the wallpaper source. */
    fun invalidateQuality() {
        mainHandler.post {
            synchronized(lock) { lensQualityLevel = -1 }
            invalidateNow()
            flushListeners()
        }
    }

    private fun invalidateNow() {
        synchronized(lock) {
            cache.clear()
            lensBackdrop = null
            lensQualityLevel = -1
            loading = false
            lensLoading = false
            hasHorizontalParallax = false
            generation++
        }
        renderStates.clear()
        scheduleRefresh()
    }

    private fun ensureDisplaySize(context: Context) {
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels
        if (displayWidth == 0 || displayHeight == 0) {
            displayWidth = width
            displayHeight = height
            return
        }
        if (displayWidth != width || displayHeight != height) {
            displayWidth = width
            displayHeight = height
            invalidateNow()
            flushListeners()
        }
    }

    private fun registerView(view: View) {
        if (refreshViews.isEmpty() || !refreshViews.containsKey(view)) {
            refreshViews[view] = true
        }
    }

    private fun scheduleRefresh() {
        if (refreshScheduled) return
        refreshScheduled = true
        mainHandler.post {
            refreshScheduled = false
            refreshViews.keys.toList().forEach { it.postInvalidateOnAnimation() }
        }
    }

    /**
     * Repaints every surface that draws glass.
     *
     * Surfaces sitting on the workspace scroll with it, so the wallpaper slice they sample depends
     * on their on-screen position even when [wallpaperOffset] never changes (e.g. a static
     * wallpaper in landscape). The page contents are also drawn through cached hardware layers
     * while scrolling, so without an explicit invalidate a folder preview keeps the slice that was
     * recorded at its off-screen position and looks like a flat colour until something else
     * repaints it.
     */
    fun refreshSurfaces() = scheduleRefresh()

    /**
     * Returns a blurred wallpaper bitmap of roughly [radiusDp] screen-dp worth of blur, or
     * null while it is still being built. Safe to call from the UI thread: the expensive work
     * happens once on a background executor and [addListener] fires when it lands.
     */
    fun get(context: Context, radiusDp: Float, sample: Int = SAMPLE): Bitmap? {
        val app = context.applicationContext
        frostedUsed = true
        registerReceiverIfNeeded(app)
        ensureDisplaySize(app)
        val radius = quantizeRadius(radiusDp)
        val key = cacheKey(radius, sample)
        val buildGeneration = generation
        synchronized(lock) {
            cache[key]?.let { return it }
            if (loading) return null
            loading = true
        }
        executor.execute {
            val built = runCatching {
                build(app, radius.toFloat(), sample, qualityFor(app).sourceDimension)
            }.getOrNull()
            synchronized(lock) {
                loading = false
                if (built != null && buildGeneration == generation) {
                    cache[key] = built
                    while (cache.size > MAX_CACHED) {
                        val eldest = cache.keys.firstOrNull() ?: break
                        cache.remove(eldest)
                    }
                }
            }
            if (built != null || buildGeneration != generation) {
                mainHandler.post { flushListeners() }
            }
        }
        return null
    }

    fun getLens(context: Context): Bitmap? {
        val app = context.applicationContext
        lensUsed = true
        registerReceiverIfNeeded(app)
        ensureDisplaySize(app)
        val buildGeneration = generation
        val level = qualityLevel(app)
        val quality = qualityFor(app)
        synchronized(lock) {
            if (level != lensQualityLevel) {
                lensQualityLevel = level
                lensBackdrop = null
            }
            lensBackdrop?.let { return it }
            if (lensLoading) return null
            lensLoading = true
        }
        executor.execute {
            val built = runCatching {
                build(app, quality.preBlurDp, quality.sample, quality.sourceDimension)
            }.getOrNull()
            synchronized(lock) {
                lensLoading = false
                if (built != null && buildGeneration == generation && level == lensQualityLevel) {
                    lensBackdrop = built
                }
            }
            if (built != null || buildGeneration != generation) {
                mainHandler.post { flushListeners() }
            }
        }
        return null
    }

    /** Snaps a requested blur radius to the nearest cached quantum, clamped to a sane range. */
    private fun quantizeRadius(radiusDp: Float): Int {
        val clamped = radiusDp.coerceIn(0f, 64f)
        return (Math.round(clamped / RADIUS_QUANTUM_DP) * RADIUS_QUANTUM_DP).toInt()
    }

    /** Radius and sampling divisor both participate in the cache key. */
    private fun cacheKey(radius: Int, sample: Int): Int = (sample shl 8) or radius

    /**
     * Runs [listener] on the main thread after the next backdrop build. The listener must re-add
     * itself if its requested bitmap is still unavailable when it runs.
     */
    fun addListener(listener: Runnable) {
        synchronized(lock) { listeners.add(listener) }
    }

    fun removeListener(listener: Runnable) {
        synchronized(lock) { listeners.remove(listener) }
    }

    /**
     * Draws the slice of the blurred wallpaper which sits behind the given local rect into that
     * same on-screen rect, optionally bending it with an edge-localised lens refraction.
     *
     * @param cornerRadiusPx the card's corner radius in px; shapes the refraction band so the
     *        bend hugs the rounded outline instead of a plain bounding box.
     * @param dispersion chromatic-fringe amount at the rim (0 = none, 1 = subtle, 2 = strong).
     */
    fun draw(
        view: View,
        canvas: Canvas,
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        refraction: Float,
        cornerRadiusPx: Float = 0f,
        dispersion: Float = 0f,
        blurPx: Float = -1f,
        specularPhase: Float = -1f,
    ) {
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return
        registerView(view)
        if (refraction > 0f && drawLens(
                view, canvas, bitmap, left, top, right, bottom, refraction, cornerRadiusPx,
                dispersion, blurPx, specularPhase,
            )
        ) {
            return
        }
        sourceRectFor(view, bitmap, left, top, right, bottom)
        dstRect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        canvas.drawBitmap(bitmap, srcRect, dstRect, bitmapPaint)
    }

    /**
     * Bends the backdrop slice with [REFRACTION_SHADER]: the wallpaper sits behind the launcher
     * window (not in the view tree), so the slice is bound as a [BitmapShader] input and the AGSL
     * shader is applied through a paint rather than a view render effect.
     */
    private fun drawLens(
        view: View,
        canvas: Canvas,
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        strength: Float,
        cornerRadiusPx: Float,
        dispersion: Float,
        blurPx: Float,
        specularPhase: Float,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || shaderBroken) return false
        return try {
            val w = right - left
            val h = bottom - top
            sourceRectFor(view, bitmap, left, top, right, bottom)

            val maxRadius = minOf(w, h) / 2f
            val radius = cornerRadiusPx.coerceIn(0f, maxRadius)
            val density = view.resources.displayMetrics.density
            val dark = (view.context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val state = renderStates.getOrPut(view) { LensRenderState() }
            val localShader = state.shader ?: RuntimeShader(LENS_SHADER).also {
                state.shader = it
            }
            localShader.setFloatUniform("uSize", w.toFloat(), h.toFloat())
            localShader.setFloatUniform("uRadius", radius)
            localShader.setFloatUniform("uRefraction", strength)
            localShader.setFloatUniform("uDispersion", dispersion)
            localShader.setFloatUniform(
                "uBlurPx",
                if (blurPx >= 0f) blurPx else 2.2f * density,
            )
            // Dark mode needs a much stronger tint: the wallpaper sample is usually bright, so
            // the historical 0.10 left white labels unreadable on a light-glass card.
            localShader.setFloatUniform("uTint", if (dark) 0.55f else 0.14f)
            localShader.setFloatUniform("uDark", if (dark) 1f else 0f)
            localShader.setFloatUniform("uSpecular", specularPhase)
            localShader.setFloatUniform("uDensity", density)

            val input = state.inputShader
                ?.takeIf { state.inputBitmap === bitmap }
                ?: BitmapShader(
                    bitmap,
                    Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP,
                ).also {
                    state.inputShader = it
                    state.inputBitmap = bitmap
                }
            state.inputMatrix.setScale(
                w / srcRect.width().toFloat(),
                h / srcRect.height().toFloat(),
            )
            state.inputMatrix.postTranslate(
                -srcRect.left * (w / srcRect.width().toFloat()),
                -srcRect.top * (h / srcRect.height().toFloat()),
            )
            input.setLocalMatrix(state.inputMatrix)
            localShader.setInputShader("backdrop", input)

            state.paint.shader = localShader
            val save = canvas.save()
            canvas.translate(left.toFloat(), top.toFloat())
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), state.paint)
            canvas.restoreToCount(save)
            state.paint.shader = null
            true
        } catch (t: Throwable) {
            Log.w(TAG, "refraction unavailable, falling back to plain blur", t)
            shaderBroken = true
            false
        }
    }

    /** Maps an on-screen rect onto the matching slice of the (downscaled) backdrop bitmap. */
    private fun sourceRectFor(
        view: View,
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val dm = view.resources.displayMetrics
        val scale = bitmap.height.toFloat() / dm.heightPixels
        val parallaxPx = (bitmap.width - dm.widthPixels * scale) * wallpaperOffset
        srcRect.set(
            ((location[0] + left) * scale + parallaxPx).toInt(),
            ((location[1] + top) * scale).toInt(),
            ((location[0] + right) * scale + parallaxPx).toInt(),
            ((location[1] + bottom) * scale).toInt(),
        )
    }

    /**
     * Decoded wallpaper content, reused across every backdrop build and across rotations.
     *
     * Rasterising [WallpaperManager.getDrawable] is by far the most expensive part of a build
     * (measured around 2.8 s on the reference tablet: decode + scale of a 4416x2944 image, versus
     * 2 ms to draw and 6 ms to blur). Rebuilding after a rotation used to fetch it again for every
     * size/radius, so the glass needed several seconds to come back. The bitmap is downscaled once
     * to keep the retained memory bounded and is only dropped when the wallpaper itself changes.
     */
    private var sourceWallpaper: Bitmap? = null

    /**
     * @param maxDimension the quality level's longest source edge. The cached bitmap is reused as
     * long as it is at least this large, so lowering the quality never re-decodes the wallpaper;
     * raising it does (once), because the extra detail is not recoverable from a smaller bitmap.
     */
    private fun sourceWallpaper(context: Context, maxDimension: Float): Bitmap? {
        synchronized(lock) {
            sourceWallpaper?.let { if (sourceMaxDimensionUsed >= maxDimension) return it }
        }
        val drawable = runCatching { WallpaperManager.getInstance(context).drawable }
            .getOrNull() ?: return null
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return null
        val downscale = minOf(1f, maxDimension / maxOf(intrinsicWidth, intrinsicHeight).toFloat())
        val width = (intrinsicWidth * downscale).toInt().coerceAtLeast(1)
        val height = (intrinsicHeight * downscale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(downscale, downscale)
        drawable.setBounds(0, 0, intrinsicWidth, intrinsicHeight)
        drawable.draw(canvas)
        synchronized(lock) {
            sourceWallpaper = bitmap
            sourceMaxDimensionUsed = maxDimension
        }
        return bitmap
    }

    private fun build(context: Context, radiusDp: Float, sample: Int, sourceDimension: Float): Bitmap? {
        val dm = context.resources.displayMetrics
        val displayWidth = dm.widthPixels / sample
        val displayHeight = dm.heightPixels / sample
        if (displayWidth <= 0 || displayHeight <= 0) return null

        val source = sourceWallpaper(context, sourceDimension) ?: return null
        // Cover-fit the wallpaper, but keep the full horizontal extent. The workspace then
        // slides the source rect with the wallpaper parallax offset instead of baking one
        // crop into the cached bitmap.
        val scale = maxOf(
            displayWidth.toFloat() / source.width,
            displayHeight.toFloat() / source.height,
        )
        val scaledWidth = source.width * scale
        val scaledHeight = source.height * scale
        val maxWidth = displayWidth * 3
        val flatWidth = ceil(scaledWidth).toInt()
            .coerceIn(displayWidth, maxWidth)
        hasHorizontalParallax = flatWidth > displayWidth
        val flat = Bitmap.createBitmap(flatWidth, displayHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.translate(0f, (displayHeight - scaledHeight) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(source, 0f, 0f, null)

        val radiusPx = (radiusDp * dm.density / sample).toInt()
        // A radius below one source pixel is a no-op; skip the blur pass entirely in that case.
        if (radiusPx <= 0) return flat
        return HokoBlur.with(context)
            .forceCopy(true)
            .scheme(HokoBlur.SCHEME_OPENGL)
            .sampleFactor(1f)
            .radius(radiusPx)
            .blur(flat)
    }

    private fun flushListeners() {
        val pending = synchronized(lock) {
            val copy = listeners.toList()
            listeners.clear()
            copy
        }
        pending.forEach { it.run() }
    }

    private fun registerReceiverIfNeeded(context: Context) {
        appContext = context
        if (!componentCallbacksRegistered) {
            componentCallbacksRegistered = true
            runCatching { context.registerComponentCallbacks(componentCallbacks) }
        }
        if (receiverRegistered) return
        receiverRegistered = true
        runCatching {
            context.registerReceiver(
                wallpaperReceiver,
                IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            )
        }.onFailure { Log.d(TAG, "wallpaper change listener not available") }
    }

    private const val LENS_SHADER = """
        uniform shader backdrop;
        uniform float2 uSize;
        uniform float uRadius;
        uniform float uRefraction;
        uniform float uDispersion;
        uniform float uBlurPx;
        uniform float uTint;
        uniform float uDark;
        uniform float uSpecular;
        uniform float uDensity;

        float roundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
        }

        half4 sampleScene(float2 px) {
            return backdrop.eval(clamp(px, float2(0.5, 0.5), uSize - float2(0.5, 0.5)));
        }

        half4 blurred(float2 px, float blur) {
            half4 c = sampleScene(px) * 0.24;
            c += sampleScene(px + float2(blur, 0.0)) * 0.12;
            c += sampleScene(px - float2(blur, 0.0)) * 0.12;
            c += sampleScene(px + float2(0.0, blur)) * 0.12;
            c += sampleScene(px - float2(0.0, blur)) * 0.12;
            c += sampleScene(px + float2(blur, blur) * 0.707) * 0.07;
            c += sampleScene(px - float2(blur, blur) * 0.707) * 0.07;
            c += sampleScene(px + float2(blur, -blur) * 0.707) * 0.07;
            c += sampleScene(px + float2(-blur, blur) * 0.707) * 0.07;
            return c;
        }

        half4 main(float2 coord) {
            float2 b = uSize * 0.5;
            float2 p = coord - b;
            float r = min(uRadius, min(b.x, b.y));
            float d = roundedBox(p, b, r);
            float3 base = float3(sampleScene(coord).rgb);
            if (d > 1.5) {
                return half4(half3(base), 1.0);
            }
            float e = 0.7;
            float2 n2 = normalize(float2(
                roundedBox(p + float2(e, 0.0), b, r) -
                    roundedBox(p - float2(e, 0.0), b, r),
                roundedBox(p + float2(0.0, e), b, r) -
                    roundedBox(p - float2(0.0, e), b, r)
            ) + float2(0.00001, 0.00001));
            float bevel = min(min(b.x, b.y) * 0.48, 19.0 * uDensity);
            float edge = clamp(1.0 + d / bevel, 0.0, 1.0);
            float slope = pow(edge, 1.65) * 0.94;
            float2 faceNormal = clamp(
                p / max(b, float2(1.0, 1.0)),
                float2(-1.0, -1.0),
                float2(1.0, 1.0)
            ) * 0.045 * (1.0 - edge);
            float2 normalXY = n2 * slope + faceNormal;
            float3 normal = float3(
                normalXY,
                sqrt(max(1.0 - dot(normalXY, normalXY), 0.001))
            );

            float eta = 1.0 / 1.45;
            float cosi = clamp(normal.z, 0.0, 1.0);
            float k = 1.0 - eta * eta * (1.0 - cosi * cosi);
            float tcoef = eta * cosi - sqrt(max(k, 0.0));
            float2 rayXY = tcoef * normalXY;
            float rayZ = -eta + tcoef * normal.z;
            float thickness = min(
                (18.0 + uRefraction * 57.0) * uDensity,
                min(b.x, b.y) * 1.1
            );
            float2 offset = rayXY / max(-rayZ, 0.05) * thickness * uRefraction;
            offset -= p * 0.016 * uRefraction;

            float3 c = float3(blurred(coord + offset, uBlurPx).rgb);
            float2 split = n2 * pow(edge, 2.8) * uDispersion * 2.6 * uDensity;
            c.r += (
                float(sampleScene(coord + offset + split).r) -
                    float(sampleScene(coord + offset).r)
            ) * 0.8;
            c.b += (
                float(sampleScene(coord + offset - split).b) -
                    float(sampleScene(coord + offset).b)
            ) * 0.8;

            float luma = dot(c, float3(0.2126, 0.7152, 0.0722));
            c = mix(float3(luma, luma, luma), c, 1.035);
            float3 tint = mix(
                float3(0.97, 0.985, 1.0),
                float3(0.10, 0.15, 0.22),
                uDark
            );
            c = mix(c, tint, uTint + uDark * 0.04);

            float2 light = normalize(float2(-0.38, -0.93));
            float directional = pow(max(dot(n2, light), 0.0), 7.0);
            float opposite = pow(max(dot(n2, -light), 0.0), 7.0) * 0.52;
            float fresnel = 0.04 + 0.96 * pow(1.0 - normal.z, 5.0);
            float outline = 1.0 - smoothstep(0.4, 2.0, abs(d + 0.8));
            float edgeLight = (directional + opposite) * pow(edge, 4.0);
            float spec = edgeLight * 0.075 +
                outline * (0.018 + directional * 0.35 + opposite * 0.15);
            spec += fresnel * 0.035;
            float3 environment = float3(
                sampleScene(coord + n2 * 34.0 * uDensity).rgb
            );
            c = mix(c, environment, fresnel * 0.12);
            c += mix(float3(1.0, 1.0, 1.0), environment, 0.18) * spec;

            float caustic = exp(-pow((d + 3.0 * uDensity) / (1.65 * uDensity), 2.0));
            c += caustic * opposite * 0.038;

            float2 face = p / max(b, float2(1.0, 1.0));
            float broad = exp(-pow((face.x * 0.55 + face.y * 0.8 + 0.28) * 1.6, 2.0));
            float3 ambient = float3(sampleScene(float2(b.x * 0.4, b.y * 0.45)).rgb);
            c = mix(
                c,
                clamp(
                    ambient + float3(0.15, 0.15, 0.15),
                    float3(0.0, 0.0, 0.0),
                    float3(1.0, 1.0, 1.0)
                ),
                broad * 0.055
            );
            c -= pow(edge, 8.0) * max(dot(n2, float2(0.45, -0.89)), 0.0) * 0.085;

            if (uSpecular >= 0.0) {
                float travel = (coord.x + coord.y * 0.35) /
                    max(uSize.x + uSize.y * 0.35, 1.0);
                float band = exp(-pow((travel - (-0.25 + uSpecular * 1.5)) / 0.10, 2.0));
                c += band * (0.05 + edge * 0.12);
            }

            return half4(half3(c), 1.0);
        }
    """
}
