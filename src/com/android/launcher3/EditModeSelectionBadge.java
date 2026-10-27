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
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.Nullable;

import com.jerry.launcherx.preferences2.PreferenceCacheExtensionsKt;
import com.jerry.launcherx.preferences2.PreferenceManager2;
import com.jerry.launcherx.views.EditModeSelection;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * LauncherX: shared state and drawing for the edit-mode multi-select check mark.
 *
 * <p>When multi-select is enabled, every selectable icon renders this badge for the whole time
 * edit mode is active: an empty circle while the item is unselected (the "to be selected" box)
 * and a filled accent circle with a check mark once it is selected. The remove badge is suppressed
 * whenever this badge is shown so the two overlays never overlap.
 */
public final class EditModeSelectionBadge {

    private static final float SIZE_FRACTION = 0.36f;

    private static final int ACCENT_COLOR = 0xFF1A73E8;
    private static final int RING_COLOR = 0xFFFFFFFF;

    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RING_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint GLYPH_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path GLYPH_PATH = new Path();

    private EditModeSelectionBadge() {
    }

    /** Whether any item is currently selected for the given activity context. */
    public static boolean isSelectionActive(@Nullable ActivityContext activity) {
        if (!(activity instanceof Launcher launcher)) {
            return false;
        }
        if (!launcher.isInState(LauncherState.EDIT_MODE)) {
            return false;
        }
        return launcher.getEditModeSelection().isActive();
    }

    /** Whether the given item is part of the current selection. */
    public static boolean isSelected(@Nullable ActivityContext activity, ItemInfo item) {
        if (!(activity instanceof Launcher launcher)) {
            return false;
        }
        EditModeSelection selection = launcher.getEditModeSelection();
        return selection.isActive() && selection.isSelected(item);
    }

    /**
     * Whether the edit-mode selection affordance should be drawn for the given display.
     *
     * <p>It shows for every selectable icon as soon as edit mode is entered (not only once a
     * selection exists), so each icon reads as a "to be selected" checkbox: an empty box when
     * unselected and a filled check when selected.
     */
    public static boolean shouldShow(@Nullable ActivityContext activity, int display) {
        if (display != BubbleTextView.DISPLAY_WORKSPACE
                && display != BubbleTextView.DISPLAY_FOLDER) {
            return false;
        }
        if (!(activity instanceof Launcher launcher)) {
            return false;
        }
        if (!launcher.isInState(LauncherState.EDIT_MODE)) {
            return false;
        }
        return isMultiSelectEnabled(launcher);
    }

    /** Whether the icon checkbox replaces the remove badge (i.e. multi-select is on). */
    private static boolean isMultiSelectEnabled(ActivityContext activity) {
        if (!(activity instanceof Context context)) {
            return false;
        }
        return PreferenceCacheExtensionsKt.firstCached(
                PreferenceManager2.getInstance(context).getEditModeMultiSelect());
    }

    /** Computes the badge bounds (view-local) from the icon bounds, writing into {@code out}. */
    public static void computeBounds(Rect iconBounds, RectF out, float density) {
        float diameter = Math.max(iconBounds.width() * SIZE_FRACTION, density * 16f);
        float radius = diameter / 2f;
        float centerX = iconBounds.left + radius;
        float centerY = iconBounds.top + radius;
        out.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
    }

    /** Draws the selection badge at the given bounds. */
    public static void draw(Canvas canvas, RectF bounds) {
        final float centerX = bounds.centerX();
        final float centerY = bounds.centerY();
        final float radius = bounds.width() / 2f;

        RING_PAINT.setStyle(Paint.Style.STROKE);
        RING_PAINT.setStrokeWidth(Math.max(1.5f, radius * 0.18f));
        RING_PAINT.setColor(RING_COLOR);
        canvas.drawCircle(centerX, centerY, radius + RING_PAINT.getStrokeWidth() / 2f, RING_PAINT);

        FILL_PAINT.setStyle(Paint.Style.FILL);
        FILL_PAINT.setColor(ACCENT_COLOR);
        canvas.drawCircle(centerX, centerY, radius, FILL_PAINT);

        GLYPH_PAINT.setStyle(Paint.Style.STROKE);
        GLYPH_PAINT.setStrokeCap(Paint.Cap.ROUND);
        GLYPH_PAINT.setStrokeJoin(Paint.Join.ROUND);
        GLYPH_PAINT.setStrokeWidth(Math.max(1.5f, radius * 0.28f));
        GLYPH_PAINT.setColor(0xFFFFFFFF);

        GLYPH_PATH.reset();
        GLYPH_PATH.moveTo(centerX - radius * 0.42f, centerY + radius * 0.02f);
        GLYPH_PATH.lineTo(centerX - radius * 0.12f, centerY + radius * 0.32f);
        GLYPH_PATH.lineTo(centerX + radius * 0.44f, centerY - radius * 0.30f);
        canvas.drawPath(GLYPH_PATH, GLYPH_PAINT);
    }

    /**
     * Draws the empty "to be selected" checkbox at the given bounds: a filled white circle with a
     * faint ring, mirroring the remove badge's silhouette but without the minus glyph. Using the
     * same silhouette keeps it legible on both light and dark icons.
     */
    public static void drawUnselected(Canvas canvas, RectF bounds) {
        final float centerX = bounds.centerX();
        final float centerY = bounds.centerY();
        final float radius = bounds.width() / 2f;

        FILL_PAINT.setStyle(Paint.Style.FILL);
        FILL_PAINT.setColor(RING_COLOR);
        canvas.drawCircle(centerX, centerY, radius, FILL_PAINT);

        RING_PAINT.setStyle(Paint.Style.STROKE);
        RING_PAINT.setStrokeWidth(Math.max(1f, radius * 0.08f));
        RING_PAINT.setColor(0x33000000);
        canvas.drawCircle(centerX, centerY, radius, RING_PAINT);
    }
}
