/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.folder;

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer;
import static com.android.launcher3.folder.PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS;
import static com.android.launcher3.model.data.FolderInfo.willAcceptItemType;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.jerry.launcherx.modern.folder.LargeFolderBinder;
import com.jerry.launcherx.modern.folder.LargeFolderView;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.EditModeSelectionBadge;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.FolderInfo.LabelState;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.FloatingIconViewCompanion;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FloatingIconViewCompanion,
        DraggableView, Reorderable {

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    @Thunk ActivityContext mActivity;
    @Thunk Folder mFolder;
    public FolderInfo mInfo;

    private CheckLongPressHelper mLongPressHelper;

    private LargeFolderView mInlineGrid;
    private boolean mInlineActive = false;

    private int mFolderNameColor = Integer.MIN_VALUE;
    private final SharedPreferences.OnSharedPreferenceChangeListener mModernPrefsListener =
            (prefs, key) -> {
                if (LargeFolderBinder.KEY_SHOW_NAME.equals(key)) {
                    updateInlineGrid();
                    requestLayout();
                    invalidate();
                }
            };

    static final int DROP_IN_ANIMATION_DURATION = 400;

    // Flag whether the folder should open itself when an item is dragged over is enabled.
    public static final boolean SPRING_LOADING_ENABLED = true;

    // Delay when drag enters until the folder opens, in miliseconds.
    private static final int ON_OPEN_DELAY = 800;

    @Thunk BubbleTextView mFolderName;
    /** Vertical cell padding the workspace container applies to every child. */
    private int mCellPadTop;

    PreviewBackground mBackground = new PreviewBackground(getContext());
    private boolean mBackgroundIsVisible = true;

    FolderGridOrganizer mPreviewVerifier;
    ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private List<ItemInfo> mCurrentPreviewItems = new ArrayList<>();
    // LauncherX: bounds of the multi-select check mark drawn while this folder is selected.
    private final RectF mSelectionBadgeBounds = new RectF();
    boolean mAnimating = false;

    private Alarm mOpenAlarm = new Alarm(Looper.getMainLooper());

    private boolean mForceHideDot;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private final FolderDotInfo mDotInfo = new FolderDotInfo();
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private DotRenderer.DrawParams mDotParams;
    private float mDotScale;
    private Animator mDotScaleAnim;

    private Rect mTouchArea = new Rect();

    private float mScaleForReorderBounce = 1f;

    private static final Property<FolderIcon, Float> DOT_SCALE_PROPERTY
            = new Property<FolderIcon, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(FolderIcon folderIcon) {
            return folderIcon.mDotScale;
        }

        @Override
        public void set(FolderIcon folderIcon, Float value) {
            folderIcon.mDotScale = value;
            folderIcon.invalidate();
        }
    };

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FolderIcon(Context context) {
        super(context);
        init();
    }

    private void init() {
        mLongPressHelper = new CheckLongPressHelper(this);
        mPreviewLayoutRule = new ClippedFolderIconLayoutRule();
        mPreviewItemManager = new PreviewItemManager(this);
        mDotParams = new DotRenderer.DrawParams();
    }

    public static <T extends Context & ActivityContext> FolderIcon inflateFolderAndIcon(int resId,
            T activityContext, ViewGroup group, FolderInfo folderInfo) {
        Folder folder = Folder.fromXml(activityContext);

        FolderIcon icon = inflateIcon(resId, activityContext, group, folderInfo);
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);

        icon.setFolder(folder);
        return icon;
    }

    /**
     * Builds a FolderIcon to be added to the activity.
     * This method doesn't add any listeners to the FolderInfo, and hence any changes to the info
     * will not be reflected in the folder.
     */
    public static FolderIcon inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group, FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        DeviceProfile grid = activity.getDeviceProfile();
        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        FolderIcon icon = (FolderIcon) inflater.inflate(resId, group, false);

        icon.setClipToPadding(false);
        icon.mFolderName = icon.findViewById(R.id.folder_icon_name);
        CharSequence displayTitle = folderInfo.title;
        if (displayTitle == null || displayTitle.length() == 0) {
            displayTitle = icon.getContext().getString(R.string.unnamed_folder);
        }
        icon.mFolderName.setText(displayTitle);
        icon.mFolderName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.mFolderName.getLayoutParams();
        if (folderInfo.container == ItemInfo.NO_ID) {
            lp.topMargin = grid.getAllAppsProfile().getIconSizePx() + grid.getAllAppsProfile().getIconDrawablePaddingPx();
            icon.mBackground = new PreviewBackground(activity.getDragLayer().getContext());
        } else {
            lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx;
        }

        icon.setTag(folderInfo);
        icon.setOnClickListener(activity.getItemOnClickListener());
        icon.mInfo = folderInfo;
        icon.mActivity = activity;
        icon.mDotRenderer = grid.mDotRendererWorkSpace;

        icon.setContentDescription(icon.getAccessiblityTitle(folderInfo.title));
        icon.updateDotInfo();

        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        icon.mPreviewVerifier = createFolderGridOrganizer(activity.getDeviceProfile());
        icon.mPreviewVerifier.setFolderInfo(folderInfo);
        icon.updatePreviewItems(false);
        icon.updateInlineGrid();

        return icon;
    }

    public void animateBgShadowAndStroke() {
        mBackground.fadeInBackgroundShadow();
        mBackground.animateBackgroundStroke();
    }

    public BubbleTextView getFolderName() {
        return mFolderName;
    }

    public void getPreviewBounds(Rect outBounds) {
        mPreviewItemManager.recomputePreviewDrawingParams();
        mBackground.getBounds(outBounds);
        // The preview items go outside of the bounds of the background.
        Utilities.scaleRectAboutCenter(outBounds, ICON_OVERLAP_FACTOR);
    }

    public float getBackgroundStrokeWidth() {
        return mBackground.getStrokeWidth();
    }

    public Folder getFolder() {
        return mFolder;
    }

    private void setFolder(Folder folder) {
        mFolder = folder;
    }

    private boolean willAcceptItem(ItemInfo item) {
        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        if (mInlineActive) {
            // The whole inline card is the drop target: highlight it and skip the
            // spring-loaded open so the drop lands directly on the desktop folder.
            if (mInlineGrid != null) {
                mInlineGrid.setDropHighlight(true);
            }
            return;
        }
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();
        CellLayout cl = (CellLayout) getParent().getParent();

        mBackground.animateToAccept(cl, lp.getCellX(), lp.getCellY());
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        if (SPRING_LOADING_ENABLED &&
                ((dragInfo instanceof WorkspaceItemFactory)
                        || (dragInfo instanceof PendingAddShortcutInfo)
                        || Folder.willAccept(dragInfo))) {
            mOpenAlarm.setAlarm(ON_OPEN_DELAY);
        }
    }

    OnAlarmListener mOnOpenListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mFolder.beginExternalDrag();
        }
    };

    public Drawable prepareCreateAnimation(final View destView) {
        return mPreviewItemManager.prepareCreateAnimation(destView);
    }

    public void performCreateAnimation(final ItemInfo destInfo, final View destView,
            final ItemInfo srcInfo, final DragObject d, Rect dstRect,
            float scaleRelativeToDragLayer) {
        prepareCreateAnimation(destView);
        getFolder().addFolderContent(destInfo);
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        mPreviewItemManager.createFirstItemAnimation(false /* reverse */, null)
                .start();

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, d, dstRect, scaleRelativeToDragLayer, 1,
                false /* itemReturnedOnFailedDrop */);
    }

    public void performDestroyAnimation(Runnable onCompleteRunnable) {
        // This will animate the final item in the preview to be full size.
        mPreviewItemManager.createFirstItemAnimation(true /* reverse */, onCompleteRunnable)
                .start();
    }

    public void onDragExit() {
        if (mInlineGrid != null) {
            mInlineGrid.setDropHighlight(false);
        }
        mBackground.animateToRest();
        mOpenAlarm.cancelAlarm();
    }

    private void onDrop(final ItemInfo item, DragObject d, Rect finalRect,
            float scaleRelativeToDragLayer, int index, boolean itemReturnedOnFailedDrop) {
        item.cellX = -1;
        item.cellY = -1;
        DragView animateView = d.dragView;
        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null && mActivity instanceof Launcher) {
            final Launcher launcher = (Launcher) mActivity;
            DragLayer dragLayer = launcher.getDragLayer();
            Rect to = finalRect;
            if (to == null && !isInAppDrawer()) {
                to = new Rect();
                Workspace<?> workspace = launcher.getWorkspace();
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform();
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform();
            }

            int numItemsInPreview = Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index + 1);
            boolean itemAdded = false;
            if (itemReturnedOnFailedDrop || index >= MAX_NUM_ITEMS_IN_PREVIEW) {
                List<ItemInfo> oldPreviewItems = new ArrayList<>(mCurrentPreviewItems);
                getFolder().addFolderContent(item, index, false);
                mCurrentPreviewItems.clear();
                mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));

                if (!oldPreviewItems.equals(mCurrentPreviewItems)) {
                    int newIndex = mCurrentPreviewItems.indexOf(item);
                    if (newIndex >= 0) {
                        // If the item dropped is going to be in the preview, we update the
                        // index here to reflect its position in the preview.
                        index = newIndex;
                    }

                    mPreviewItemManager.hidePreviewItem(index, true);
                    mPreviewItemManager.onDrop(oldPreviewItems, mCurrentPreviewItems, item);
                    itemAdded = true;
                } else {
                    getFolder().removeFolderContent(false, item);
                }
            }

            if (!itemAdded) {
                getFolder().addFolderContent(item, index, true);
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, numItemsInPreview, center);
            center[0] = Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = Math.round(scaleRelativeToDragLayer * center[1]);

            // LauncherX-TODO: if to is null we skip immediately to place the item. destination can be null (or nowhere)
            if (to != null) {
                to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                        center[1] - animateView.getMeasuredHeight() / 2);

                float finalAlpha = index < MAX_NUM_ITEMS_IN_PREVIEW ? 1f : 0f;

                float finalScale = scale * scaleRelativeToDragLayer;

                // Account for potentially different icon sizes with non-default grid settings
                if (d.dragSource instanceof ActivityAllAppsContainerView) {
                    DeviceProfile grid = mActivity.getDeviceProfile();
                    float containerScale = (1f * grid.iconSizePx
                            / grid.getAllAppsProfile().getIconSizePx());
                    finalScale *= containerScale;
                }

                final int finalIndex = index;
                dragLayer.animateView(animateView, to, finalAlpha,
                        finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                        Interpolators.DECELERATE_2,
                        () -> {
                            mPreviewItemManager.hidePreviewItem(finalIndex, false);
                            mFolder.showItem(item);
                        },
                        DragLayer.ANIMATION_END_DISAPPEAR, null);

                mFolder.hideItem(item);

                if (!itemAdded) mPreviewItemManager.hidePreviewItem(index, true);
            }
            d.folderNameSuggestionLoader.getSuggestedFolderName(mInfo.getAppContents(),
                    folderNameInfos -> postDelayed(() -> {
                        setLabelSuggestion(folderNameInfos, d.logInstanceId);
                        invalidate();
                    }, DROP_IN_ANIMATION_DURATION));

        } else {
            getFolder().addFolderContent(item);
        }
    }
    
    public boolean isInAppDrawer() {
        return mInfo.container == ItemInfo.NO_ID;
    }

    /**
     * Set the suggested folder name.
     */
    public void setLabelSuggestion(FolderNameInfos nameInfos, InstanceId instanceId) {
        if (!mInfo.getLabelState().equals(LabelState.UNLABELED)) {
            return;
        }
        if (nameInfos == null || !nameInfos.hasSuggestions()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS);
            return;
        }
        if (!nameInfos.hasPrimary()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY);
            return;
        }
        CharSequence newTitle = nameInfos.getLabels()[0];
        FromState fromState = mInfo.getFromLabelState();

        mInfo.setTitle(newTitle, mActivity.getModelWriter());
        onTitleChanged(mInfo.title);
        mFolder.getFolderName().setText(mInfo.title);

        // Logging for folder creation flow
        StatsLogManager.newInstance(getContext()).logger()
                .withInstanceId(instanceId)
                .withItemInfo(mInfo)
                .withFromState(fromState)
                .withToState(ToState.TO_SUGGESTION0)
                // When LAUNCHER_FOLDER_LABEL_UPDATED event.edit_text does not have delimiter,
                // event is assumed to be folder creation on the server side.
                .withEditText(newTitle.toString())
                .log(LAUNCHER_FOLDER_AUTO_LABELED);
    }


    public void onDrop(DragObject d, boolean itemReturnedOnFailedDrop) {
        if (mInlineGrid != null) {
            mInlineGrid.setDropHighlight(false);
        }
        ItemInfo item;
        if (d.dragInfo instanceof WorkspaceItemFactory) {
            // Came from all apps -- make a copy
            item = ((WorkspaceItemFactory) d.dragInfo).makeWorkspaceItem(getContext());
        } else if (d.dragSource instanceof BaseItemDragListener){
            // Came from a different window -- make a copy
            if (d.dragInfo instanceof AppPairInfo) {
                // dragged item is app pair
                item = new AppPairInfo((AppPairInfo) d.dragInfo);
            } else {
                // dragged item is WorkspaceItemInfo
                item = new WorkspaceItemInfo((WorkspaceItemInfo) d.dragInfo);
            }
        } else {
            item = d.dragInfo;
        }
        mFolder.notifyDrop();
        onDrop(item, d, null, 1.0f,
                itemReturnedOnFailedDrop ? item.rank : mInfo.getContents().size(),
                itemReturnedOnFailedDrop
        );
    }

    /** Keep the notification dot up to date with the sum of all the content's dots. */
    public void updateDotInfo() {
        boolean hadDot = mDotInfo.hasDot();
        mDotInfo.reset();
        for (ItemInfo si : mInfo.getContents()) {
            mDotInfo.addDotInfo(mActivity.getDotInfoForItem(si));
        }
        boolean isDotted = mDotInfo.hasDot();
        float newDotScale = isDotted ? 1f : 0f;
        // Animate when a dot is first added or when it is removed.
        if ((hadDot ^ isDotted) && isShown()) {
            animateDotScale(newDotScale);
        } else {
            cancelDotScaleAnim();
            mDotScale = newDotScale;
            invalidate();
        }
    }

    public ClippedFolderIconLayoutRule getLayoutRule() {
        return mPreviewLayoutRule;
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    public boolean hasDot() {
        return mDotInfo != null && mDotInfo.hasDot();
    }

    private float getLocalCenterForIndex(int index, int curNumItems, int[] center) {
        mTmpParams = mPreviewItemManager.computePreviewItemDrawingParams(
                Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index), curNumItems, mTmpParams);

        // Match the offset the preview items are drawn with, so a dropped item lands on
        // the icon it visually replaces.
        mTmpParams.transX += mBackground.layoutOffsetX;
        mTmpParams.transY += mBackground.layoutOffsetY;

        float intrinsicIconSize = mPreviewItemManager.getIntrinsicIconSize();
        float offsetX = mTmpParams.transX + (mTmpParams.scale * intrinsicIconSize) / 2;
        float offsetY = mTmpParams.transY + (mTmpParams.scale * intrinsicIconSize) / 2;

        center[0] = Math.round(offsetX);
        center[1] = Math.round(offsetY);
        return mTmpParams.scale;
    }

    public void setFolderBackground(PreviewBackground bg) {
        mBackground = bg;
        mBackground.setInvalidateDelegate(this);
    }

    @Override
    public void setIconVisible(boolean visible) {
        mBackgroundIsVisible = visible;
        invalidate();
    }

    public boolean getIconVisible() {
        return mBackgroundIsVisible;
    }

    public PreviewBackground getFolderBackground() {
        return mBackground;
    }

    public PreviewItemManager getPreviewItemManager() {
        return mPreviewItemManager;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		if (mInlineActive) {
			// The inline card draws its own edit-mode selection check (see LargeFolderView).
			return;
		}

		if (!mBackgroundIsVisible) {
			return;
		}

        mPreviewItemManager.recomputePreviewDrawingParams();

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackground(canvas);
        }

        if (mCurrentPreviewItems.isEmpty() && !mAnimating) {
            return;
        }

        mPreviewItemManager.draw(canvas);

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackgroundStroke(canvas);
        }

        drawDot(canvas);
        drawSelectionBadge(canvas);
    }

    /**
     * LauncherX: draws the edit-mode selection affordance for this folder - an empty "to be
     * selected" box when unselected, a check mark when selected.
     */
    private void drawSelectionBadge(Canvas canvas) {
        if (mInfo == null || mInlineActive
                || !EditModeSelectionBadge.shouldShow(mActivity, BubbleTextView.DISPLAY_WORKSPACE)) {
            return;
        }
        final boolean selected = EditModeSelectionBadge.isSelected(mActivity, mInfo);
        final int iconSize = mActivity.getDeviceProfile().iconSizePx;
        Rect iconBounds = new Rect();
        iconBounds.left = (getWidth() - iconSize) / 2;
        iconBounds.right = iconBounds.left + iconSize;
        iconBounds.top = getPaddingTop();
        iconBounds.bottom = iconBounds.top + iconSize;
        float iconScale = (float) mBackground.previewSize / iconSize;
        Utilities.scaleRectAboutCenter(iconBounds, iconScale);
        EditModeSelectionBadge.computeBounds(
                iconBounds, mSelectionBadgeBounds, getResources().getDisplayMetrics().density);
        if (selected) {
            EditModeSelectionBadge.draw(canvas, mSelectionBadgeBounds);
        } else {
            EditModeSelectionBadge.drawUnselected(canvas, mSelectionBadgeBounds);
        }
    }

    public void drawDot(Canvas canvas) {
        if (!mForceHideDot && ((mDotInfo != null && mDotInfo.hasDot()) || mDotScale > 0)) {
            Rect iconBounds = mDotParams.iconBounds;
            // FolderIcon draws the icon to be top-aligned (with padding) & horizontally-centered
            int iconSize = mActivity.getDeviceProfile().iconSizePx;
            iconBounds.left = (getWidth() - iconSize) / 2;
            iconBounds.right = iconBounds.left + iconSize;
            iconBounds.top = getPaddingTop();
            iconBounds.bottom = iconBounds.top + iconSize;

            float iconScale = (float) mBackground.previewSize / iconSize;
            Utilities.scaleRectAboutCenter(iconBounds, iconScale);

            // If we are animating to the accepting state, animate the dot out.
            mDotParams.scale = Math.max(0, mDotScale - mBackground.getAcceptScaleProgress());
            mDotParams.dotColor = mBackground.getDotColor();
            mDotRenderer.draw(canvas, mDotParams);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // ShortcutAndWidgetContainer.measureChild sets cell padding on every
        // workspace child (including this FolderIcon) right before measuring.
        // Remember it before the inline path resets padding to zero, so the
        // desktop label can use the exact same vertical logic as app icons.
        mCellPadTop = getPaddingTop();
        boolean shouldCenterIcon = mActivity.getDeviceProfile().iconCenterVertically;
        if (shouldCenterIcon) {
            int iconSize = mActivity.getDeviceProfile().iconSizePx;
            Paint.FontMetrics fm = mFolderName.getPaint().getFontMetrics();
            int cellHeightPx = iconSize + mFolderName.getCompoundDrawablePadding()
                    + (int) Math.ceil(fm.bottom - fm.top);
            setPadding(getPaddingLeft(), (MeasureSpec.getSize(heightMeasureSpec)
                    - cellHeightPx) / 2, getPaddingRight(), getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mInlineActive && mInlineGrid != null && mInlineGrid.getVisibility() == VISIBLE) {
            // Reset vertical padding so that onLayout coordinates align with
            // the CellLayout row grid: row 0 top == view top. Without this the
            // iconCenterVertically padding shifts children and breaks label
            // alignment with desktop app labels.
            setPadding(getPaddingLeft(), 0, getPaddingRight(), getPaddingBottom());
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            // Inline large folders never draw the legacy icon preview, so the preview layout
            // rule (icon size / background radius) is never initialised. The folder open
            // animation still reads those values, so make sure they are computed.
            mPreviewItemManager.recomputePreviewDrawingParams();
            int w = getMeasuredWidth();
            int h = getMeasuredHeight();
            int nameH = 0;
            if (mFolderName.getVisibility() != GONE) {
                mFolderName.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                nameH = mFolderName.getMeasuredHeight();
            }
            // Card height = total height - label band height - icon/label gap.
            // The label band uses the same logic as desktop apps: the space
            // below iconSize + drawablePadding within one cell, so the label
            // top lines up with app labels. The card additionally stops one
            // iconDrawablePaddingPx short of the label, matching the gap apps
            // and small folders have between their icon and the label. When the
            // label is hidden both values are zero and the card fills the cell.
            mInlineGrid.setBottomContentInset(0);
            mInlineGrid.measure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(inlineCardHeight(h), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mInlineActive && mInlineGrid != null && mInlineGrid.getVisibility() == VISIBLE) {
            int w = r - l;
            int h = b - t;
            int bandH = inlineLabelBandHeight(h);
            int cardH = inlineCardHeight(h);
            int labelY = Math.max(0, h - bandH);
            mInlineGrid.layout(0, 0, w, cardH);
            mFolderName.layout(0, labelY, w, Math.min(labelY + bandH, h));
            return;
        }
        super.onLayout(changed, l, t, r, b);
    }

    /**
     * Height of the label band reserved at the bottom of the inline card.
     * Uses the same logic as desktop apps: the space below
     * iconSize + drawablePadding within one cell. Returns 0 when the label is
     * hidden so the card reclaims the band.
     */
    private int inlineLabelBandHeight(int viewHeight) {
        if (mFolderName.getVisibility() == GONE) {
            return 0;
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        int borderSpace = dp.cellLayoutBorderSpacePx.y;
        int spanY = (mInfo != null) ? mInfo.spanY : 1;
        int rowPitch = (viewHeight + borderSpace) / spanY;
        int cellH = rowPitch - borderSpace;
        return Math.max(0, cellH - mCellPadTop
                - dp.iconSizePx - dp.iconDrawablePaddingPx);
    }

    /**
     * Card height: everything above the label band, minus the icon/label gap
     * so the card bottom-to-label gap matches app icons and small folders.
     */
    private int inlineCardHeight(int viewHeight) {
        if (mFolderName.getVisibility() == GONE) {
            return viewHeight;
        }
        return Math.max(0, viewHeight - inlineLabelBandHeight(viewHeight)
                - mActivity.getDeviceProfile().iconDrawablePaddingPx);
    }

    /** Height of the inline large-folder card (excluding the title band), in view pixels. */
    public int getInlineCardHeightPx() {
        if (mInlineGrid != null && mInlineGrid.getHeight() > 0) {
            return mInlineGrid.getHeight();
        }
        return inlineCardHeight(getHeight());
    }

    /**
     * Hides/shows the inline large-folder card (e.g. while the folder popup is open). Uses alpha
     * instead of visibility so the label keeps its inline position while hidden.
     */
    public void setInlineCardVisible(boolean visible) {
        if (mInlineActive && mInlineGrid != null) {
            mInlineGrid.setAlpha(visible ? 1f : 0f);
            mFolderName.setAlpha(visible ? 1f : 0f);
        }
    }

    /**
     * Hides/shows the desktop label of a small folder while its popup is open. Inline large
     * folders are handled by {@link #setInlineCardVisible(boolean)}.
     */
    public void setFolderLabelVisible(boolean visible) {
        if (!mInlineActive) {
            mFolderName.setAlpha(visible ? 1f : 0f);
        }
    }

    /** Sets the visibility of the icon's title text */
    public void setTextVisible(boolean visible) {
        if (visible && mInlineActive && !LargeFolderBinder.isShowName(getContext())) {
            // Large folders can opt out of the title entirely; GONE (not INVISIBLE)
            // so the inline grid reclaims the reserved band.
            mFolderName.setVisibility(GONE);
            return;
        }
        mFolderName.setVisibility(visible ? VISIBLE : INVISIBLE);
        if (visible) {
            ensureFolderNameMinSize();
        }
    }

    /**
     * The global folder-label pref multiplies folderLabelTextSizePx by a factor that can
     * be 0, making the title invisible even when VISIBLE. Force a minimum readable size
     * whenever the title is shown on the workspace.
     */
    private void ensureFolderNameMinSize() {
        float minSize = mActivity.getDeviceProfile().iconSizePx / 4f;
        if (mFolderName.getTextSize() < minSize) {
            mFolderName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, minSize);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().getSharedPreferences(LargeFolderBinder.PREFS_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(mModernPrefsListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBackground.clearGlassListener();
        getContext().getSharedPreferences(LargeFolderBinder.PREFS_NAME, Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(mModernPrefsListener);
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    /**
     * Returns the list of items which should be visible in the preview
     */
    public List<ItemInfo> getPreviewItemsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mInfo).previewItemsForPage(page, mInfo.getContents());
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return mPreviewItemManager.verifyDrawable(who) || super.verifyDrawable(who);
    }

    private void updatePreviewItems(boolean animate) {
        mPreviewItemManager.updatePreviewItems(animate);
        mCurrentPreviewItems.clear();
        mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));
    }

    /**
     * Updates the preview items which match the provided condition
     */
    public void updatePreviewItems(Predicate<ItemInfo> itemCheck) {
        mPreviewItemManager.updatePreviewItems(itemCheck);
    }

    public void onItemsChanged(boolean animate) {
        updateInlineGrid();
        updatePreviewItems(false);
        updateDotInfo();
        setContentDescription(getAccessiblityTitle(mInfo.title));
        updatePreviewItems(animate);
        invalidate();
        requestLayout();
    }

    private void updateInlineGrid() {
        int spanX = mInfo != null ? mInfo.spanX : 1;
        int spanY = mInfo != null ? mInfo.spanY : 1;
        if (getLayoutParams() instanceof CellLayoutLayoutParams lp) {
            // During a resize drag the layout params are updated before the model, and some
            // reorder paths briefly report a stale span. Take the larger of the two so a
            // transient mismatch can never flip an active large folder back to the legacy
            // preview (the resize commit path syncs the model span immediately).
            spanX = Math.max(spanX, lp.cellHSpan);
            spanY = Math.max(spanY, lp.cellVSpan);
        }
        boolean inline = mInfo != null
                && (spanX > 1 || spanY > 1)
                && LargeFolderBinder.isEnabled(getContext());
        android.util.Log.d("LargeFolder",
                "updateInlineGrid span=" + spanX + "x" + spanY
                + " info=" + (mInfo != null ? mInfo.spanX + "x" + mInfo.spanY : "null")
                + " lp=" + (getLayoutParams() instanceof CellLayoutLayoutParams clp
                        ? clp.cellHSpan + "x" + clp.cellVSpan : "none")
                + " inline=" + inline);
        if (!inline) {
            if (mInlineGrid != null) {
                mInlineGrid.setVisibility(GONE);
                mInlineGrid.setDropHighlight(false);
            }
            mInlineActive = false;
            // Colour is no longer overridden for inline folders; nothing to restore.
            if (mFolderName.getVisibility() == GONE) {
                // The large-folder title opt-out no longer applies to a legacy icon.
                mFolderName.setVisibility(VISIBLE);
            }
            return;
        }
        if (mInlineGrid == null) {
            mInlineGrid = new LargeFolderView(getContext());
            // Height 0 so FrameLayout's own measure pass (which subtracts our top
            // padding) is a harmless no-op; onMeasure below re-measures the grid
            // EXACTLY to (height - nameH), the size it is laid out at. Measuring it
            // twice with different heights flips the icon bounds every pass.
            addView(mInlineGrid, 0, new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, 0));
        }
        LargeFolderBinder.bindInline(mInlineGrid, this, mInfo, spanX, spanY,
                mActivity.getItemOnClickListener());
        mInlineGrid.setFolderInfo(mInfo);
        mInlineGrid.setVisibility(VISIBLE);
        mInlineActive = true;
        // Keep the workspace label colour so the large-folder desktop name matches
        // app labels and small-folder names (the colour is configurable via the
        // workspace text-colour setting).
        // Respect the large-folder title opt-out (WorkspaceLayoutManager may have
        // just forced the title visible again on this bind pass).
        boolean showName = LargeFolderBinder.isShowName(getContext());
        mFolderName.setVisibility(showName ? VISIBLE : GONE);
        if (showName) {
            ensureFolderNameMinSize();
        }
    }

    /** Whether the inline large-folder grid is the active rendering of this icon. */
    public boolean isInlineActive() {
        return mInlineActive;
    }

    /** Returns the inline grid child view whose [ItemInfo] matches, or null. */
    public @Nullable View getInlineChildFor(Predicate<ItemInfo> matcher) {
        if (!mInlineActive || mInlineGrid == null) return null;
        return mInlineGrid.findChildByMatcher(info -> matcher.test(info));
    }

    /** The inline large-folder grid, or null when this icon renders as a regular folder. */
    public @Nullable LargeFolderView getInlineGrid() {
        return mInlineActive ? mInlineGrid : null;
    }

    public void onSpanChanged() {
        updateInlineGrid();
        requestLayout();
        invalidate();
    }

    public void onTitleChanged(CharSequence title) {
        // Show a default name when the folder has no title yet, so the desktop
        // label is visible immediately after the folder is created.
        CharSequence display = title;
        if (display == null || display.length() == 0) {
            display = getContext().getString(R.string.unnamed_folder);
        }
        mFolderName.setText(display);
        setContentDescription(getAccessiblityTitle(title));
    }

    /**
     * Shows a dialog to rename the folder from the desktop label. Used when the
     * user taps the folder name on the workspace (especially for large folders
     * which have no other rename entry point outside the opened folder).
     */
    public void showRenameDialog() {
        if (mFolder == null || mActivity == null) return;
        final Context raw = getContext();
        // The launcher activity theme inherits Theme.DeviceDefault.Light, so a platform
        // dialog built from it always gets a light window background. Launcher3 then
        // recolours text for dark mode at runtime, which leaves white text on a white
        // panel. Picking the platform dialog theme that actually matches the launcher's
        // light/dark state keeps the background, title, body and buttons consistent.
        final int dialogTheme = Utilities.isDarkTheme(raw)
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        final Context themed = new android.view.ContextThemeWrapper(raw, dialogTheme);

        final android.widget.EditText input = new android.widget.EditText(themed);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setTextColor(resolveAttrColor(raw, R.attr.popupTextColor));
        input.setHintTextColor(ColorUtils.setAlphaComponent(
                resolveAttrColor(raw, R.attr.popupTextColor), 0x8C));
        input.setBackground(null);
        input.setPadding(0, input.getPaddingTop(), 0, input.getPaddingBottom());
        CharSequence current = mInfo.title;
        String initial = (current == null || current.length() == 0)
                ? getContext().getString(R.string.unnamed_folder)
                : current.toString();
        input.setText(initial);
        input.setSelection(initial.length());

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(themed)
                .setTitle(R.string.folder_hint_text)
                .setView(input, 0, 0, 0, 0)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newTitle = input.getText().toString().trim();
                    mInfo.setTitle(newTitle, mActivity.getModelWriter());
                    onTitleChanged(newTitle);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    /** Resolves a launcher theme colour attribute to a concrete ARGB value. */
    private static int resolveAttrColor(Context context, int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) {
            return android.graphics.Color.BLACK;
        }
        if (value.resourceId != 0) {
            return ContextCompat.getColor(context, value.resourceId);
        }
        return value.data;
    }

    private float mTouchDownX;
    private float mTouchDownY;

    /** Local coordinates of the last accepted {@link MotionEvent#ACTION_DOWN}. */
    public float getTouchDownX() {
        return mTouchDownX;
    }

    /** Local coordinates of the last accepted {@link MotionEvent#ACTION_DOWN}. */
    public float getTouchDownY() {
        return mTouchDownY;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDownX = event.getX();
            mTouchDownY = event.getY();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && shouldIgnoreTouchDown(event.getX(), event.getY())) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Remember where this gesture started so the resize frame (opened by the
            // long-press below) can take over the *same* gesture and drag the folder
            // once the finger moves past the touch slop.
            mTouchDownX = event.getX();
            mTouchDownY = event.getY();
        }

        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        super.onTouchEvent(event);
        mLongPressHelper.onTouchEvent(event);
        // Keep receiving the rest of the events
        return true;
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        mTouchArea.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        return !mTouchArea.contains((int) x, (int) y);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    private boolean isInHotseat() {
        return mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    public void clearLeaveBehindIfExists() {
        if (isInAppDrawer()) return;
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).clearFolderLeaveBehind(this);
        }
    }

    public void drawLeaveBehindIfExists() {
        if (isInAppDrawer()) return;
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).drawFolderLeaveBehindForIcon(this);
        }
    }

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        if (mInlineActive) {
            // Drag the full inline card (grid + name) instead of the small legacy
            // preview area, so the DragView keeps the large-folder appearance.
            bounds.set(0, 0, getWidth(), getHeight());
            return;
        }
        getPreviewBounds(bounds);
    }

    /**
     * Returns a formatted accessibility title for folder
     */
    public String getAccessiblityTitle(CharSequence title) {
        if (title == null) {
            // Avoids "Talkback -> Folder: null" announcement.
            title = getContext().getString(R.string.unnamed_folder);
        }
        int size = mInfo.getContents().size();
        if (size < MAX_NUM_ITEMS_IN_PREVIEW) {
            return getContext().getString(R.string.folder_name_format_exact, title, size);
        } else {
            return getContext().getString(R.string.folder_name_format_overflow, title,
                    MAX_NUM_ITEMS_IN_PREVIEW);
        }
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);
        if (enableCursorHoverStates()) {
            mBackground.setHovered(hovered);
        }
    }

    /**
     * Interface that provides callbacks to a parent ViewGroup that hosts this FolderIcon.
     */
    public interface FolderIconParent {
        /**
         * Tells the FolderIconParent to draw a "leave-behind" when the Folder is open and leaving a
         * gap where the FolderIcon would be when the Folder is closed.
         */
        void drawFolderLeaveBehindForIcon(FolderIcon child);
        /**
         * Tells the FolderIconParent to stop drawing the "leave-behind" as the Folder is closed.
         */
        void clearFolderLeaveBehind(FolderIcon child);
    }
}
