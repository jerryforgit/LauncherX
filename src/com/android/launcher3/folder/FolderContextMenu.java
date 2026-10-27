package com.android.launcher3.folder;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.IGNORE;

import android.content.Context;
import android.graphics.RectF;

import androidx.core.content.ContextCompat;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.views.OptionsPopupView.OptionItem;

import com.jerry.launcherx.modern.folder.LargeFolderBinder;
import com.jerry.launcherx.modern.folder.SwiperMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Long-press menu for a desktop folder.
 *
 * <p>Shown on top of {@link FolderResizeFrame}, which the same long-press opens, so the span
 * handles stay visible while the menu is up. The menu is added last and is therefore the
 * floating view consulted for touch, which is what lets its items be tapped; dismissing it
 * with an outside tap leaves the handles open for the next gesture.
 */
public final class FolderContextMenu {

    private FolderContextMenu() { }

    /**
     * Inflates and shows the menu for {@code folderIcon}. Does nothing for folders which are
     * not on the desktop (hotseat / app drawer), matching {@link FolderResizeFrame}.
     */
    public static void showForFolder(FolderIcon folderIcon) {
        if (!(folderIcon.getTag() instanceof FolderInfo info)
                || info.container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
            return;
        }
        Launcher launcher = Launcher.getLauncher(folderIcon.getContext());
        Context context = folderIcon.getContext();
        boolean isLarge = info.spanX > 1 || info.spanY > 1;

        List<OptionItem> items = new ArrayList<>(2);
        items.add(new OptionItem(
                context.getText(R.string.folder_menu_rename),
                ContextCompat.getDrawable(context, R.drawable.gm_edit_24),
                IGNORE,
                v -> {
                    AbstractFloatingView.closeAllOpenViews(launcher, false);
                    folderIcon.showRenameDialog();
                    return true;
                }));
        items.add(new OptionItem(
                context.getText(isLarge
                        ? R.string.folder_menu_switch_to_small
                        : R.string.folder_menu_switch_to_large),
                ContextCompat.getDrawable(context, R.drawable.ic_aspect_ratio),
                IGNORE,
                v -> {
                    AbstractFloatingView.closeAllOpenViews(launcher, false);
                    if (isLarge) {
                        FolderResizeFrame.resizeFolderTo(folderIcon, 1, 1);
                    } else {
                        switchToLarge(folderIcon);
                    }
                    return true;
                }));

        if (isLarge) {
            addSwiperOptions(context, launcher, folderIcon, info, items);
        }

        OptionsPopupView.show(launcher, targetRect(folderIcon), items, true);
    }

    /**
     * Per-folder swiper overrides. Choosing a mode writes only this folder's preference, so
     * the card stops following the global Layout → Swiper setting until it is reset.
     */
    private static void addSwiperOptions(
            Context context,
            Launcher launcher,
            FolderIcon folderIcon,
            FolderInfo info,
            List<OptionItem> items) {
        SwiperMode current = LargeFolderBinder.getSwiperMode(context, info.id);
        if (current != SwiperMode.HORIZONTAL) {
            items.add(new OptionItem(
                    context.getText(R.string.folder_menu_swiper_horizontal),
                    ContextCompat.getDrawable(context, R.drawable.ic_split_horizontal),
                    IGNORE,
                    v -> {
                        AbstractFloatingView.closeAllOpenViews(launcher, false);
                        LargeFolderBinder.setSwiperMode(
                                context, info.id, SwiperMode.HORIZONTAL);
                        folderIcon.onSpanChanged();
                        return true;
                    }));
        }
        if (current != SwiperMode.VERTICAL) {
            items.add(new OptionItem(
                    context.getText(R.string.folder_menu_swiper_vertical),
                    ContextCompat.getDrawable(context, R.drawable.ic_split_vertical),
                    IGNORE,
                    v -> {
                        AbstractFloatingView.closeAllOpenViews(launcher, false);
                        LargeFolderBinder.setSwiperMode(
                                context, info.id, SwiperMode.VERTICAL);
                        folderIcon.onSpanChanged();
                        return true;
                    }));
        }
        if (current != SwiperMode.OFF) {
            items.add(new OptionItem(
                    context.getText(R.string.folder_menu_swiper_off),
                    ContextCompat.getDrawable(context, R.drawable.ic_close),
                    IGNORE,
                    v -> {
                        AbstractFloatingView.closeAllOpenViews(launcher, false);
                        LargeFolderBinder.setSwiperMode(context, info.id, SwiperMode.OFF);
                        folderIcon.onSpanChanged();
                        return true;
                    }));
        }
        if (LargeFolderBinder.hasSwiperModeOverride(context, info.id)) {
            items.add(new OptionItem(
                    context.getText(R.string.folder_menu_swiper_follow_global),
                    ContextCompat.getDrawable(context, R.drawable.ic_gestures),
                    IGNORE,
                    v -> {
                        AbstractFloatingView.closeAllOpenViews(launcher, false);
                        LargeFolderBinder.clearSwiperModeOverride(context, info.id);
                        folderIcon.onSpanChanged();
                        return true;
                    }));
        }
    }

    /** Target span for "switch to large folder". Fixed, so the gesture is predictable. */
    private static final int LARGE_SPAN = 2;

    /**
     * Grows a small folder into a large one. Always targets {@link #LARGE_SPAN}; if that
     * footprint is blocked the folder simply stays small rather than silently becoming a
     * different size than the one the menu promised.
     */
    private static boolean switchToLarge(FolderIcon folderIcon) {
        return FolderResizeFrame.resizeFolderTo(folderIcon, LARGE_SPAN, LARGE_SPAN);
    }

    /**
     * Small square target centred on the folder, in DragLayer coordinates — the same shape
     * the empty-space long-press menu uses, so ArrowPopup places and arrows the same way
     * regardless of how large the folder's own bounds are.
     */
    private static RectF targetRect(FolderIcon folderIcon) {
        Launcher launcher = Launcher.getLauncher(folderIcon.getContext());
        int[] loc = new int[2];
        launcher.getDragLayer().getLocationInDragLayer(folderIcon, loc);
        int halfSize = folderIcon.getResources()
                .getDimensionPixelSize(R.dimen.options_menu_thumb_size) / 2;
        int cx = loc[0] + folderIcon.getWidth() / 2;
        int cy = loc[1] + folderIcon.getHeight() / 2;
        return new RectF(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize);
    }
}
