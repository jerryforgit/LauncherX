package com.android.launcher3.folder;

import static androidx.core.view.HapticFeedbackConstantsCompat.CLOCK_TICK;
import static com.android.launcher3.LauncherAnimUtils.LAYOUT_HEIGHT;
import static com.android.launcher3.LauncherAnimUtils.LAYOUT_WIDTH;
import static com.android.launcher3.Utilities.ATLEAST_Q;
import static com.android.launcher3.views.BaseDragLayer.LAYOUT_X;
import static com.android.launcher3.views.BaseDragLayer.LAYOUT_Y;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.FirstFrameAnimatorHelper;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.CellPosMapper.CellPos;
import com.android.launcher3.celllayout.ItemConfiguration;
import com.android.launcher3.celllayout.MulticellReorderAlgorithm;
import com.android.launcher3.celllayout.ReorderParameters;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.touch.ItemLongClickListener;

import java.util.ArrayList;
import java.util.List;

import com.jerry.launcherx.modern.folder.LargeFolderBinder;
import com.jerry.launcherx.theme.color.tokens.ColorTokens;

public class FolderResizeFrame extends AbstractFloatingView implements View.OnKeyListener {
    private static final int SNAP_DURATION_MS = 150;
    private static final float DIMMED_HANDLE_ALPHA = 0f;
    private static final float RESIZE_THRESHOLD = 0.66f;

    private static final Rect sTmpRect = new Rect();

    private static final int HANDLE_COUNT = 4;
    private static final int INDEX_LEFT = 0;
    private static final int INDEX_TOP = 1;
    private static final int INDEX_RIGHT = 2;
    private static final int INDEX_BOTTOM = 3;

    private static long sJustClosedAt;

    private final Launcher mLauncher;
    private final FirstFrameAnimatorHelper mFirstFrameAnimatorHelper;

    private final View[] mDragHandles = new View[HANDLE_COUNT];
    private final List<Rect> mSystemGestureExclusionRects = new ArrayList<>(HANDLE_COUNT);

    private FolderIcon mFolderIcon;
    private FolderInfo mFolderInfo;
    private CellLayout mCellLayout;
    private DragLayer mDragLayer;

    private final int mBackgroundPadding;
    private final int mTouchTargetWidth;

    private final int[] mDirectionVector = new int[2];
    private final int[] mLastDirectionVector = new int[2];

    private final IntRange mTempRange1 = new IntRange();
    private final IntRange mTempRange2 = new IntRange();

    private final IntRange mDeltaXRange = new IntRange();
    private final IntRange mBaselineX = new IntRange();

    private final IntRange mDeltaYRange = new IntRange();
    private final IntRange mBaselineY = new IntRange();

    private boolean mLeftBorderActive;
    private boolean mRightBorderActive;
    private boolean mTopBorderActive;
    private boolean mBottomBorderActive;

    private boolean mHorizontalResizeActive;
    private boolean mVerticalResizeActive;

    private int mRunningHInc;
    private int mRunningVInc;
    private int mMinHSpan;
    private int mMinVSpan;
    private int mMaxHSpan;
    private int mMaxVSpan;
    private int mDeltaX;
    private int mDeltaY;
    private int mDeltaXAddOn;
    private int mDeltaYAddOn;

    private int mTopTouchRegionAdjustment = 0;
    private int mBottomTouchRegionAdjustment = 0;

    private int mXDown, mYDown;

    private final int mTouchSlop;
    private boolean mBodyPressed;
    private boolean mBodyDragStarted;
    private boolean mResizeActive;

    // DragLayer coordinates of the press which opened this frame. While that same gesture
    // is still in progress, moving past the touch slop from this origin takes the gesture
    // over (and starts a folder drag) instead of leaking it to the swipe-up/down
    // controllers (app drawer / notification shade).
    private int mGestureOriginX;
    private int mGestureOriginY;
    private boolean mGestureOriginValid;

    /** Run (posted) when the opening long-press ends without becoming a folder drag. */
    @Nullable
    private Runnable mOnGestureEnd;

    public FolderResizeFrame(Context context) {
        this(context, null);
    }

    public FolderResizeFrame(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FolderResizeFrame(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);

        mBackgroundPadding = getResources()
                .getDimensionPixelSize(R.dimen.resize_frame_background_padding);
        mTouchTargetWidth = 2 * mBackgroundPadding;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mFirstFrameAnimatorHelper = new FirstFrameAnimatorHelper(this);

        for (int i = 0; i < HANDLE_COUNT; i++) {
            mSystemGestureExclusionRects.add(new Rect());
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mDragHandles[INDEX_LEFT] = findViewById(R.id.widget_resize_left_handle);
        mDragHandles[INDEX_TOP] = findViewById(R.id.widget_resize_top_handle);
        mDragHandles[INDEX_RIGHT] = findViewById(R.id.widget_resize_right_handle);
        mDragHandles[INDEX_BOTTOM] = findViewById(R.id.widget_resize_bottom_handle);

        int workspaceAccentColor = ColorTokens.WorkspaceAccentColor.resolveColor(getContext());
        for (int i = 0; i < HANDLE_COUNT; i++) {
            ((ImageView) mDragHandles[i]).setColorFilter(workspaceAccentColor);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        for (int i = 0; i < HANDLE_COUNT; i++) {
            View dragHandle = mDragHandles[i];
            mSystemGestureExclusionRects.get(i).set(dragHandle.getLeft(), dragHandle.getTop(),
                    dragHandle.getRight(), dragHandle.getBottom());
        }
        if (!ATLEAST_Q) return;
        setSystemGestureExclusionRects(mSystemGestureExclusionRects);
    }

    public static boolean showForFolder(FolderIcon folderIcon) {
        return showForFolder(folderIcon, null);
    }

    /**
     * Shows the resize handles for {@code folderIcon}.
     *
     * @param onGestureEnd run (posted) when the long-press which opened this frame ends
     *                     without turning into a folder drag — the point at which a
     *                     context menu can safely be shown on top of the handles.
     */
    public static boolean showForFolder(FolderIcon folderIcon, @Nullable Runnable onGestureEnd) {
        Launcher launcher = Launcher.getLauncher(folderIcon.getContext());
        if (!(folderIcon.getTag() instanceof FolderInfo info)
                || info.container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            return false;
        }
        if (!(folderIcon.getParent() instanceof ViewGroup group)
                || !(group.getParent() instanceof CellLayout cellLayout)) {
            return false;
        }
        AbstractFloatingView.closeAllOpenViews(launcher);

        DragLayer dl = launcher.getDragLayer();
        FolderResizeFrame frame = (FolderResizeFrame) LayoutInflater
                .from(folderIcon.getContext())
                .inflate(R.layout.folder_resize_frame, dl, false);
        frame.setupForFolder(folderIcon, cellLayout);
        frame.mOnGestureEnd = onGestureEnd;
        // Remember where the opening press started (in DragLayer coordinates) so that the
        // gesture which triggered the long-press can continue straight into a folder drag.
        int[] loc = new int[2];
        dl.getLocationInDragLayer(folderIcon, loc);
        frame.setGestureOrigin(loc[0] + Math.round(folderIcon.getTouchDownX()),
                loc[1] + Math.round(folderIcon.getTouchDownY()));
        ((DragLayer.LayoutParams) frame.getLayoutParams()).customPosition = true;

        dl.addView(frame);
        frame.mIsOpen = true;
        frame.snapToFolder();
        return true;
    }

    public static boolean consumeJustClosed() {
        if (sJustClosedAt == 0) {
            return false;
        }
        if (SystemClock.uptimeMillis() - sJustClosedAt
                < ViewConfiguration.getLongPressTimeout() + 300) {
            sJustClosedAt = 0;
            return true;
        }
        return false;
    }

    public int getFloatingViewType() {
        return TYPE_FOLDER_RESIZE_FRAME;
    }

    private void setupForFolder(FolderIcon folderIcon, CellLayout cellLayout) {
        mFolderIcon = folderIcon;
        mCellLayout = cellLayout;
        mDragLayer = mLauncher.getDragLayer();
        mFolderInfo = (FolderInfo) folderIcon.getTag();

        InvariantDeviceProfile idp = LauncherAppState.getIDP(cellLayout.getContext());

        mMinHSpan = 1;
        mMinVSpan = 1;
        // Limit the workspace footprint (span) so the derived internal grid (long-side
        // 2n rule) stays within the configured span cap — the span is bounded up front
        // instead of truncating the grid after the fact. A folder whose span already
        // exceeds the cap (created before the limit existed) may keep it, it just
        // cannot grow.
        int maxSpan = LargeFolderBinder.getMaxSpan(mLauncher);
        mMaxHSpan = Math.min(idp.numColumns, maxSpan);
        mMaxVSpan = Math.min(idp.numRows, maxSpan);
        if (mFolderInfo != null) {
            mMaxHSpan = Math.max(mMaxHSpan, mFolderInfo.spanX);
            mMaxVSpan = Math.max(mMaxVSpan, mFolderInfo.spanY);
        }

        // Only show resize handles for the directions in which resizing is possible.
        mHorizontalResizeActive = mMinHSpan < mMaxHSpan;
        if (!mHorizontalResizeActive) {
            mDragHandles[INDEX_LEFT].setVisibility(GONE);
            mDragHandles[INDEX_RIGHT].setVisibility(GONE);
        }
        mVerticalResizeActive = mMinVSpan < mMaxVSpan;
        if (!mVerticalResizeActive) {
            mDragHandles[INDEX_TOP].setVisibility(GONE);
            mDragHandles[INDEX_BOTTOM].setVisibility(GONE);
        }

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) mFolderIcon.getLayoutParams();
        CellPos presenterPos = mLauncher.getCellPosMapper().mapModelToPresenter(mFolderInfo);
        lp.setCellX(presenterPos.cellX);
        lp.setTmpCellX(presenterPos.cellX);
        lp.setCellY(presenterPos.cellY);
        lp.setTmpCellY(presenterPos.cellY);
        lp.cellHSpan = mFolderInfo.spanX;
        lp.cellVSpan = mFolderInfo.spanY;
        lp.isLockedToGrid = true;

        // When we create the resize frame, we first mark all cells as unoccupied. The appropriate
        // cells (same if not resized, or different) will be marked as occupied when the resize
        // frame is dismissed.
        mCellLayout.markCellsAsUnoccupiedForView(mFolderIcon);

        setOnKeyListener(this);
    }

    /**
     * Records where the gesture which opened this frame started, in DragLayer coordinates.
     */
    public void setGestureOrigin(int x, int y) {
        mGestureOriginX = x;
        mGestureOriginY = y;
        mGestureOriginValid = true;
    }

    public boolean beginResizeIfPointInRegion(int x, int y) {
        mLeftBorderActive = (x < mTouchTargetWidth) && mHorizontalResizeActive;
        mRightBorderActive = (x > getWidth() - mTouchTargetWidth) && mHorizontalResizeActive;
        mTopBorderActive = (y < mTouchTargetWidth + mTopTouchRegionAdjustment)
                && mVerticalResizeActive;
        mBottomBorderActive = (y > getHeight() - mTouchTargetWidth + mBottomTouchRegionAdjustment)
                && mVerticalResizeActive;

        boolean anyBordersActive = mLeftBorderActive || mRightBorderActive
                || mTopBorderActive || mBottomBorderActive;

        if (anyBordersActive) {
            mDragHandles[INDEX_LEFT].setAlpha(mLeftBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_RIGHT].setAlpha(mRightBorderActive ? 1.0f :DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_TOP].setAlpha(mTopBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
            mDragHandles[INDEX_BOTTOM].setAlpha(mBottomBorderActive ? 1.0f : DIMMED_HANDLE_ALPHA);
        }

        if (mLeftBorderActive) {
            mDeltaXRange.set(-getLeft(), getWidth() - 2 * mTouchTargetWidth);
        } else if (mRightBorderActive) {
            mDeltaXRange.set(2 * mTouchTargetWidth - getWidth(), mDragLayer.getWidth() - getRight());
        } else {
            mDeltaXRange.set(0, 0);
        }
        mBaselineX.set(getLeft(), getRight());

        if (mTopBorderActive) {
            mDeltaYRange.set(-getTop(), getHeight() - 2 * mTouchTargetWidth);
        } else if (mBottomBorderActive) {
            mDeltaYRange.set(2 * mTouchTargetWidth - getHeight(), mDragLayer.getHeight() - getBottom());
        } else {
            mDeltaYRange.set(0, 0);
        }
        mBaselineY.set(getTop(), getBottom());

        return anyBordersActive;
    }

    /**
     *  Based on the deltas, we resize the frame.
     */
    public void visualizeResizeForDelta(int deltaX, int deltaY) {
        mDeltaX = mDeltaXRange.clamp(deltaX);
        mDeltaY = mDeltaYRange.clamp(deltaY);

        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        mBaselineX.applyDelta(mLeftBorderActive, mRightBorderActive, mDeltaX, mTempRange1);
        lp.x = mTempRange1.start;
        lp.width = mTempRange1.size();

        mBaselineY.applyDelta(mTopBorderActive, mBottomBorderActive, mDeltaY, mTempRange1);
        lp.y = mTempRange1.start;
        lp.height = mTempRange1.size();

        resizeFolderIfNeeded(false);

        requestLayout();
    }

    private static int getSpanIncrement(float deltaFrac) {
        return Math.abs(deltaFrac) > RESIZE_THRESHOLD ? Math.round(deltaFrac) : 0;
    }

    /**
     *  Based on the current deltas, we determine if and how to resize the folder.
     */
    private void resizeFolderIfNeeded(boolean onDismiss) {
        ViewGroup.LayoutParams wlp = mFolderIcon.getLayoutParams();
        if (!(wlp instanceof CellLayoutLayoutParams)) {
            return;
        }
        DeviceProfile dp = mLauncher.getDeviceProfile();
        float xThreshold = mCellLayout.getCellWidth() + dp.cellLayoutBorderSpacePx.x;
        float yThreshold = mCellLayout.getCellHeight() + dp.cellLayoutBorderSpacePx.y;

        int hSpanInc = getSpanIncrement((mDeltaX + mDeltaXAddOn) / xThreshold - mRunningHInc);
        int vSpanInc = getSpanIncrement((mDeltaY + mDeltaYAddOn) / yThreshold - mRunningVInc);

        if (!onDismiss && (hSpanInc == 0 && vSpanInc == 0)) return;

        mDirectionVector[0] = 0;
        mDirectionVector[1] = 0;

        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) wlp;

        int spanX = lp.cellHSpan;
        int spanY = lp.cellVSpan;
        int cellX = lp.useTmpCoords ? lp.getTmpCellX() : lp.getCellX();
        int cellY = lp.useTmpCoords ? lp.getTmpCellY() : lp.getCellY();

        // For each border, we bound the resizing based on the minimum width, and the maximum
        // expandability.
        mTempRange1.set(cellX, spanX + cellX);
        int hSpanDelta = mTempRange1.applyDeltaAndBound(mLeftBorderActive, mRightBorderActive,
                hSpanInc, mMinHSpan, mMaxHSpan, mCellLayout.getCountX(), mTempRange2);
        cellX = mTempRange2.start;
        spanX = mTempRange2.size();
        if (hSpanDelta != 0) {
            mDirectionVector[0] = mLeftBorderActive ? -1 : 1;
        }

        mTempRange1.set(cellY, spanY + cellY);
        int vSpanDelta = mTempRange1.applyDeltaAndBound(mTopBorderActive, mBottomBorderActive,
                vSpanInc, mMinVSpan, mMaxVSpan, mCellLayout.getCountY(), mTempRange2);
        cellY = mTempRange2.start;
        spanY = mTempRange2.size();
        if (vSpanDelta != 0) {
            mDirectionVector[1] = mTopBorderActive ? -1 : 1;
        }

        if (!onDismiss && vSpanDelta == 0 && hSpanDelta == 0) return;

        // We always want the final commit to match the feedback, so we make sure to use the
        // last used direction vector when committing the resize / reorder.
        if (onDismiss) {
            mDirectionVector[0] = mLastDirectionVector[0];
            mDirectionVector[1] = mLastDirectionVector[1];
        } else {
            mLastDirectionVector[0] = mDirectionVector[0];
            mLastDirectionVector[1] = mDirectionVector[1];
        }

        if (createAreaForResize(cellX, cellY, spanX, spanY, onDismiss)) {
            lp.setTmpCellX(cellX);
            lp.setTmpCellY(cellY);
            lp.cellHSpan = spanX;
            lp.cellVSpan = spanY;
            mRunningVInc += vSpanDelta;
            mRunningHInc += hSpanDelta;

            if (onDismiss) {
                commitFolderPlacement(lp);
            } else if (hSpanDelta != 0 || vSpanDelta != 0) {
                // Update the inline grid live while dragging the handles.
                mFolderIcon.onSpanChanged();
            }
        }
        mFolderIcon.requestLayout();
    }

    private boolean createAreaForResize(int cellX, int cellY, int spanX, int spanY,
            boolean commit) {
        ItemConfiguration swapSolution = findSwapSolution(mCellLayout, mFolderIcon,
                mDirectionVector, cellX, cellY, spanX, spanY);
        if (swapSolution.isSolution) {
            mCellLayout.performReorder(swapSolution, mFolderIcon,
                    commit ? CellLayout.MODE_ON_DROP : CellLayout.MODE_DRAG_OVER);
            return true;
        }
        return false;
    }

    private static ItemConfiguration findSwapSolution(CellLayout cellLayout, View dragView,
            int[] directionVector, int cellX, int cellY, int spanX, int spanY) {
        if (cellLayout.createReorderAlgorithm() instanceof MulticellReorderAlgorithm mc) {
            // Add seam to x position
            int seamCellX = cellX >= cellLayout.getCountX() / 2 ? cellX + 1 : cellX;
            return mc.removeSeamFromSolution(mc.simulateSeam(
                    () -> computeSwapSolution(cellLayout, dragView, directionVector,
                            seamCellX, cellY, spanX, spanY)));
        }
        return computeSwapSolution(cellLayout, dragView, directionVector,
                cellX, cellY, spanX, spanY);
    }

    private static ItemConfiguration computeSwapSolution(CellLayout cellLayout, View dragView,
            int[] directionVector, int cellX, int cellY, int spanX, int spanY) {
        int[] pixelXY = new int[2];
        cellLayout.regionToCenterPoint(cellX, cellY, spanX, spanY, pixelXY);

        ItemConfiguration configuration = new ItemConfiguration();
        cellLayout.copyCurrentStateToSolution(configuration);
        ReorderParameters parameters = new ReorderParameters(pixelXY[0], pixelXY[1], spanX, spanY,
                spanX, spanY, dragView, configuration);
        return cellLayout.createReorderAlgorithm().findReorderSolution(parameters,
                directionVector, true);
    }

    private void commitFolderPlacement(CellLayoutLayoutParams lp) {
        int prevSpanX = mFolderInfo.spanX;
        int prevSpanY = mFolderInfo.spanY;
        CellPos presenterPos = mLauncher.getCellPosMapper().mapModelToPresenter(mFolderInfo);
        boolean requiresDbUpdate = presenterPos.cellX != lp.getTmpCellX()
                || presenterPos.cellY != lp.getTmpCellY()
                || mFolderInfo.spanX != lp.cellHSpan
                || mFolderInfo.spanY != lp.cellVSpan;

        lp.setCellX(lp.getTmpCellX());
        lp.setCellY(lp.getTmpCellY());

        if (requiresDbUpdate) {
            mLauncher.getModelWriter().modifyItemInDatabase(mFolderInfo, mFolderInfo.container,
                    mFolderInfo.screenId, lp.getCellX(), lp.getCellY(), lp.cellHSpan,
                    lp.cellVSpan);
        }
        // Keep the in-memory model in sync immediately: updateInlineGrid takes the larger of
        // layout params and model, so this lets it drop back to the legacy preview as soon as
        // the folder shrinks back to 1x1 instead of waiting for an async model refresh.
        mFolderInfo.spanX = lp.cellHSpan;
        mFolderInfo.spanY = lp.cellVSpan;
        if (prevSpanX != lp.cellHSpan || prevSpanY != lp.cellVSpan) {
            mFolderIcon.onSpanChanged();
        }
    }

    /**
     * Resizes the folder to the given span in place, pushing neighbouring items aside with
     * the same reorder machinery the resize handles use. Returns false when the footprint
     * cannot be placed (nothing is changed in that case).
     */
    public static boolean resizeFolderTo(FolderIcon folderIcon, int targetSpanX,
            int targetSpanY) {
        Launcher launcher = Launcher.getLauncher(folderIcon.getContext());
        if (!(folderIcon.getTag() instanceof FolderInfo info)
                || info.container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            return false;
        }
        if (!(folderIcon.getParent() instanceof ViewGroup group)
                || !(group.getParent() instanceof CellLayout cellLayout)) {
            return false;
        }
        if (!(folderIcon.getLayoutParams() instanceof CellLayoutLayoutParams lp)) {
            return false;
        }

        InvariantDeviceProfile idp = LauncherAppState.getIDP(cellLayout.getContext());
        int maxSpan = LargeFolderBinder.getMaxSpan(launcher);
        int spanX = Utilities.boundToRange(targetSpanX, 1,
                Math.max(1, Math.min(idp.numColumns, maxSpan)));
        int spanY = Utilities.boundToRange(targetSpanY, 1,
                Math.max(1, Math.min(idp.numRows, maxSpan)));
        spanX = Math.min(spanX, cellLayout.getCountX());
        spanY = Math.min(spanY, cellLayout.getCountY());
        if (spanX == info.spanX && spanY == info.spanY) {
            return false;
        }

        CellPos presenterPos = launcher.getCellPosMapper().mapModelToPresenter(info);
        int prevCellX = lp.getCellX();
        int prevCellY = lp.getCellY();
        int prevSpanX = lp.cellHSpan;
        int prevSpanY = lp.cellVSpan;

        int cellX = Math.max(0,
                Math.min(presenterPos.cellX, cellLayout.getCountX() - spanX));
        int cellY = Math.max(0,
                Math.min(presenterPos.cellY, cellLayout.getCountY() - spanY));

        // Free the current footprint before moving it, exactly like setupForFolder() does,
        // so the algorithm is free to place the folder anywhere including where it stands.
        cellLayout.markCellsAsUnoccupiedForView(folderIcon);
        lp.setCellX(cellX);
        lp.setTmpCellX(cellX);
        lp.setCellY(cellY);
        lp.setTmpCellY(cellY);
        lp.cellHSpan = spanX;
        lp.cellVSpan = spanY;
        lp.isLockedToGrid = true;

        ItemConfiguration swapSolution = findSwapSolution(cellLayout, folderIcon,
                new int[2], cellX, cellY, spanX, spanY);
        if (!swapSolution.isSolution) {
            // Nothing moved; put the folder back exactly where it was.
            lp.setCellX(prevCellX);
            lp.setTmpCellX(prevCellX);
            lp.setCellY(prevCellY);
            lp.setTmpCellY(prevCellY);
            lp.cellHSpan = prevSpanX;
            lp.cellVSpan = prevSpanY;
            cellLayout.markCellsAsOccupiedForView(folderIcon);
            folderIcon.requestLayout();
            return false;
        }

        // Writes every displaced neighbour back to layout params and the model.
        cellLayout.performReorder(swapSolution, folderIcon, CellLayout.MODE_ON_DROP);

        lp.setCellX(lp.getTmpCellX());
        lp.setCellY(lp.getTmpCellY());
        if (presenterPos.cellX != lp.getCellX() || presenterPos.cellY != lp.getCellY()
                || info.spanX != lp.cellHSpan || info.spanY != lp.cellVSpan) {
            launcher.getModelWriter().modifyItemInDatabase(info, info.container,
                    info.screenId, lp.getCellX(), lp.getCellY(), lp.cellHSpan, lp.cellVSpan);
        }
        // Keep the in-memory model in sync immediately so updateInlineGrid() drops back to
        // the legacy preview as soon as a large folder shrinks to 1x1, without waiting for
        // an async model refresh.
        info.spanX = lp.cellHSpan;
        info.spanY = lp.cellVSpan;
        if (prevSpanX != lp.cellHSpan || prevSpanY != lp.cellVSpan) {
            folderIcon.onSpanChanged();
        }
        cellLayout.markCellsAsOccupiedForView(folderIcon);
        folderIcon.requestLayout();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // We are done with resizing the folder. Save the folder size & position to LauncherModel
        resizeFolderIfNeeded(true);
    }

    private void onTouchUp() {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        int xThreshold = mCellLayout.getCellWidth() + dp.cellLayoutBorderSpacePx.x;
        int yThreshold = mCellLayout.getCellHeight() + dp.cellLayoutBorderSpacePx.y;

        mDeltaXAddOn = mRunningHInc * xThreshold;
        mDeltaYAddOn = mRunningVInc * yThreshold;
        mDeltaX = 0;
        mDeltaY = 0;

        post(() -> snapToFolder(true));
    }

    /**
     * Returns the rect of this view when the frame is snapped around the folder, with the bounds
     * relative to the {@link DragLayer}.
     */
    private void getSnappedRectRelativeToDragLayer(@NonNull Rect out) {
        mDragLayer.getViewRectRelativeToSelf(mFolderIcon, out);

        int width = 2 * mBackgroundPadding + out.width();
        int height = 2 * mBackgroundPadding + out.height();
        int x = out.left - mBackgroundPadding;
        int y = out.top - mBackgroundPadding;

        out.left = x;
        out.top = y;
        out.right = out.left + width;
        out.bottom = out.top + height;
    }

    public void snapToFolder() {
        snapToFolder(false);
    }

    private void snapToFolder(boolean animate) {
        getSnappedRectRelativeToDragLayer(sTmpRect);
        int newWidth = sTmpRect.width();
        int newHeight = sTmpRect.height();
        int newX = sTmpRect.left;
        int newY = sTmpRect.top;

        // We need to make sure the frame's touchable regions lie fully within the bounds of the
        // DragLayer. We allow the actual handles to be clipped, but we shift the touch regions
        // down accordingly to provide a proper touch target.
        if (newY < 0) {
            // In this case we shift the touch region down to start at the top of the DragLayer
            mTopTouchRegionAdjustment = -newY;
        } else {
            mTopTouchRegionAdjustment = 0;
        }
        if (newY + newHeight > mDragLayer.getHeight()) {
            // In this case we shift the touch region up to end at the bottom of the DragLayer
            mBottomTouchRegionAdjustment = -(newY + newHeight - mDragLayer.getHeight());
        } else {
            mBottomTouchRegionAdjustment = 0;
        }

        final DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        if (!animate) {
            lp.width = newWidth;
            lp.height = newHeight;
            lp.x = newX;
            lp.y = newY;
            for (int i = 0; i < HANDLE_COUNT; i++) {
                mDragHandles[i].setAlpha(1f);
            }
            requestLayout();
        } else {
            ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(lp,
                    PropertyValuesHolder.ofInt(LAYOUT_WIDTH, lp.width, newWidth),
                    PropertyValuesHolder.ofInt(LAYOUT_HEIGHT, lp.height, newHeight),
                    PropertyValuesHolder.ofInt(LAYOUT_X, lp.x, newX),
                    PropertyValuesHolder.ofInt(LAYOUT_Y, lp.y, newY));
            mFirstFrameAnimatorHelper.addTo(oa).addUpdateListener(a -> requestLayout());

            AnimatorSet set = new AnimatorSet();
            set.play(oa);
            for (int i = 0; i < HANDLE_COUNT; i++) {
                set.play(mFirstFrameAnimatorHelper.addTo(
                        ObjectAnimator.ofFloat(mDragHandles[i], ALPHA, 1f)));
            }
            set.setDuration(SNAP_DURATION_MS);
            set.start();
        }

        setFocusableInTouchMode(true);
        requestFocus();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // Clear the frame and give focus to the folder icon when a directional key is pressed.
        if (shouldConsume(keyCode)) {
            close(false);
            mFolderIcon.requestFocus();
            return true;
        }
        return false;
    }

    private boolean handleTouchDown(MotionEvent ev) {
        Rect hitRect = new Rect();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        getHitRect(hitRect);
        if (hitRect.contains(x, y)) {
            if (beginResizeIfPointInRegion(x - getLeft(), y - getTop())) {
                if (mXDown == 0 && mYDown == 0) {
                    performHapticFeedback(CLOCK_TICK);
                }
                mXDown = x;
                mYDown = y;
                mResizeActive = true;
                return true;
            }
        }
        mResizeActive = false;
        return false;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(ev);
            case MotionEvent.ACTION_MOVE:
                if (mBodyPressed) {
                    // Claim a body press only after the finger moves beyond the touch slop:
                    // start dragging the folder itself instead of leaking the gesture to the
                    // workspace swipe handlers (app drawer / notification shade).
                    if (!mBodyDragStarted
                            && Math.hypot(x - mXDown, y - mYDown) > mTouchSlop) {
                        mBodyDragStarted = true;
                        close(false);
                        ItemLongClickListener.beginDrag(mFolderIcon, mLauncher, mFolderInfo,
                                new DragOptions());
                    }
                    if (mBodyDragStarted) {
                        // This frame owns the DragLayer's active controller slot for the whole
                        // gesture, so the DragController never receives these events through the
                        // normal intercept path. Forward them so the drag follows the finger
                        // instead of freezing until the next gesture.
                        mLauncher.getDragController().onControllerTouchEvent(ev);
                    }
                    break;
                }
                if (mResizeActive) {
                    visualizeResizeForDelta(x - mXDown, y - mYDown);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mBodyPressed) {
                    if (mBodyDragStarted) {
                        // Deliver the release to the DragController so the drag drops
                        // (or cancels) properly; it owns the gesture now.
                        mLauncher.getDragController().onControllerTouchEvent(ev);
                    } else {
                        // Tap on the folder body: just dismiss the frame. Opening the
                        // folder is reserved for the "+N" overflow cell. Record the close
                        // so an immediate long-press on the same gesture doesn't reopen it.
                        sJustClosedAt = SystemClock.uptimeMillis();
                        close(false);
                    }
                    mBodyPressed = false;
                    mBodyDragStarted = false;
                    mXDown = mYDown = 0;
                    break;
                }
                if (mResizeActive) {
                    visualizeResizeForDelta(x - mXDown, y - mYDown);
                    onTouchUp();
                    mResizeActive = false;
                }
                mXDown = mYDown = 0;
                break;
        }
        return true;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            // A new gesture starts on the frame itself; the origin recorded for the
            // gesture which opened it no longer applies.
            mGestureOriginValid = false;
            if (handleTouchDown(ev)) {
                return true;
            }
            // Body press: take over the gesture instead of closing and letting it fall
            // through to the folder icon / workspace (which caused stray swipe-up/swipe-
            // down actions).
            mBodyPressed = true;
            mBodyDragStarted = false;
            mResizeActive = false;
            mXDown = (int) ev.getX();
            mYDown = (int) ev.getY();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && mGestureOriginValid) {
            // The gesture which opened this frame is still in progress (the frame was
            // created after its ACTION_DOWN). Once the finger moves past the touch slop
            // from its origin — while still over the frame — take the gesture over so the
            // same press-and-drag becomes a folder drag instead of leaking to the swipe
            // up / swipe down controllers. Sub-slop moves are left alone so a long-press
            // which lifts without dragging keeps the frame open.
            getHitRect(sTmpRect);
            int x = (int) ev.getX();
            int y = (int) ev.getY();
            if (sTmpRect.contains(x, y)
                    && Math.hypot(x - mGestureOriginX, y - mGestureOriginY) > mTouchSlop) {
                mGestureOriginValid = false;
                mBodyPressed = true;
                mBodyDragStarted = false;
                mResizeActive = false;
                mXDown = mGestureOriginX;
                mYDown = mGestureOriginY;
                return true;
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            // The opening gesture ended without taking it over; it must not leak into a
            // later gesture. On a plain release this is also the first moment the context
            // menu can be layered on top of the handles without swallowing the drag.
            boolean openingGesture = mGestureOriginValid;
            mGestureOriginValid = false;
            if (openingGesture && action == MotionEvent.ACTION_UP && mOnGestureEnd != null) {
                post(mOnGestureEnd);
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        MotionEvent dragLayerEvent = MotionEvent.obtain(ev);
        dragLayerEvent.offsetLocation(getLeft(), getTop());
        try {
            if (ev.getAction() == MotionEvent.ACTION_DOWN
                    && !handleTouchDown(dragLayerEvent)) {
                // Body press consumed directly from the frame when the DragLayer did not
                // claim us through intercept. Never let it fall through to the workspace
                // (stray swipe-up/down actions); later moves come back here and are forwarded
                // to the DragController once a drag starts.
                mGestureOriginValid = false;
                mBodyPressed = true;
                mBodyDragStarted = false;
                mResizeActive = false;
                mXDown = (int) dragLayerEvent.getX();
                mYDown = (int) dragLayerEvent.getY();
                return true;
            }
            return onControllerTouchEvent(dragLayerEvent);
        } finally {
            dragLayerEvent.recycle();
        }
    }

    @Override
    protected void handleClose(boolean animate) {
        mDragLayer.removeView(this);
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_FOLDER_RESIZE_FRAME) != 0;
    }

    /**
     * Returns true only if this utility class handles the key code.
     */
    public static boolean shouldConsume(int keyCode) {
        return (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_MOVE_HOME || keyCode == KeyEvent.KEYCODE_MOVE_END
                || keyCode == KeyEvent.KEYCODE_PAGE_UP || keyCode == KeyEvent.KEYCODE_PAGE_DOWN);
    }

    /**
     * A mutable class for describing the range of two int values.
     */
    private static class IntRange {

        public int start, end;

        public int clamp(int value) {
            return Utilities.boundToRange(value, start, end);
        }

        public void set(int s, int e) {
            start = s;
            end = e;
        }

        public int size() {
            return end - start;
        }

        /**
         * Moves either the start or end edge (but never both) by {@param delta} and  sets the
         * result in {@param out}
         */
        public void applyDelta(boolean moveStart, boolean moveEnd, int delta, IntRange out) {
            out.start = moveStart ? start + delta : start;
            out.end = moveEnd ? end + delta : end;
        }

        /**
         * Applies delta similar to {@link #applyDelta(boolean, boolean, int, IntRange)},
         * with extra conditions.
         * @param minSize minimum size after with the moving edge should not be shifted any further.
         *                For eg, if delta = -3 when moving the endEdge brings the size to less than
         *                minSize, only delta = -2 will applied
         * @param maxSize maximum size after with the moving edge should not be shifted any further.
         *                For eg, if delta = -3 when moving the endEdge brings the size to greater
         *                than maxSize, only delta = -2 will applied
         * @param maxEnd The maximum value to the end edge (start edge is always restricted to 0)
         * @return the amount of increase when endEdge was moves and the amount of decrease when
         * the start edge was moved.
         */
        public int applyDeltaAndBound(boolean moveStart, boolean moveEnd, int delta,
                int minSize, int maxSize, int maxEnd, IntRange out) {
            applyDelta(moveStart, moveEnd, delta, out);
            if (out.start < 0) {
                out.start = 0;
            }
            if (out.end > maxEnd) {
                out.end = maxEnd;
            }
            if (out.size() < minSize) {
                if (moveStart) {
                    out.start = out.end - minSize;
                } else if (moveEnd) {
                    out.end = out.start + minSize;
                }
            }
            if (out.size() > maxSize) {
                if (moveStart) {
                    out.start = out.end - maxSize;
                } else if (moveEnd) {
                    out.end = out.start + maxSize;
                }
            }
            return moveEnd ? out.size() - size() : size() - out.size();
        }
    }
}
