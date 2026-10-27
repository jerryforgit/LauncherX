/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.folder;

import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Shader;
import android.util.Property;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.celllayout.DelegatedCellDrawing;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.jerry.launcherx.glass.GlassBackdropBridge;
import com.jerry.launcherx.preferences2.PreferenceCacheExtensionsKt;

import com.jerry.launcherx.preferences2.PreferenceManager2;
import com.jerry.launcherx.theme.color.ColorOption;
import com.jerry.launcherx.theme.color.tokens.ColorTokens;
import com.jerry.launcherx.util.LauncherXUtilsKt;

/**
 * This object represents a FolderIcon preview background. It stores drawing /
 * measurement
 * information, handles drawing, and animation (accept state <--> rest state).
 */
public class PreviewBackground extends DelegatedCellDrawing {

    private static final boolean DRAW_SHADOW = false;
    private static final boolean DRAW_STROKE = false;

    @VisibleForTesting
    protected static final int CONSUMPTION_ANIMATION_DURATION = 100;

    @VisibleForTesting
    protected static final float HOVER_SCALE = 1.1f;
    @VisibleForTesting
    protected static final int HOVER_ANIMATION_DURATION = 300;

    private final Context mContext;
    private final PorterDuffXfermode mShadowPorterDuffXfermode
            = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
    private RadialGradient mShadowShader = null;

    private final Matrix mShaderMatrix = new Matrix();
    private final Path mPath = new Path();

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    float mScale = 1f;
    private int mBgColor;
    private int mStrokeColor;
    private int mDotColor;
    private float mStrokeWidth;
    private int mStrokeAlpha = MAX_BG_OPACITY;
    private int mShadowAlpha = 255;
    private View mInvalidateDelegate;
    private boolean mFrostedGlass;
    private boolean mLiquidGlass;
    private final Paint mGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.Shader mFrostedShader;
    private android.graphics.Shader mLiquidShader;
    private android.graphics.Shader mLiquidShader2;
    private android.graphics.Shader mEdgeTopShader;
    private android.graphics.Shader mEdgeBottomShader;
    private int mGlassW;
    private int mGlassH;
    private Bitmap mGlassBackdrop;
    private int mGlassGeneration;
    private float mGlassBlurDp = 28f;
    private float mGlassRefraction = 0.75f;
    private float mGlassDispersion = 1f;
    private final Runnable mGlassBackdropListener = new Runnable() {
        @Override
        public void run() {
            if (!mFrostedGlass && !mLiquidGlass) {
                return;
            }
            mGlassBackdrop = mLiquidGlass
                    ? GlassBackdropBridge.getLens(mContext)
                    : GlassBackdropBridge.get(mContext, mGlassBlurDp);
            invalidate();
            if (mGlassBackdrop == null) {
                GlassBackdropBridge.addListener(this);
            }
        }
    };

    int previewSize;
    int basePreviewOffsetX;
    int basePreviewOffsetY;

    // LauncherX: the area the preview items are laid out in. It is inset from the
    // background so icons the layout rule places on a wide circle stay fully inside the
    // background, plus an optional user margin between background and cell edges.
    int layoutSize;
    int layoutOffsetX;
    int layoutOffsetY;
    int previewMargin;

    // Fraction of the background the preview item layout occupies (before the margin).
    private static final float LAYOUT_CONTENT_FACTOR = 0.85f;

    private CellLayout mDrawingDelegate;

    // When the PreviewBackground is drawn under an icon (for creating a folder) the
    // border
    // should not occlude the icon
    public boolean isClipping = true;

    // Drawing / animation configurations
    @VisibleForTesting
    protected static final float ACCEPT_SCALE_FACTOR = 1.20f;

    // Expressed on a scale from 0 to 255.
    private static final int BG_OPACITY = 255;
    private static final int MAX_BG_OPACITY = 255;
    private static final int SHADOW_OPACITY = 40;

    @VisibleForTesting
    protected ValueAnimator mScaleAnimator;
    private ObjectAnimator mStrokeAlphaAnimator;
    private ObjectAnimator mShadowAnimator;

    @VisibleForTesting
    protected boolean mIsAccepting;
    @VisibleForTesting
    protected boolean mIsHovered;
    @VisibleForTesting
    protected boolean mIsHoveredOrAnimating;

    private static final Property<PreviewBackground, Integer> STROKE_ALPHA = new Property<PreviewBackground, Integer>(
            Integer.class, "strokeAlpha") {
        @Override
        public Integer get(PreviewBackground previewBackground) {
            return previewBackground.mStrokeAlpha;
        }

        @Override
        public void set(PreviewBackground previewBackground, Integer alpha) {
            previewBackground.mStrokeAlpha = alpha;
            previewBackground.invalidate();
        }
    };

    private static final Property<PreviewBackground, Integer> SHADOW_ALPHA = new Property<PreviewBackground, Integer>(
            Integer.class, "shadowAlpha") {
        @Override
        public Integer get(PreviewBackground previewBackground) {
            return previewBackground.mShadowAlpha;
        }

        @Override
        public void set(PreviewBackground previewBackground, Integer alpha) {
            previewBackground.mShadowAlpha = alpha;
            previewBackground.invalidate();
        }
    };

    public PreviewBackground(Context context) {
        mContext = context;
    }

    /**
     * Draws folder background under cell layout
     */
    @Override
    public void drawUnderItem(Canvas canvas) {
        drawBackground(canvas);
        if (!isClipping) {
            drawBackgroundStroke(canvas);
        }
    }

    /**
     * Draws folder background on cell layout
     */
    @Override
    public void drawOverItem(Canvas canvas) {
        if (isClipping) {
            drawBackgroundStroke(canvas);
        }
    }

    public void setup(Context context, ActivityContext activity, View invalidateDelegate,
            int availableSpaceX, int topPadding) {
        mInvalidateDelegate = invalidateDelegate;

        PreferenceManager2 preferenceManager2 = PreferenceManager2.INSTANCE.get(context);

        TypedArray ta = context.getTheme().obtainStyledAttributes(R.styleable.FolderIconPreview);
        ColorOption dotColorOption = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.getNotificationDotColor());
        mDotColor = dotColorOption.getColorPreferenceEntry().getLightColor().invoke(context);
        mStrokeColor = ColorTokens.FolderIconBorderColor.resolveColor(context);
        mBgColor = LauncherXUtilsKt.resolveFolderPreviewColor(context);
        mFrostedGlass = PreferenceCacheExtensionsKt
                .firstCached(preferenceManager2.getFolderFrostedGlass());
        mLiquidGlass = PreferenceCacheExtensionsKt
                .firstCached(preferenceManager2.getLiquidGlassEnabled())
                && PreferenceCacheExtensionsKt
                        .firstCached(preferenceManager2.getFolderLiquidGlass());
        mGlassBlurDp = PreferenceCacheExtensionsKt
                .firstCached(preferenceManager2.getFolderGlassBlur());
        mGlassRefraction = PreferenceCacheExtensionsKt
                .firstCached(preferenceManager2.getLiquidGlassRefraction());
        mGlassDispersion = PreferenceCacheExtensionsKt
                .firstCached(preferenceManager2.getLiquidGlassDispersion());
        ta.recycle();

        DeviceProfile grid = activity.getDeviceProfile();
        // LauncherX: Find the correct icon size depending on which parent owned them
        if (invalidateDelegate instanceof FolderIcon && ((FolderIcon) invalidateDelegate).isInAppDrawer()) {
            int allAppsIconSize = grid.getAllAppsProfile().getIconSizePx();
            previewSize = Math.round(allAppsIconSize * ICON_VISIBLE_AREA_FACTOR);
            basePreviewOffsetX = (availableSpaceX - previewSize) / 2;
            basePreviewOffsetY = topPadding + (allAppsIconSize - previewSize) / 2;
        } else {
            previewSize = grid.folderIconSizePx;

            basePreviewOffsetX = (availableSpaceX - previewSize) / 2;
            basePreviewOffsetY = topPadding + grid.folderIconOffsetYPx;
        }

        // LauncherX: optional margin between the preview background and the cell edges.
        float marginDp = PreferenceCacheExtensionsKt
                .firstCached(preferenceManager2.getFolderPreviewMargin());
        int marginPx = Math.round(marginDp * context.getResources().getDisplayMetrics().density);
        previewMargin = marginPx;
        if (marginPx != 0) {
            previewSize = Math.max(1, previewSize - 2 * marginPx);
            basePreviewOffsetX += marginPx;
            basePreviewOffsetY += marginPx;
        }

        // LauncherX: inset the item layout area inside the background so the preview
        // icons (arranged by the layout rule on a dilated circle that can extend past
        // the background bounds) stay fully within the background.
        layoutSize = Math.max(1, Math.round(previewSize * LAYOUT_CONTENT_FACTOR));
        int layoutInset = (previewSize - layoutSize) / 2;
        layoutOffsetX = basePreviewOffsetX + layoutInset;
        layoutOffsetY = basePreviewOffsetY + layoutInset;

        // Stroke width is 1dp
        mStrokeWidth = context.getResources().getDisplayMetrics().density;

        if (DRAW_SHADOW) {
            float radius = getScaledRadius();
            float shadowRadius = radius + mStrokeWidth;
            int shadowColor = Color.argb(SHADOW_OPACITY, 0, 0, 0);
            mShadowShader = new RadialGradient(0, 0, 1,
                    new int[] { shadowColor, Color.TRANSPARENT },
                    new float[] { radius / shadowRadius, 1 },
                    Shader.TileMode.CLAMP);
        }

        updateGlassBackdrop();
        invalidate();
    }

    /**
     * Fetches the blurred wallpaper slice for the glass preview, requesting it
     * asynchronously and repainting once it lands. No-op when glass is off.
     */
    void clearGlassListener() {
        GlassBackdropBridge.removeListener(mGlassBackdropListener);
    }

    private void updateGlassBackdrop() {
        mGlassGeneration = GlassBackdropBridge.generation();
        if (!mFrostedGlass && !mLiquidGlass) {
            GlassBackdropBridge.removeListener(mGlassBackdropListener);
            mGlassBackdrop = null;
            return;
        }
        Bitmap bmp = mLiquidGlass
                ? GlassBackdropBridge.getLens(mContext)
                : GlassBackdropBridge.get(mContext, mGlassBlurDp);
        mGlassBackdrop = bmp;
        if (bmp == null) {
            GlassBackdropBridge.addListener(mGlassBackdropListener);
            bmp = mLiquidGlass
                    ? GlassBackdropBridge.getLens(mContext)
                    : GlassBackdropBridge.get(mContext, mGlassBlurDp);
            mGlassBackdrop = bmp;
            if (bmp != null) {
                GlassBackdropBridge.removeListener(mGlassBackdropListener);
            }
        }
    }

    void getBounds(Rect outBounds) {
        int top = basePreviewOffsetY;
        int left = basePreviewOffsetX;
        int right = left + previewSize;
        int bottom = top + previewSize;
        outBounds.set(left, top, right, bottom);
    }

    public int getRadius() {
        return previewSize / 2;
    }

    int getScaledRadius() {
        return (int) (mScale * getRadius());
    }

    int getOffsetX() {
        return basePreviewOffsetX - (getScaledRadius() - getRadius());
    }

    int getOffsetY() {
        return basePreviewOffsetY - (getScaledRadius() - getRadius());
    }

    /**
     * Returns the progress of the scale animation to accept state, where 0 means
     * the scale is at
     * 1f and 1 means the scale is at ACCEPT_SCALE_FACTOR. Returns 0 when scaled due
     * to hover.
     */
    float getAcceptScaleProgress() {
        return mIsHoveredOrAnimating ? 0 : (mScale - 1f) / (ACCEPT_SCALE_FACTOR - 1f);
    }

    void invalidate() {
        if (mInvalidateDelegate != null) {
            mInvalidateDelegate.invalidate();
        }

        if (mDrawingDelegate != null) {
            mDrawingDelegate.invalidate();
        }
    }

    void setInvalidateDelegate(View invalidateDelegate) {
        mInvalidateDelegate = invalidateDelegate;
        invalidate();
    }

    public int getBgColor() {
        return mBgColor;
    }

    public int getDotColor() {
        return mDotColor;
    }

    public void drawBackground(Canvas canvas) {
        mPaint.setStyle(Paint.Style.FILL);
        int color = getBgColor();
        int baseAlpha;
        if (mFrostedGlass && mLiquidGlass) {
            baseAlpha = 140;
        } else if (mFrostedGlass) {
            baseAlpha = 170;
        } else if (mLiquidGlass) {
            baseAlpha = 180;
        } else {
            baseAlpha = 230;
        }
        color = setColorAlphaBound(color, (int) (Color.alpha(color) * (baseAlpha / 255f)));
        mPaint.setColor(color);

        getShape().drawShape(canvas, getOffsetX(), getOffsetY(), getScaledRadius(), mPaint);

        // Clip to the preview shape (circle / squircle / rounded rect) rather than its
        // bounding rect, so the glass overlay follows the folder outline instead of
        // rendering as a square that mismatches a circular folder style.
        int left = getOffsetX();
        int top = getOffsetY();
        int size = getScaledRadius() * 2;
        if (size > 0 && (mFrostedGlass || mLiquidGlass)) {
            ensureGlassShaders(left, top, size);
            int save = canvas.save();
            mPath.reset();
            getShape().addToPath(mPath, left, top, getScaledRadius());
            canvas.clipPath(mPath);
            // Blurred wallpaper slice behind the icon so the glass reads as real glass.
            // Skipped while the background is drawn delegated onto a cell (drag/accept),
            // where this canvas is not the icon's own and the position would be wrong.
            // Also re-request the slice while it is still missing (build pending, or a request
            // dropped before it landed): otherwise the preview stays a flat colour.
            if (mGlassBackdrop == null || GlassBackdropBridge.generation() != mGlassGeneration) {
                updateGlassBackdrop();
            }
            if (mGlassBackdrop != null && mInvalidateDelegate != null && !drawingDelegated()) {
                GlassBackdropBridge.draw(
                        mInvalidateDelegate, canvas, mGlassBackdrop,
                        left, top, left + size, top + size,
                        mLiquidGlass ? mGlassRefraction : 0f,
                        getScaledRadius(), mGlassDispersion);
            }
            if (mFrostedGlass) {
                // iOS UIBlurEffect-style milky veil.
                mGlassPaint.setShader(mFrostedShader);
                mGlassPaint.setAlpha(90);
                canvas.drawRect(left, top, left + size, top + size, mGlassPaint);
            }
            canvas.restoreToCount(save);
        }
        drawShadow(canvas);
    }

    /**
     * Creates a LinearGradient without triggering Shader.detectColorSpace, which crashes
     * on Android 13+ when packed int colours carry an invalid colour-space ID. We
     * rebuild the colours via Color.argb which produces properly packed sRGB values.
     */
    private static android.graphics.LinearGradient safeGradient(
            float x0, float y0, float x1, float y1, int c0, int c1) {
        int a0 = (c0 >> 24) & 0xFF;
        int r0 = (c0 >> 16) & 0xFF;
        int g0 = (c0 >> 8) & 0xFF;
        int b0 = c0 & 0xFF;
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        return new android.graphics.LinearGradient(
                x0, y0, x1, y1,
                android.graphics.Color.argb(a0, r0, g0, b0),
                android.graphics.Color.argb(a1, r1, g1, b1),
                Shader.TileMode.CLAMP);
    }

    /** Lazily builds the gradient shaders for the iOS-style glass overlays. */
    private void ensureGlassShaders(int left, int top, int size) {
        if (mFrostedShader != null && mGlassW == size && mGlassH == size) return;
        mGlassW = size;
        mGlassH = size;
        float s = size;
        mFrostedShader = safeGradient(
                left, top, left, top + s,
                0x33FFFFFF, 0x0AFFFFFF);
        mLiquidShader = safeGradient(
                left - s * 0.1f, top - s * 0.1f, left + s * 0.8f, top + s * 0.8f,
                0x8CFFFFFF, 0x00FFFFFF);
        mLiquidShader2 = safeGradient(
                left + s * 1.1f, top + s * 1.1f, left + s * 0.2f, top + s * 0.2f,
                0x4DFFFFFF, 0x00FFFFFF);
        mEdgeTopShader = safeGradient(
                left, top, left, top + s * 0.12f,
                0xB3FFFFFF, 0x00FFFFFF);
        mEdgeBottomShader = safeGradient(
                left, top + s * 0.88f, left, top + s,
                0x00000000, 0x1A000000);
    }

    private ShapeDelegate getShape() {
        return ThemeManager.INSTANCE.get(mContext).getFolderShape();
    }

    public void drawShadow(Canvas canvas) {
        if (!DRAW_SHADOW) {
            return;
        }
        if (mShadowShader == null) {
            return;
        }

        float radius = getScaledRadius();
        float shadowRadius = radius + mStrokeWidth;
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(Color.BLACK);
        int offsetX = getOffsetX();
        int offsetY = getOffsetY();
        final int saveCount;
        if (canvas.isHardwareAccelerated()) {
            saveCount = canvas.saveLayer(offsetX - mStrokeWidth, offsetY,
                    offsetX + radius + shadowRadius, offsetY + shadowRadius + shadowRadius, null);

        } else {
            saveCount = canvas.save();
            canvas.clipPath(getClipPath(), Region.Op.DIFFERENCE);
        }

        mShaderMatrix.setScale(shadowRadius, shadowRadius);
        mShaderMatrix.postTranslate(radius + offsetX, shadowRadius + offsetY);
        mShadowShader.setLocalMatrix(mShaderMatrix);
        mPaint.setAlpha(mShadowAlpha);
        mPaint.setShader(mShadowShader);
        canvas.drawPaint(mPaint);
        mPaint.setAlpha(255);
        mPaint.setShader(null);
        if (canvas.isHardwareAccelerated()) {
            mPaint.setXfermode(mShadowPorterDuffXfermode);
            getShape().drawShape(canvas, offsetX, offsetY, radius, mPaint);
            mPaint.setXfermode(null);
        }

        canvas.restoreToCount(saveCount);
    }

    public void fadeInBackgroundShadow() {
        if (!DRAW_SHADOW) {
            return;
        }
        if (mShadowAnimator != null) {
            mShadowAnimator.cancel();
        }
        mShadowAnimator = ObjectAnimator
                .ofInt(this, SHADOW_ALPHA, 0, 255)
                .setDuration(100);
        mShadowAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mShadowAnimator = null;
            }
        });
        mShadowAnimator.start();
    }

    public void animateBackgroundStroke() {
        if (!DRAW_STROKE) {
            return;
        }

        if (mStrokeAlphaAnimator != null) {
            mStrokeAlphaAnimator.cancel();
        }
        mStrokeAlphaAnimator = ObjectAnimator
                .ofInt(this, STROKE_ALPHA, MAX_BG_OPACITY / 2, MAX_BG_OPACITY)
                .setDuration(100);
        mStrokeAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStrokeAlphaAnimator = null;
            }
        });
        mStrokeAlphaAnimator.start();
    }

    public void drawBackgroundStroke(Canvas canvas) {
        if (!DRAW_STROKE) {
            return;
        }
        mPaint.setColor(setColorAlphaBound(mStrokeColor, mStrokeAlpha));
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(mStrokeWidth);

        float inset = 1f;
        getShape().drawShape(canvas,
                getOffsetX() + inset, getOffsetY() + inset, getScaledRadius() - inset, mPaint);
    }

    /**
     * Draws the leave-behind circle on the given canvas and in the given color.
     */
    public void drawLeaveBehind(Canvas canvas, int color) {
        float originalScale = mScale;
        mScale = 0.5f;

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        getShape().drawShape(canvas, getOffsetX(), getOffsetY(), getScaledRadius(), mPaint);

        mScale = originalScale;
    }

    public Path getClipPath() {
        mPath.reset();
        float radius = getScaledRadius();
        if (!Flags.enableLauncherIconShapes()) {
            radius = radius * ICON_OVERLAP_FACTOR;
        }
        // Find the difference in radius so that the clip path remains centered.
        float radiusDifference = radius - getRadius();
        float offsetX = basePreviewOffsetX - radiusDifference;
        float offsetY = basePreviewOffsetY - radiusDifference;
        getShape().addToPath(mPath, offsetX, offsetY, radius);
        return mPath;
    }

    private void delegateDrawing(CellLayout delegate, int cellX, int cellY) {
        if (mDrawingDelegate != delegate) {
            delegate.addDelegatedCellDrawing(this);
        }

        mDrawingDelegate = delegate;
        mDelegateCellX = cellX;
        mDelegateCellY = cellY;

        invalidate();
    }

    private void clearDrawingDelegate() {
        if (mDrawingDelegate != null) {
            mDrawingDelegate.removeDelegatedCellDrawing(this);
        }

        mDrawingDelegate = null;
        isClipping = false;
        invalidate();
    }

    boolean drawingDelegated() {
        return mDrawingDelegate != null;
    }

    protected void animateScale(boolean isAccepting, boolean isHovered) {
        if (mScaleAnimator != null) {
            mScaleAnimator.cancel();
        }

        final float startScale = mScale;
        final float endScale = isAccepting ? ACCEPT_SCALE_FACTOR : (isHovered ? HOVER_SCALE : 1f);
        Interpolator interpolator = isAccepting != mIsAccepting ? ACCELERATE_DECELERATE : EMPHASIZED_DECELERATE;
        int duration = isAccepting != mIsAccepting ? CONSUMPTION_ANIMATION_DURATION
                : HOVER_ANIMATION_DURATION;
        mIsAccepting = isAccepting;
        mIsHovered = isHovered;
        if (startScale == endScale) {
            if (!mIsAccepting) {
                clearDrawingDelegate();
            }
            mIsHoveredOrAnimating = mIsHovered;
            return;
        }

        mScaleAnimator = ValueAnimator.ofFloat(0f, 1.0f);
        mScaleAnimator.addUpdateListener(animation -> {
            float prog = animation.getAnimatedFraction();
            mScale = prog * endScale + (1 - prog) * startScale;
            invalidate();
        });
        mScaleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (mIsHovered) {
                    mIsHoveredOrAnimating = true;
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mIsAccepting) {
                    clearDrawingDelegate();
                }
                mIsHoveredOrAnimating = mIsHovered;
                mScaleAnimator = null;
            }
        });
        mScaleAnimator.setInterpolator(interpolator);
        mScaleAnimator.setDuration(duration);
        mScaleAnimator.start();
    }

    public void animateToAccept(CellLayout cl, int cellX, int cellY) {
        delegateDrawing(cl, cellX, cellY);
        animateScale(/* isAccepting= */ true, mIsHovered);
    }

    public void animateToRest() {
        animateScale(/* isAccepting= */ false, mIsHovered);
    }

    public float getStrokeWidth() {
        return mStrokeWidth;
    }

    protected void setHovered(boolean hovered) {
        animateScale(mIsAccepting, /* isHovered= */ hovered);
    }
}
