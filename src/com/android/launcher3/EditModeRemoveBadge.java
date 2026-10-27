/*
 * Copyright (C) 2026 The Lawnchair Project
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
package com.android.launcher3;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.Nullable;

import com.jerry.launcherx.preferences2.PreferenceCacheExtensionsKt;
import com.jerry.launcherx.preferences2.PreferenceManager2;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * LauncherX: shared state, drawing and hit-testing for the edit-mode "remove" badge that OEM
 * launchers render on home-screen icons (and folder icons) while the launcher is being edited.
 *
 * <p>Keeping the logic here lets both {@link BubbleTextView} (regular apps and folder contents)
 * and {@link com.android.launcher3.folder.FolderIcon} (which is a {@code FrameLayout}) render a
 * consistent badge without duplicating the paint code.
 */
public final class EditModeRemoveBadge {

    private static final float SIZE_FRACTION = 0.36f;

    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RING_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint GLYPH_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static boolean sEnabled = true;

    private EditModeRemoveBadge() {
    }

    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
    }

    public static boolean isEnabled() {
        return sEnabled;
    }

    /** Whether a badge should currently be shown for the given display and activity context. */
    public static boolean shouldShow(@Nullable ActivityContext activity, int display) {
        if (!sEnabled || display != BubbleTextView.DISPLAY_WORKSPACE
                && display != BubbleTextView.DISPLAY_FOLDER) {
            return false;
        }
        if (!(activity instanceof Launcher launcher) || !(launcher instanceof Context context)) {
            return false;
        }
        if (!launcher.isInState(LauncherState.EDIT_MODE)) {
            return false;
        }
        return !PreferenceCacheExtensionsKt.firstCached(
                PreferenceManager2.getInstance(context).getLockHomeScreen());
    }

    /** Computes the badge bounds (view-local) from the icon bounds, writing into {@code out}. */
    public static void computeBounds(Rect iconBounds, RectF out, float density) {
        float diameter = Math.max(iconBounds.width() * SIZE_FRACTION, density * 16f);
        float radius = diameter / 2f;
        float centerX = iconBounds.left + radius;
        float centerY = iconBounds.top + radius;
        out.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }

    /** Draws the badge filled at the given bounds. */
    public static void draw(Canvas canvas, RectF bounds) {
        final float centerX = bounds.centerX();
        final float centerY = bounds.centerY();
        final float radius = bounds.width() / 2f + RING_PAINT.getStrokeWidth();

        FILL_PAINT.setStyle(Paint.Style.FILL);
        FILL_PAINT.setColor(0xFFFFFFFF);
        canvas.drawCircle(centerX, centerY, radius, FILL_PAINT);

        RING_PAINT.setStyle(Paint.Style.STROKE);
        RING_PAINT.setStrokeWidth(Math.max(1f, radius * 0.08f));
        RING_PAINT.setColor(0x26000000);
        canvas.drawCircle(centerX, centerY, radius, RING_PAINT);

        GLYPH_PAINT.setStyle(Paint.Style.STROKE);
        GLYPH_PAINT.setStrokeCap(Paint.Cap.ROUND);
        GLYPH_PAINT.setStrokeWidth(Math.max(1.5f, radius * 0.26f));
        GLYPH_PAINT.setColor(0xFF5F6368);
        final float glyphHalf = radius * 0.42f;
        canvas.drawLine(centerX - glyphHalf, centerY, centerX + glyphHalf, centerY, GLYPH_PAINT);
    }

    /** Removes the given item, matching the pop-up "Remove" action (with undo). */
    public static void removeItem(ActivityContext activity, ItemInfo item, View view) {
        DropTargetHandler dropTargetHandler = activity.getDropTargetHandler();
        dropTargetHandler.prepareToUndoDelete();
        dropTargetHandler.onDeleteComplete(item, view);
    }
}
