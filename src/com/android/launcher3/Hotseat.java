/*
 * Copyright (C) 2011 The Android Open Source Project
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
 *
 * Modifications copyright 2025 Lawnchair
 */

package com.android.launcher3;

import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.launcher3.accessibility.DragAndDropAccessibilityDelegate;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.jerry.launcherx.hotseat.DisabledHotseat;
import com.jerry.launcherx.hotseat.HotseatMode;
import com.jerry.launcherx.hotseat.HotseatPagedView;
import com.jerry.launcherx.hotseat.LauncherXHotseat;
import com.jerry.launcherx.preferences.PreferenceManager;
import com.jerry.launcherx.preferences2.PreferenceCacheExtensionsKt;
import com.jerry.launcherx.preferences2.PreferenceManager2;

/**
 * View class that represents the bottom dock of the home screen.
 * Hosts a {@link com.jerry.launcherx.hotseat.HotseatPagedView} of icon grids plus an optional QSB.
 */
public class Hotseat extends FrameLayout implements Insettable {

    public static final int ALPHA_CHANNEL_TASKBAR_ALIGNMENT = 0;
    public static final int ALPHA_CHANNEL_PREVIEW_RENDERER = 1;
    public static final int ALPHA_CHANNEL_TASKBAR_STASH = 2;
    public static final int ALPHA_CHANNEL_ASSISTANT_VISIBILITY = 3;
    public static final int ALPHA_CHANNEL_CHANNELS_COUNT = 4;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ALPHA_CHANNEL_TASKBAR_ALIGNMENT, ALPHA_CHANNEL_PREVIEW_RENDERER,
            ALPHA_CHANNEL_TASKBAR_STASH, ALPHA_CHANNEL_ASSISTANT_VISIBILITY})
    public @interface HotseatQsbAlphaId {
    }

    public static final int ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT = 0;
    public static final int ICONS_TRANSLATION_X_CHANNELS_COUNT = 1;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT})
    public @interface IconsTranslationX {
    }

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;
    private static final int BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS = 250;
    private static final int DOCK_PAGE_INDICATOR_HEIGHT_DP = 8;
    private static final int QSB_OUTSIDE_CAPSULE_GAP_DP = 12;
    /** Capsule content height, relative to the dock icon size (same in portrait/landscape). */
    private static final float CAPSULE_HEIGHT_FACTOR = 1.3f;
    /** Glass opacity while a folder popup dims the desktop (visible but clearly dimmed). */
    private static final float FOLDER_POPUP_GLASS_ALPHA = 0.35f;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private Workspace<?> mWorkspace;
    private boolean mSendTouchToWorkspace;
    private boolean mSendTouchToPager;
    private final MultiValueAlpha mIconsAlphaChannels;
    private final MultiValueAlpha mQsbAlphaChannels;

    private @Nullable MultiProperty mQsbTranslationX;

    private final MultiPropertyFactory mIconsTranslationXFactory;

    private final View mQsb;
    private boolean mQsbDisabled;
    private final FrameLayout mIconsContainer;
    private final HotseatPagedView mPagedView;
    private final PageIndicatorDots mPageIndicator;
    private final int mPageIndicatorHeight;
    private com.jerry.launcherx.hotseat.RecentAppsView mRecentAppsView;

    private final ActivityContext mActivity;

    PreferenceManager2 preferenceManager2;
    PreferenceManager preferenceManager;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivity = ActivityContext.lookupContext(context);

        preferenceManager2 = PreferenceManager2.getInstance(context);
        preferenceManager = PreferenceManager.getInstance(context);
        HotseatMode hotseatMode = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.getHotseatMode());
        var hotseatEnabled = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.isHotseatEnabled());

        if (!hotseatEnabled) {
            hotseatMode = DisabledHotseat.INSTANCE;
        }

        if (!hotseatMode.isAvailable(context)) {
            // The current hotseat mode is not available,
            // setting the hotseat mode to one that is always available
            hotseatMode = LauncherXHotseat.INSTANCE;
            com.patrykmichalik.opto.core.PreferenceExtensionsKt.setBlocking(preferenceManager2.getHotseatMode(), hotseatMode);
        }
        int layoutId = hotseatMode.getLayoutResourceId();
        mQsbDisabled = layoutId == R.layout.empty_view;

        mIconsContainer = new FrameLayout(context);
        mIconsContainer.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mIconsContainer.setClipChildren(false);
        mIconsContainer.setClipToPadding(false);
        addView(mIconsContainer);

        mPagedView = new HotseatPagedView(context);
        mPagedView.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mIconsContainer.addView(mPagedView);

        mPageIndicatorHeight = Math.round(
                DOCK_PAGE_INDICATOR_HEIGHT_DP * getResources().getDisplayMetrics().density);
        mPageIndicator = new PageIndicatorDots(context);
        LayoutParams indicatorLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, mPageIndicatorHeight);
        indicatorLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        mPageIndicator.setLayoutParams(indicatorLp);
        mPageIndicator.setVisibility(GONE);
        mIconsContainer.addView(mPageIndicator);
        mPagedView.setPageIndicator(mPageIndicator);

        if (Flags.enableQsbOnHotseat()) {
            mQsb = LayoutInflater.from(context).inflate(R.layout.qsb_container_hotseat, this,
                    false);
        } else {
            mQsb = LayoutInflater.from(context).inflate(layoutId, this,
                    false);
        }

        addView(mQsb);

        if (mQsbDisabled) {
            // Dock search box turned off: keep the dock geometry intact, just drop the view.
            mQsb.setVisibility(GONE);
        }

        boolean recentAppsEnabled = preferenceManager.getHotseatRecentAppsEnabled().get();
        if (recentAppsEnabled) {
            mRecentAppsView = new com.jerry.launcherx.hotseat.RecentAppsView(context);
            // Apply divider settings from prefs
            mRecentAppsView.setDividerEnabled(
                preferenceManager.getHotseatRecentAppsDividerEnabled().get());
            var dividerColorOption = PreferenceCacheExtensionsKt.firstCached(
                preferenceManager2.getHotseatRecentAppsDividerColor());
            mRecentAppsView.setDividerColor(
                dividerColorOption.getColorPreferenceEntry().getLightColor().invoke(getContext()));
            mRecentAppsView.setDividerAlphaPercent(
                preferenceManager.getHotseatRecentAppsDividerAlpha().get());
            mRecentAppsView.setDividerHeightPercent(
                preferenceManager.getHotseatRecentAppsDividerHeightPercent().get());
            DeviceProfile dp = mActivity.getDeviceProfile();
            mRecentAppsView.setIconSize(dp.iconSizePx);
            float density = getResources().getDisplayMetrics().density;
            mRecentAppsView.setAppSpacingPx(
                    Math.round(preferenceManager.getHotseatRecentAppsSpacing().get() * density));
            mRecentAppsView.setStripHorizontalPaddingPx(
                    Math.round(preferenceManager.getHotseatRecentAppsPaddingLeft().get() * density),
                    Math.round(preferenceManager.getHotseatRecentAppsPaddingRight().get() * density));
            FrameLayout.LayoutParams recentLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            recentLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            mRecentAppsView.setLayoutParams(recentLp);
            // Add inside mIconsContainer so it sits in the same layout pass as the
            // dock icon row (blends into the app icon row) rather than floating on top.
            mIconsContainer.addView(mRecentAppsView);
            com.jerry.launcherx.hotseat.RecentAppsManager.Companion.getInstance(context)
                    .startObserving(() -> getPinnedDockPackages());
            mRecentAppsView.setOnVisibilityChangedListener(this::updateRecentAppsPadding);
            // Apply the already-loaded list right away: after an in-app restart the
            // lifecycle-bound collector may attach later (or never, depending on the
            // restart timing), which left the strip permanently invisible.
            mRecentAppsView.applyCachedApps();
        }

        // Create an initial page so alpha/translation channels have a target during construction.
        mPagedView.resetPages(false, null, mActivity.getDeviceProfile());

        mIconsAlphaChannels = new MultiValueAlpha(mIconsContainer, ALPHA_CHANNEL_CHANNELS_COUNT);
        mIconsAlphaChannels.setUpdateVisibility(true);
        if (mQsb instanceof Reorderable qsbReorderable) {
            mQsbTranslationX = qsbReorderable.getTranslateDelegate()
                    .getTranslationX(MultiTranslateDelegate.INDEX_NAV_BAR_ANIM);
        }
        mIconsTranslationXFactory = new MultiPropertyFactory<>(mIconsContainer,
                VIEW_TRANSLATE_X, ICONS_TRANSLATION_X_CHANNELS_COUNT, Float::sum);
        mQsbAlphaChannels = new MultiValueAlpha(mQsb, ALPHA_CHANNEL_CHANNELS_COUNT);
        mQsbAlphaChannels.setUpdateVisibility(true);

        setUpBackground();
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // The constructor reads a possibly stale DataStore snapshot when a settings change
        // just triggered an activity restart. Follow the live preference instead.
        com.jerry.launcherx.hotseat.HotseatQsbModeKt.observeQsbDisabled(this, this::setQsbDisabled);
    }

    private void setQsbDisabled(boolean disabled) {
        if (disabled == mQsbDisabled) return;
        mQsbDisabled = disabled;
        if (disabled) {
            mQsb.setVisibility(GONE);
        }
        requestLayout();
    }

    private com.jerry.launcherx.glass.GlassCardBackground mCapsuleGlass;
    private com.jerry.launcherx.glass.GlassCardBackground mRegularGlass;
    /**
     * Opacity of the dock's glass backgrounds. The all-apps drawer is translucent and is drawn
     * above the dock, so the capsule used to leave a visible ghost behind the panel; the all-apps
     * transition fades it out (and back in) without hiding the dock itself.
     */
    private float mGlassVisibility = 1f;
    private boolean mFolderPopupActive;
    private float mFolderPopupBaseElevation;
    private final android.graphics.RectF mRegularRect = new android.graphics.RectF();
    private int mRegularInsetLeftPx;
    private int mRegularInsetTopPx;
    private int mRegularInsetRightPx;
    private int mRegularInsetBottomPx;
    private int mCapsuleMarginBottomPx;
    private int mCapsuleMarginTopPx;
    private int mCapsuleMarginLeftPx;
    private int mCapsuleMarginRightPx;
    private int mCapsuleInnerPaddingLeftPx;
    private int mCapsuleInnerPaddingRightPx;
    private int mCapsuleInnerPaddingTopPx;
    private int mCapsuleInnerPaddingBottomPx;
    private float mCapsuleCornerRadiusDp;
    private final android.graphics.RectF mCapsuleRect = new android.graphics.RectF();
    private final android.graphics.Rect mContentBounds = new android.graphics.Rect();
    private void setUpBackground() {
        // The capsule background and the regular dock background are independent and may
        // coexist: the capsule glass is drawn on top of the regular background in onDraw().
        if (preferenceManager.getHotseatCapsuleBg().get()) {
            setUpCapsuleBackground();
        } else {
            clearCapsuleBackground();
        }
        setUpRegularBackground();
        applyGlassVisibility();
    }

    /**
     * Sets how opaque the dock's glass backgrounds are (0 = fully faded out, 1 = normal).
     * Keeps the dock itself visible; only the glass and its elevation shadow fade.
     */
    public void setGlassVisibility(float visibility) {
        float clamped = Math.max(0f, Math.min(1f, visibility));
        if (Math.abs(clamped - mGlassVisibility) < 0.001f) return;
        mGlassVisibility = clamped;
        applyGlassVisibility();
        invalidateOutline();
        invalidate();
    }

    private void applyGlassVisibility() {
        if (mCapsuleGlass != null) mCapsuleGlass.setSurfaceAlpha(mGlassVisibility);
        if (mRegularGlass != null) mRegularGlass.setSurfaceAlpha(mGlassVisibility);
    }

    /**
     * The folder popup dims/blurs the desktop behind it. The dock's elevated capsule would
     * otherwise stay above that scrim and look like a bright layer floating over the dimmed
     * screen. Lower the dock below the scrim and fade only its glass background while a folder
     * is open; the dock icons stay where they are.
     */
    public void setFolderPopupActive(boolean active) {
        if (mFolderPopupActive == active) return;
        mFolderPopupActive = active;
        if (active) {
            mFolderPopupBaseElevation = getElevation();
            setElevation(0f);
            // Keep the capsule present but dim it with the rest of the dock; hiding it
            // entirely read like a rendering bug.
            setGlassVisibility(FOLDER_POPUP_GLASS_ALPHA);
        } else {
            setElevation(mFolderPopupBaseElevation);
            setGlassVisibility(1f);
        }
    }

    private void clearCapsuleBackground() {
        if (mCapsuleGlass != null) {
            mCapsuleGlass.release();
        }
        mCapsuleGlass = null;
        mCapsuleMarginBottomPx = 0;
        mCapsuleMarginTopPx = 0;
        mCapsuleMarginLeftPx = 0;
        mCapsuleMarginRightPx = 0;
        mCapsuleInnerPaddingLeftPx = 0;
        mCapsuleInnerPaddingRightPx = 0;
        mCapsuleInnerPaddingTopPx = 0;
        mCapsuleInnerPaddingBottomPx = 0;
        mCapsuleRect.setEmpty();
        setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        setElevation(0);
    }

    private void setUpRegularBackground() {
        if (mRegularGlass != null) {
            mRegularGlass.release();
            mRegularGlass = null;
        }
        mRegularRect.setEmpty();
        if (!preferenceManager.getHotseatBG().get()) {
            setBackground(null);
            return;
        }

        var bgColor = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.getHotseatBackgroundColor());
        var transparency = preferenceManager.getHotseatBGAlpha().get();
        var alphaValue = (transparency * 255) / 100;
        var baseColor = bgColor.getColorPreferenceEntry().getLightColor().invoke(getContext());
        var finalColor = Color.argb(alphaValue, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
        int insetHorizontalLeft = preferenceManager.getHotseatBGHorizontalInsetLeft().get();
        int insetHorizontalRight = preferenceManager.getHotseatBGHorizontalInsetRight().get();
        int insetVerticalTop = preferenceManager.getHotseatBGVerticalInsetTop().get();
        int insetVerticalBottom = preferenceManager.getHotseatBGVerticalInsetBottom().get();
        float cornerRadiusDp = PreferenceCacheExtensionsKt.firstCached(
                preferenceManager2.getHotseatBackgroundCornerRadius());
        float cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                cornerRadiusDp,
                getResources().getDisplayMetrics());

        boolean frosted = preferenceManager.getHotseatBGFrostedGlass().get();
        boolean liquid = PreferenceCacheExtensionsKt.firstCached(
                preferenceManager2.getLiquidGlassEnabled())
                && preferenceManager.getHotseatBGLiquidGlass().get();
        if (frosted || liquid) {
            // Glass dock background: drawn in onDraw() instead of the flat InsetDrawable so the
            // blurred wallpaper / liquid sheen can show through.
            mRegularInsetLeftPx = insetHorizontalLeft;
            mRegularInsetTopPx = insetVerticalTop;
            mRegularInsetRightPx = insetHorizontalRight;
            mRegularInsetBottomPx = insetVerticalBottom;
            mRegularGlass = new com.jerry.launcherx.glass.GlassCardBackground(getContext());
            mRegularGlass.setCardColor(baseColor);
            mRegularGlass.setCardAlpha(alphaValue);
            mRegularGlass.setBlurRadiusDp(preferenceManager.getHotseatBGGlassBlur().get());
            mRegularGlass.setRefractionStrength(PreferenceCacheExtensionsKt.firstCached(
                    preferenceManager2.getLiquidGlassRefraction()));
            mRegularGlass.setDispersion(PreferenceCacheExtensionsKt.firstCached(
                    preferenceManager2.getLiquidGlassDispersion()));
            mRegularGlass.setAnimateSpecular(PreferenceCacheExtensionsKt.firstCached(
                    preferenceManager2.getLiquidGlassSpecularFlow()));
            mRegularGlass.setGlassMode(frosted, liquid);
            mRegularGlass.setCornerRadius(cornerRadius);
            updateRegularRect();
            setWillNotDraw(false);
            setBackground(null);
            invalidate();
            return;
        }

        GradientDrawable background = new GradientDrawable();
        background.setColor(finalColor);
        background.setCornerRadius(cornerRadius);
        InsetDrawable bg = new InsetDrawable(background,
                insetHorizontalLeft, insetVerticalTop, insetHorizontalRight, insetVerticalBottom);
        setBackground(bg);
    }

    private void updateRegularRect() {
        if (mRegularGlass == null) {
            return;
        }
        mRegularRect.set(
                mRegularInsetLeftPx,
                mRegularInsetTopPx,
                getWidth() - mRegularInsetRightPx,
                getHeight() - mRegularInsetBottomPx);
    }

    private void setUpCapsuleBackground() {
        var bgColor = PreferenceCacheExtensionsKt.firstCached(preferenceManager2.getHotseatBackgroundColor());
        var transparency = preferenceManager.getHotseatBGAlpha().get();
        var alphaValue = (transparency * 255) / 100;
        var baseColor = bgColor.getColorPreferenceEntry().getLightColor().invoke(getContext());

        float density = getResources().getDisplayMetrics().density;
        float elevationDp = preferenceManager.getHotseatCapsuleElevation().get();
        mCapsuleMarginBottomPx = Math.round(preferenceManager.getHotseatCapsuleMarginBottom().get() * density);
        mCapsuleMarginTopPx = Math.round(preferenceManager.getHotseatCapsuleMarginTop().get() * density);
        mCapsuleMarginLeftPx = Math.round(preferenceManager.getHotseatCapsuleMarginLeft().get() * density);
        mCapsuleMarginRightPx = Math.round(preferenceManager.getHotseatCapsuleMarginRight().get() * density);
        mCapsuleInnerPaddingLeftPx = Math.round(preferenceManager.getHotseatCapsuleInnerPaddingLeft().get() * density);
        mCapsuleInnerPaddingRightPx = Math.round(preferenceManager.getHotseatCapsuleInnerPaddingRight().get() * density);
        mCapsuleInnerPaddingTopPx = Math.round(preferenceManager.getHotseatCapsuleInnerPaddingTop().get() * density);
        mCapsuleInnerPaddingBottomPx = Math.round(preferenceManager.getHotseatCapsuleInnerPaddingBottom().get() * density);
        mCapsuleCornerRadiusDp = preferenceManager.getHotseatCapsuleCornerRadius().get();

        mCapsuleGlass = new com.jerry.launcherx.glass.GlassCardBackground(getContext());
        mCapsuleGlass.setCardColor(baseColor);
        mCapsuleGlass.setCardAlpha(alphaValue);
        mCapsuleGlass.setBlurRadiusDp(preferenceManager.getHotseatCapsuleGlassBlur().get());
        mCapsuleGlass.setRefractionStrength(PreferenceCacheExtensionsKt.firstCached(
                preferenceManager2.getLiquidGlassRefraction()));
        mCapsuleGlass.setDispersion(PreferenceCacheExtensionsKt.firstCached(
                preferenceManager2.getLiquidGlassDispersion()));
        mCapsuleGlass.setAnimateSpecular(PreferenceCacheExtensionsKt.firstCached(
                preferenceManager2.getLiquidGlassSpecularFlow()));
        boolean liquidCapsule = PreferenceCacheExtensionsKt.firstCached(
                preferenceManager2.getLiquidGlassEnabled())
                && preferenceManager.getHotseatCapsuleLiquidGlass().get();
        mCapsuleGlass.setGlassMode(
                preferenceManager.getHotseatCapsuleFrostedGlass().get(),
                liquidCapsule);

        setWillNotDraw(false);
        setBackground(null);
        setElevation(elevationDp * density);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                if (mCapsuleGlass == null || mCapsuleRect.isEmpty()) {
                    outline.setEmpty();
                    return;
                }
                outline.setRoundRect(
                        (int) mCapsuleRect.left, (int) mCapsuleRect.top,
                        (int) mCapsuleRect.right, (int) mCapsuleRect.bottom,
                        mCapsuleGlass.getCornerRadius());
                // Fade the elevation shadow together with the glass behind the all-apps drawer.
                outline.setAlpha(mGlassVisibility);
            }
        });
        computeContentBounds();
        updateCapsuleCornerRadius();
    }

    private void updateCapsuleCornerRadius() {
        if (mCapsuleGlass == null) return;
        int drawableHeight = (int) mCapsuleRect.height();
        if (drawableHeight <= 0) return;
        float radius;
        if (mCapsuleCornerRadiusDp <= 0) {
            radius = drawableHeight / 2f; // auto pill
        } else {
            float density = getResources().getDisplayMetrics().density;
            float radiusPx = mCapsuleCornerRadiusDp * density;
            radius = Math.min(radiusPx, drawableHeight / 2f);
        }
        mCapsuleGlass.setCornerRadius(radius);
        invalidateOutline();
        invalidate();
    }

    /** Computes the union of laid-out bounds of dock content (icons, recents, QSB). */
    /**
     * Whether the dock search box should be wrapped by the capsule background.
     * Inline (landscape) layouts keep it inside; portrait or cramped layouts put it outside.
     */
    private boolean isQsbInsideCapsule() {
        if (mQsb == null || mQsb.getVisibility() == GONE) return false;
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (!dp.isQsbInline) return false;
        int qsbWidth = mQsb.getWidth() > 0 ? mQsb.getWidth() : dp.hotseatQsbWidth;
        return qsbWidth >= dp.iconSizePx * 2;
    }

    private void computeContentBounds() {
        mContentBounds.setEmpty();
        boolean empty = true;

        // QSB (direct child of Hotseat). It is part of the capsule only when it is laid
        // out inline next to the icons (landscape) and is wide enough to be useful;
        // in portrait / cramped layouts it floats outside the capsule instead.
        if (mQsb != null && mQsb.getVisibility() != GONE && isQsbInsideCapsule()) {
            mContentBounds.union(mQsb.getLeft(), mQsb.getTop(),
                    mQsb.getRight(), mQsb.getBottom());
            empty = false;
        }

        // Icons in current CellLayout page
        if (mPagedView != null) {
            CellLayout page = mPagedView.getCurrentCellLayout();
            if (page != null) {
                var sc = page.getShortcutsAndWidgets();
                for (int i = 0; i < sc.getChildCount(); i++) {
                    View child = sc.getChildAt(i);
                    if (child.getVisibility() == GONE) continue;
                    android.graphics.Rect r = new android.graphics.Rect();
                    if (child instanceof com.android.launcher3.BubbleTextView btv
                            && !btv.shouldTextBeVisible()) {
                        // Labels hidden in dock: use the visible icon graphic bounds
                        // instead of the full cell height so the capsule hugs the icons.
                        btv.getIconBounds(r);
                    } else {
                        r.set(0, 0, child.getWidth(), child.getHeight());
                    }
                    offsetDescendantRectToHotseat(child, r);
                    mContentBounds.union(r);
                    empty = false;
                }
            } else {
            }
        } else {
        }

        // Recent apps view
        if (mRecentAppsView != null && mRecentAppsView.getVisibility() != GONE) {
            android.graphics.Rect r = new android.graphics.Rect(
                    0, 0, mRecentAppsView.getWidth(), mRecentAppsView.getHeight());
            offsetDescendantRectToHotseat(mRecentAppsView, r);
            mContentBounds.union(r);
            empty = false;
        }

        if (empty) {
            mContentBounds.set(getDockInsetLeft(), getContentInsetTop(),
                    getWidth() - getDockInsetRight(), getHeight() - getContentInsetBottom());
        } else {
            // Keep the capsule height identical in portrait and landscape: derive it from
            // the icon size around the content center instead of the (orientation dependent)
            // cell height, which varies with folders / recent apps.
            DeviceProfile dp = mActivity.getDeviceProfile();
            int centerY = (mContentBounds.top + mContentBounds.bottom) / 2;
            int halfHeight = Math.round(dp.iconSizePx * CAPSULE_HEIGHT_FACTOR / 2f);
            mContentBounds.top = centerY - halfHeight;
            mContentBounds.bottom = centerY + halfHeight;
        }

        if (isFullWidthDock()) {
            // Full-width mode: the capsule follows the dock margins instead of the icons,
            // so it stays the same width when the dock has fewer apps.
            mContentBounds.left = getDockInsetLeft() + mCapsuleInnerPaddingLeftPx;
            mContentBounds.right = getWidth() - getDockInsetRight()
                    - mCapsuleInnerPaddingRightPx;
        }

        // Expand content bounds by inner padding to get the capsule rect.
        // In full-width mode the capsule follows the dock bar (margins), not the icon
        // union, so the content is inset from the capsule by the inner padding.
        if (isFullWidthDock()) {
            mCapsuleRect.set(
                    getDockInsetLeft(),
                    mContentBounds.top - mCapsuleInnerPaddingTopPx,
                    getWidth() - getDockInsetRight(),
                    mContentBounds.bottom + mCapsuleInnerPaddingBottomPx);
        } else {
            mCapsuleRect.set(
                    mContentBounds.left - mCapsuleInnerPaddingLeftPx,
                    mContentBounds.top - mCapsuleInnerPaddingTopPx,
                    mContentBounds.right + mCapsuleInnerPaddingRightPx,
                    mContentBounds.bottom + mCapsuleInnerPaddingBottomPx);
        }
    }

    /** Walks up the parent chain to convert [rect] from [descendant]'s coords to ours. */
    private void offsetDescendantRectToHotseat(View descendant, android.graphics.Rect rect) {
        View current = descendant;
        while (current != null && current != this) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) break;
            View pv = (View) parent;
            rect.offset(current.getLeft() - pv.getScrollX(),
                    current.getTop() - pv.getScrollY());
            current = pv;
        }
        if (current == this) {
            rect.offset(getScrollX(), getScrollY());
        }
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        if (mGlassVisibility > 0f) {
            if (mRegularGlass != null && !mRegularRect.isEmpty()) {
                mRegularGlass.draw(this, canvas, mRegularRect);
            }
            if (mCapsuleGlass != null && !mCapsuleRect.isEmpty()) {
                mCapsuleGlass.draw(this, canvas, mCapsuleRect);
            }
        }
        super.onDraw(canvas);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mRegularGlass != null) {
            updateRegularRect();
            mRegularGlass.onSizeChanged();
        }
        if (mCapsuleGlass != null) {
            computeContentBounds();
            updateCapsuleCornerRadius();
            mCapsuleGlass.onSizeChanged();
        }
    }

    /** Provides translation X for hotseat icons for the channel. */
    public MultiProperty getIconsTranslationX(@IconsTranslationX int channelId) {
        return mIconsTranslationXFactory.get(channelId);
    }

    private boolean mRecentPaddingListenerAdded = false;

    private void updateRecentAppsPadding() {
        if (mRecentAppsView == null) {
            requestLayout();
            return;
        }
        if (mRecentAppsView.getMeasuredWidth() <= 0 && !mRecentPaddingListenerAdded) {
            mRecentPaddingListenerAdded = true;
            mRecentAppsView.addOnLayoutChangeListener(
                    (v, l, t, r, b, ol, ot, or, ob) -> requestLayout());
        }
        requestLayout();
    }

    /** Collects package names of all items currently pinned in the dock. */
    private java.util.Set<String> getPinnedDockPackages() {
        java.util.Set<String> packages = new java.util.HashSet<>();
        int pageCount = mPagedView.getPageCount();
        for (int i = 0; i < pageCount; i++) {
            CellLayout page = mPagedView.getPageAt(i);
            if (page == null) continue;
            ViewGroup icons = page.getShortcutsAndWidgets();
            for (int j = 0; j < icons.getChildCount(); j++) {
                View child = icons.getChildAt(j);
                if (child instanceof BubbleTextView btv && btv.getTag() instanceof ItemInfo info) {
                    String pkg = info.getTargetPackage();
                    if (pkg != null) {
                        packages.add(pkg);
                    }
                }
            }
        }
        return packages;
    }

    /** Provides translation X for hotseat Qsb. */
    @Nullable
    public MultiProperty getQsbTranslationX() {
        return mQsbTranslationX;
    }

    public HotseatPagedView getPagedView() {
        return mPagedView;
    }

    /** Returns the CellLayout for the given dock page. */
    @Nullable
    public CellLayout getPageAt(int page) {
        return mPagedView.getPageAt(page);
    }

    /** Returns the currently visible dock page layout. */
    @Nullable
    public CellLayout getCurrentPageLayout() {
        return mPagedView.getCurrentCellLayout();
    }

    /** Returns all dock page layouts. */
    public CellLayout[] getPageLayouts() {
        int count = mPagedView.getPageCount();
        CellLayout[] pages = new CellLayout[count];
        for (int i = 0; i < count; i++) {
            pages[i] = mPagedView.getPageAt(i);
        }
        return pages;
    }

    /** Whether {@code layout} is one of this hotseat's page CellLayouts. */
    public boolean isHotseatPage(View layout) {
        if (!(layout instanceof CellLayout)) {
            return false;
        }
        return layout.getParent() == mPagedView;
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        if (mHasVerticalHotseat) {
            return 0;
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        int numColumns = dp.numShownHotseatIcons;
        int localRank = getLocalRank(rank, dp);
        return localRank % numColumns;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        if (mHasVerticalHotseat) {
            CellLayout page = getCurrentPageLayout();
            int countY = page != null ? page.getCountY() : mActivity.getDeviceProfile().numShownHotseatIcons;
            return countY - (rank + 1);
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        int numColumns = dp.numShownHotseatIcons;
        int localRank = getLocalRank(rank, dp);
        return localRank / numColumns;
    }

    /** Returns the dock page index for a global hotseat rank. */
    public int getPageFromOrder(int rank) {
        if (mHasVerticalHotseat) {
            return 0;
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        int slotsPerPage = Math.max(1, dp.numShownHotseatIcons * dp.numHotseatRows);
        return rank / slotsPerPage;
    }

    private static int getLocalRank(int rank, DeviceProfile dp) {
        int slotsPerPage = Math.max(1, dp.numShownHotseatIcons * dp.numHotseatRows);
        return rank % slotsPerPage;
    }

    boolean isHasVerticalHotseat() {
        return mHasVerticalHotseat;
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        boolean bubbleBarEnabled = activityContext.isBubbleBarEnabled();
        boolean hasBubbles = activityContext.hasBubbles();
        mHasVerticalHotseat = hasVerticalHotseat;
        DeviceProfile dp = mActivity.getDeviceProfile();

        mPagedView.resetPages(hasVerticalHotseat, mWorkspace, dp);

        // resetPages recreates all CellLayout pages, leaving them unmeasured (0×0).
        // During model binding (workspaceLoading=true) the requestLayout() inside
        // resetPages is swallowed, so no measure/layout pass ever runs and the dock
        // icons stay invisible. Post a requestLayout so it fires after the current
        // binding batch, guaranteeing the new pages get measured and laid out.
        post(() -> requestLayout());

        if (bubbleBarEnabled) {
            for (CellLayout page : getPageLayouts()) {
                if (dp.shouldAdjustHotseatForBubbleBar(getContext(), hasBubbles)) {
                    page.getShortcutsAndWidgets().setTranslationProvider(
                            cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
                } else {
                    page.getShortcutsAndWidgets().setTranslationProvider(null);
                }
            }
            if (mQsb instanceof HorizontalInsettableView) {
                HorizontalInsettableView insettableQsb = (HorizontalInsettableView) mQsb;
                if (dp.shouldAdjustHotseatForBubbleBar(getContext(), hasBubbles)) {
                    final float insetFraction = (float) dp.iconSizePx / dp.hotseatQsbWidth;
                    mQsb.post(() -> insettableQsb.setHorizontalInsets(insetFraction));
                } else {
                    insettableQsb.setHorizontalInsets(0);
                }
            }
        }
    }

    /**
     * Adjust the hotseat icons for the bubble bar.
     */
    public void adjustForBubbleBar(boolean isBubbleBarVisible) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        boolean shouldAdjust = isBubbleBarVisible
                && dp.shouldAdjustHotseatOrQsbForBubbleBar(getContext());
        boolean shouldAdjustHotseat = shouldAdjust
                && dp.shouldAlignBubbleBarWithHotseat();
        AnimatorSet animatorSet = new AnimatorSet();
        for (CellLayout page : getPageLayouts()) {
            ShortcutAndWidgetContainer icons = page.getShortcutsAndWidgets();
            if (shouldAdjustHotseat) {
                icons.setTranslationProvider(
                        cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
            } else {
                icons.setTranslationProvider(null);
            }
            for (int i = 0; i < icons.getChildCount(); i++) {
                View child = icons.getChildAt(i);
                if (child.getLayoutParams() instanceof CellLayoutLayoutParams lp) {
                    float tx = shouldAdjustHotseat
                            ? dp.getHotseatAdjustedTranslation(getContext(), lp.getCellX()) : 0;
                    if (child instanceof Reorderable) {
                        MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                        animatorSet.play(
                                mtd.getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM).animateToValue(tx));
                    } else {
                        animatorSet.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, tx));
                    }
                }
            }
        }
        boolean shouldAdjustQsb =
                shouldAdjustHotseat || (shouldAdjust && dp.shouldAlignBubbleBarWithQSB());
        if (mQsb instanceof HorizontalInsettableView horizontalInsettableQsb) {
            final float currentInsetFraction = horizontalInsettableQsb.getHorizontalInsets();
            final float targetInsetFraction = shouldAdjustQsb
                    ? (float) dp.iconSizePx / dp.hotseatQsbWidth : 0;
            ValueAnimator qsbAnimator =
                    ValueAnimator.ofFloat(currentInsetFraction, targetInsetFraction);
            qsbAnimator.addUpdateListener(animation -> {
                float insetFraction = (float) animation.getAnimatedValue();
                horizontalInsettableQsb.setHorizontalInsets(insetFraction);
            });
            animatorSet.play(qsbAnimator);
        }
        animatorSet.setDuration(BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS).start();
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            mQsb.setVisibility(View.GONE);
            mPageIndicator.setVisibility(GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right;
            }
        } else {
            mQsb.setVisibility(View.VISIBLE);
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = grid.hotseatBarSizePx;
        }

        Rect padding = grid.getHotseatLayoutPadding(getContext());
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void setWorkspace(Workspace<?> w) {
        mWorkspace = w;
        for (CellLayout page : getPageLayouts()) {
            page.setCellLayoutContainer(w);
        }
    }

    private boolean isTouchOnQsb(MotionEvent ev) {
        if (mQsb == null || mQsb.getVisibility() != VISIBLE) {
            return false;
        }
        float x = ev.getX();
        float y = ev.getY();
        return x >= mQsb.getLeft() && x < mQsb.getRight()
                && y >= mQsb.getTop() && y < mQsb.getBottom();
    }

    private MotionEvent obtainPagerEvent(MotionEvent ev) {
        MotionEvent pagerEv = MotionEvent.obtain(ev);
        pagerEv.offsetLocation(-mIconsContainer.getLeft() - mPagedView.getLeft(),
                -mIconsContainer.getTop() - mPagedView.getTop());
        return pagerEv;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (ev.getY() > yThreshold || isTouchOnQsb(ev)) {
            return false;
        }

        // Multi-page dock: own the icon-band stream and dispatch it to the pager so empty
        // pages can still scroll (same pattern as single-page workspace forwarding).
        if (mPagedView.isPagingEnabled()) {
            final int action = ev.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_DOWN) {
                mSendTouchToPager = true;
                mSendTouchToWorkspace = false;
            }
            return mSendTouchToPager;
        }

        // Single-page dock: forward horizontal swipes to workspace.
        if (mWorkspace != null) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mSendTouchToPager) {
            MotionEvent pagerEv = obtainPagerEvent(event);
            boolean handled = mPagedView.dispatchTouchEvent(pagerEv);
            pagerEv.recycle();
            final int action = event.getAction() & MotionEvent.ACTION_MASK;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mSendTouchToPager = false;
            }
            return handled;
        }
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        return false;
    }

    /**
     * Effective content insets. When the capsule background is enabled, the capsule
     * outer margins and inner padding must be reserved on each side so that laid-out
     * content (QSB/icons/recents) always sits inside the capsule (content top-aligned
     * in the resulting band). Hotseat padding alone is not enough: its top padding can
     * be negative, and it does not know about the capsule margins.
     */
    private int getContentInsetTop() {
        if (mCapsuleGlass == null) return getPaddingTop();
        // Content inset uses only the margin. Inner padding is applied later
        // in computeContentBounds when expanding the capsule around content.
        // This keeps the content vertically centered in the dock regardless
        // of inner padding values. Use max(marginTop, marginBottom) for both
        // sides so content is centered when margins are asymmetric.
        int margin = Math.max(mCapsuleMarginTopPx, mCapsuleMarginBottomPx);
        return Math.max(getPaddingTop(), margin);
    }

    private int getContentInsetBottom() {
        if (mCapsuleGlass == null) return getPaddingBottom();
        int margin = Math.max(mCapsuleMarginTopPx, mCapsuleMarginBottomPx);
        return Math.max(getPaddingBottom(), margin);
    }

    private int getContentInsetLeft() {
        if (mCapsuleGlass == null) return getPaddingLeft();
        return Math.max(getPaddingLeft(), mCapsuleMarginLeftPx);
    }

    private int getContentInsetRight() {
        if (mCapsuleGlass == null) return getPaddingRight();
        return Math.max(getPaddingRight(), mCapsuleMarginRightPx);
    }

    /**
     * Full-width docks ignore the QSB-centering padding that DeviceProfile applies to the
     * Hotseat (it is only meaningful when the QSB is inline). The bars use the capsule
     * margins symmetrically instead.
     */
    private int getDockInsetLeft() {
        return isFullWidthDock() ? mCapsuleMarginLeftPx : getContentInsetLeft();
    }

    private int getDockInsetRight() {
        return isFullWidthDock() ? mCapsuleMarginRightPx : getContentInsetRight();
    }

    // Temporary clamped insets computed by clampContentInsets().
    private int mClampedInsetTop;
    private int mClampedInsetBottom;

    /**
     * Capsule margin/padding prefs can demand more vertical space than the hotseat
     * height allows. Shrink the margin-driven parts of the insets (bottom first, as
     * its margin part is less critical than the top) so content always fits; icons
     * need iconSizePx, and the QSB needs the full qsb height.
     */
    private void clampContentInsets(int height, int insetTop, int insetBottom,
            DeviceProfile dp) {
        mClampedInsetTop = insetTop;
        mClampedInsetBottom = insetBottom;
        if (mCapsuleGlass == null) return;
        int required = Math.max(dp.iconSizePx, dp.getHotseatProfile().getQsbHeight());
        int shrink = required - (height - insetTop - insetBottom);
        if (shrink <= 0) return;
        // Content insets are margin-only. Shrink down to 0 if needed.
        int fromBottom = Math.min(shrink, Math.max(0, mClampedInsetBottom));
        mClampedInsetBottom -= fromBottom;
        shrink -= fromBottom;
        int fromTop = Math.min(shrink, Math.max(0, mClampedInsetTop));
        mClampedInsetTop -= fromTop;
        shrink -= fromTop;
        mClampedInsetBottom = Math.max(0, mClampedInsetBottom - shrink);
    }

    /**
     * Number of slots in the pinned-icons grid.
     *
     * This must be the grid capacity, not the number of icons currently bound to it: the value is
     * used to derive the dock cell width/border space, so a dock with empty slots (an icon was
     * dragged out, or the user never filled it) would otherwise be given oversized cells that
     * spread the icons out and push the last ones off the visible row.
     */
    private int getPinnedIconCount(DeviceProfile dp) {
        return Math.max(1, dp.numShownHotseatIcons);
    }

    /** User-configured gap between dock app cells, in px. */
    private int getDockAppSpacingPx() {
        return Math.round(preferenceManager.getHotseatAppSpacing().get()
                * getResources().getDisplayMetrics().density);
    }

    /**
     * Whether the dock bar should span the available width instead of hugging its content.
     * Auto follows the form factor: a phone dock is full width once it is (nearly) full,
     * and hugs its icons when it only holds a couple of apps, so it never renders as a long
     * empty bar. Tablets always hug their content.
     */
    private boolean isFullWidthDock() {
        String mode = preferenceManager.getHotseatDockWidthMode().get();
        if ("full".equals(mode)) return true;
        if ("content".equals(mode)) return false;
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (!dp.getDeviceProperties().isPhone()) return false;
        // A search box shares the dock bar, so keep the full-width bar in that case.
        if (mQsb != null && mQsb.getVisibility() != GONE) return true;
        return getOccupiedIconColumns(dp) >= Math.max(2, dp.numShownHotseatIcons - 1);
    }

    /**
     * Number of dock columns that actually contain an icon. Empty trailing slots are not
     * counted, so the content-width dock can hug the apps that are really there.
     */
    private int getOccupiedIconColumns(DeviceProfile dp) {
        int columns = 0;
        if (mPagedView != null) {
            CellLayout page = mPagedView.getCurrentCellLayout();
            if (page != null) {
                ViewGroup icons = page.getShortcutsAndWidgets();
                for (int i = 0; i < icons.getChildCount(); i++) {
                    View child = icons.getChildAt(i);
                    if (child.getVisibility() == GONE) continue;
                    if (child.getLayoutParams()
                            instanceof com.android.launcher3.celllayout.CellLayoutLayoutParams lp) {
                        columns = Math.max(columns, lp.getCellX() + 1);
                    } else {
                        columns++;
                    }
                }
            }
        }
        return Math.min(columns, getPinnedIconCount(dp));
    }

    /** Width of the fixed dock row: one cell per occupied slot plus the configured gaps. The
     * capsule adds its left/right inner padding around this row. */
    private int getNaturalIconRowWidth(DeviceProfile dp) {
        int count = Math.max(1, getOccupiedIconColumns(dp));
        return dp.iconSizePx * count + getDockAppSpacingPx() * Math.max(0, count - 1);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);

        DeviceProfile dp = mActivity.getDeviceProfile();
        // Content insets use only the margin (not inner padding). Inner padding
        // is applied later when expanding the capsule around the content bounds.
        // This keeps the content vertically centered in the dock regardless of
        // padding changes; the capsule is then centered around the content.
        clampContentInsets(heightSize, getContentInsetTop(), getContentInsetBottom(), dp);
        int paddingLeft = getDockInsetLeft();
        int paddingRight = getDockInsetRight();
        int paddingTop = mClampedInsetTop;
        int paddingBottom = mClampedInsetBottom;

        if (mQsbDisabled) {
            mQsb.setVisibility(GONE);
        }
        boolean qsbShown = mQsb != null && mQsb.getVisibility() != GONE;
        if (!qsbShown && dp.isQsbInline) {
            // QSB is turned off: the dock icons take over the space it would occupy.
            paddingLeft = 0;
            paddingRight = 0;
        }
        if (isFullWidthDock()) {
            // Full-width dock: the inner padding is between the capsule edge and the icons.
            paddingLeft += mCapsuleInnerPaddingLeftPx;
            paddingRight += mCapsuleInnerPaddingRightPx;
        }

        boolean showIndicator = mPagedView.isPagingEnabled() && !dp.isVerticalBarLayout();
        int indicatorSpace = showIndicator ? mPageIndicatorHeight : 0;

        int pagerWidth = widthSize - paddingLeft - paddingRight;
        int pagerHeight = heightSize - paddingTop - paddingBottom;

        // Reserve the pinned-icons grid's minimum width so recent apps can never
        // shrink the icon row below iconSizePx per cell (which would squish icons).
        // Note: do NOT include hotseatBorderSpace in the minimum — that value is
        // the *expansion* gap when the dock is full-width, not a fixed minimum gap.
        // CellLayout auto-computes a smaller border space when the row narrows.
        int pinnedCount = getPinnedIconCount(dp);
        // QSB off: keep the icon row at its natural width (padding + cells + user spacing)
        // and center icons + recents as one group instead of stretching the icons across
        // the whole screen. This makes the capsule width orientation-independent.
        boolean compactDock = !qsbShown && !isFullWidthDock();
        int naturalIconRowWidth = getNaturalIconRowWidth(dp);
        int minIconRowWidth = compactDock ? naturalIconRowWidth : dp.iconSizePx * pinnedCount;
        int maxRecentWidth = Math.max(0, pagerWidth - minIconRowWidth);

        int recentWidth = 0;
        if (mRecentAppsView != null && mRecentAppsView.getVisibility() != GONE) {
            // Tell the strip its width budget so it only renders as many
            // recent-app icons as fit, leaving the rest of the space for
            // the pinned-icons row.
            mRecentAppsView.setMaxAvailableWidth(maxRecentWidth);
            mRecentAppsView.measure(
                    makeMeasureSpec(maxRecentWidth, MeasureSpec.AT_MOST),
                    makeMeasureSpec(heightSize, MeasureSpec.AT_MOST));
            recentWidth = mRecentAppsView.getMeasuredWidth();
        }
        // Recent apps stick directly to the icons with no extra gap.
        // The width budget (maxAvailableWidth) already trims the strip to fit.
        int recentSpace = Math.min(recentWidth, maxRecentWidth);
        int iconRowWidth = Math.max(0, pagerWidth - recentSpace);
        if (compactDock) {
            iconRowWidth = Math.min(iconRowWidth, naturalIconRowWidth);
        }

        mIconsContainer.measure(
                makeMeasureSpec(Math.max(0, pagerWidth), MeasureSpec.EXACTLY),
                makeMeasureSpec(Math.max(0, pagerHeight), MeasureSpec.EXACTLY));

        int pageHeight = Math.max(0, pagerHeight - indicatorSpace);
        mPagedView.measure(
                makeMeasureSpec(iconRowWidth, MeasureSpec.EXACTLY),
                makeMeasureSpec(pageHeight, MeasureSpec.EXACTLY));

        // Guard against dock icon deformation: when recent apps shrink the icon row,
        // CellLayout would otherwise derive a cell width below iconSizePx, squishing icons.
        // Always recompute so border space tracks iconRowWidth changes (e.g. rotation).
        if (compactDock) {
            int numIcons = Math.max(1, pinnedCount);
            int userSpacing = getDockAppSpacingPx();
            int maxSpacing = numIcons > 1
                    ? Math.max(0, iconRowWidth - dp.iconSizePx * numIcons) / (numIcons - 1)
                    : 0;
            int spacing = Math.min(userSpacing, maxSpacing);
            for (int i = 0; i < mPagedView.getChildCount(); i++) {
                CellLayout page = mPagedView.getPageAt(i);
                if (page == null) continue;
                page.setCellDimensions(dp.iconSizePx, page.getCellHeight(),
                        new android.graphics.Point(spacing, spacing));
                page.measure(
                        makeMeasureSpec(iconRowWidth, MeasureSpec.EXACTLY),
                        makeMeasureSpec(pageHeight, MeasureSpec.EXACTLY));
            }
        } else if (recentSpace > 0) {
            int minCellWidth = dp.iconSizePx;
            int numIcons = Math.max(1, pinnedCount);
            for (int i = 0; i < mPagedView.getChildCount(); i++) {
                CellLayout page = mPagedView.getPageAt(i);
                if (page == null) continue;
                // Compute a border space that lets minCellWidth cells fit in iconRowWidth.
                // The default hotseatBorderSpace is sized for the full-width row and
                // would overflow once recent apps claim part of the dock.
                int totalIconWidth = minCellWidth * numIcons;
                int borderBudget = Math.max(0, iconRowWidth - totalIconWidth);
                int borderSpace = numIcons > 1 ? borderBudget / (numIcons - 1) : 0;
                page.setCellDimensions(minCellWidth, page.getCellHeight(),
                        new android.graphics.Point(borderSpace, borderSpace));
                // Re-measure the page so its children are re-measured with the
                // corrected cell width and border space.
                page.measure(
                        makeMeasureSpec(iconRowWidth, MeasureSpec.EXACTLY),
                        makeMeasureSpec(pageHeight, MeasureSpec.EXACTLY));
            }
        } else {
            // No recent apps: restore auto-computed cell sizing so the full-width
            // dock gets its normal DeviceProfile border space back.
            // Use clearFixedCellDimensions (not resetCellSize) to avoid requestLayout()
            // during the measure pass, then re-measure so the auto-computed sizes apply.
            for (int i = 0; i < mPagedView.getChildCount(); i++) {
                CellLayout page = mPagedView.getPageAt(i);
                if (page != null) {
                    page.clearFixedCellDimensions(dp);
                    page.measure(
                            makeMeasureSpec(iconRowWidth, MeasureSpec.EXACTLY),
                            makeMeasureSpec(pageHeight, MeasureSpec.EXACTLY));
                }
            }
        }

        if (showIndicator) {
            mPageIndicator.measure(
                    makeMeasureSpec(iconRowWidth, MeasureSpec.EXACTLY),
                    makeMeasureSpec(mPageIndicatorHeight, MeasureSpec.EXACTLY));
        }

        if (qsbShown) {
            int width;
            if (dp.isQsbInline) {
                width = dp.hotseatQsbWidth;
            } else {
                width = mPagedView.getMeasuredWidth();
            }

            mQsb.measure(makeMeasureSpec(Math.max(0, width), MeasureSpec.EXACTLY),
                    makeMeasureSpec(dp.getHotseatProfile().getQsbHeight(), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;
        DeviceProfile dp = mActivity.getDeviceProfile();
        clampContentInsets(height, getContentInsetTop(), getContentInsetBottom(), dp);
        int paddingLeft = getDockInsetLeft();
        int paddingRight = getDockInsetRight();
        int paddingTop = mClampedInsetTop;
        int paddingBottom = mClampedInsetBottom;
        float density = getResources().getDisplayMetrics().density;
        if (mQsbDisabled) {
            mQsb.setVisibility(GONE);
        }
        boolean qsbShown = mQsb != null && mQsb.getVisibility() != GONE;
        if (!qsbShown && dp.isQsbInline) {
            // QSB is turned off: let the dock icons use the space it would have occupied so
            // the dock keeps the exact same height and stays centered.
            paddingLeft = 0;
            paddingRight = 0;
        }
        if (isFullWidthDock()) {
            paddingLeft += mCapsuleInnerPaddingLeftPx;
            paddingRight += mCapsuleInnerPaddingRightPx;
        }

        mIconsContainer.layout(paddingLeft, paddingTop, width - paddingRight,
                height - paddingBottom);

        int containerWidth = mIconsContainer.getWidth();
        int containerHeight = mIconsContainer.getHeight();
        boolean showIndicator = mPagedView.isPagingEnabled() && !dp.isVerticalBarLayout();
        int indicatorSpace = showIndicator ? mPageIndicatorHeight : 0;

        int recentWidth = 0;
        if (mRecentAppsView != null && mRecentAppsView.getVisibility() != GONE) {
            recentWidth = mRecentAppsView.getMeasuredWidth();
        }
        // Recent apps stick directly to the icons with no extra gap.
        int minIconRowWidth = dp.iconSizePx * getPinnedIconCount(dp);
        int maxRecentWidth = Math.max(0, containerWidth - minIconRowWidth);
        int recentSpace = Math.min(recentWidth, maxRecentWidth);
        int iconRowWidth = Math.max(0, containerWidth - recentSpace);
        boolean compactDock = !qsbShown && !isFullWidthDock();
        if (compactDock) {
            int naturalIconRowWidth = getNaturalIconRowWidth(dp);
            iconRowWidth = Math.min(iconRowWidth, naturalIconRowWidth);
        }
        // When the QSB is off, center the icon row + recents group inside the dock.
        int rowLeft = compactDock ? (containerWidth - iconRowWidth - recentSpace) / 2 : 0;

        mPagedView.layout(rowLeft, 0, rowLeft + iconRowWidth,
                Math.max(0, containerHeight - indicatorSpace));

        if (showIndicator) {
            int indicatorTop = containerHeight - mPageIndicatorHeight;
            mPageIndicator.layout(rowLeft, indicatorTop, rowLeft + iconRowWidth,
                    indicatorTop + mPageIndicatorHeight);
            mPageIndicator.setVisibility(VISIBLE);
        } else {
            mPageIndicator.setVisibility(GONE);
        }

        // Lay out the recent apps strip first: it is part of the capsule content and the
        // QSB width below is derived from the capsule bounds.
        if (mRecentAppsView != null && mRecentAppsView.getVisibility() != GONE) {
            int rw = Math.min(mRecentAppsView.getMeasuredWidth(), recentSpace);
            int rh = mRecentAppsView.getMeasuredHeight();
            int recentLeft = rowLeft + iconRowWidth;
            int recentRight = recentLeft + rw;
            int recentTop = (containerHeight - rh) / 2;
            int recentBottom = recentTop + rh;
            mRecentAppsView.layout(recentLeft, recentTop, recentRight, recentBottom);
        }

        if (mCapsuleGlass != null) {
            computeContentBounds();
        }

        int qsbMeasuredWidth = mQsb.getMeasuredWidth();
        int qsbHeight = dp.getHotseatProfile().getQsbHeight();
        boolean qsbInline = isQsbInsideCapsule();
        if (qsbShown) {
            int left;
            if (mCapsuleGlass != null && !qsbInline && !mCapsuleRect.isEmpty()) {
                // Outside the capsule (portrait, or landscape too narrow for inline): match
                // the capsule width so both edges line up.
                int capLeft = Math.round(mCapsuleRect.left);
                int capWidth = Math.round(mCapsuleRect.width());
                if (qsbMeasuredWidth != capWidth) {
                    mQsb.measure(makeMeasureSpec(capWidth, MeasureSpec.EXACTLY),
                            makeMeasureSpec(qsbHeight, MeasureSpec.EXACTLY));
                    qsbMeasuredWidth = capWidth;
                }
                left = capLeft;
            } else if (qsbInline || dp.isQsbInline) {
                int qsbSpace = dp.hotseatBorderSpace;
                left = Utilities.isRtl(getResources()) ? r - paddingRight + qsbSpace
                        : l + paddingLeft - qsbMeasuredWidth - qsbSpace;
            } else {
                left = (width - qsbMeasuredWidth) / 2;
            }
            int right = left + qsbMeasuredWidth;

            int top;
            int bottom;
            if (mCapsuleGlass != null && qsbInline) {
                // Vertically center the QSB within the same content band that the
                // icons container occupies. The icons are laid out inside
                // mInsetsContainer (top=paddingTop, bottom=height-paddingBottom)
                // and CellLayout centers them vertically within that band.
                // Centering QSB in the same band keeps QSB and icons vertically
                // aligned, which is what the capsule wraps around.
                int bandTop = paddingTop;
                int bandBottom = height - paddingBottom;
                top = bandTop + Math.max(0, (bandBottom - bandTop - qsbHeight) / 2);
                bottom = top + qsbHeight;
            } else {
                bottom = height - dp.getQsbOffsetY();
                if (mCapsuleGlass != null) {
                    // QSB sits below the capsule (portrait, or landscape when too narrow to
                    // fit inline): keep a visible gap so it doesn't touch the capsule.
                    bottom += Math.round(QSB_OUTSIDE_CAPSULE_GAP_DP * density);
                }
                top = bottom - qsbHeight;
            }
            mQsb.layout(left, top, right, bottom);
        }

        if (mCapsuleGlass != null) {
            computeContentBounds();
            updateCapsuleCornerRadius();
        }
    }

    /**
     * Sets the alpha value of the specified alpha channel of just our ShortcutAndWidgetContainer.
     */
    public void setIconsAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getIconsAlpha(channelId).setValue(alpha);
    }

    /**
     * Sets the alpha value of just our QSB.
     */
    public void setQsbAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getQsbAlpha(channelId).setValue(alpha);
    }

    /** Returns the alpha channel for ShortcutAndWidgetContainer */
    public MultiProperty getIconsAlpha(@HotseatQsbAlphaId int channelId) {
        return mIconsAlphaChannels.get(channelId);
    }

    /** Returns the alpha channel for Qsb */
    public MultiProperty getQsbAlpha(@HotseatQsbAlphaId int channelId) {
        return mQsbAlphaChannels.get(channelId);
    }

    /**
     * Returns the QSB inside hotseat
     */
    public View getQsb() {
        return mQsb;
    }

    /** Delegates to the current page's shortcuts container. */
    @Nullable
    public ShortcutAndWidgetContainer getShortcutsAndWidgets() {
        CellLayout page = getCurrentPageLayout();
        return page != null ? page.getShortcutsAndWidgets() : null;
    }

    /** Delegates accessibility drag helper to the current page. */
    @Nullable
    public DragAndDropAccessibilityDelegate getDragAndDropAccessibilityDelegate() {
        CellLayout page = getCurrentPageLayout();
        return page != null ? page.getDragAndDropAccessibilityDelegate() : null;
    }

    /** Dumps the Hotseat internal state */
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "Hotseat:");
        writer.println(prefix + "\tpages: " + mPagedView.getPageCount()
                + " pagingEnabled=" + mPagedView.isPagingEnabled());
        mIconsAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mIconsAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH");
        mQsbAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mQsbAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH"
        );
    }

}
