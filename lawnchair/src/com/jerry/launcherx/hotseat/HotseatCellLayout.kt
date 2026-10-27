/*
 * Copyright (C) 2026 The Android Open Source Project
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
 * Modifications copyright 2026 Lawnchair
 */
package com.jerry.launcherx.hotseat

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.android.launcher3.CellLayout
import com.android.launcher3.celllayout.CellLayoutLayoutParams

/**
 * Dock page layout that keeps the pinned icons packed into the first slots.
 *
 * Launcher3 stores a hotseat item's position as a flat rank in `favorites.screen`, so removing
 * one item leaves a hole at that rank and the remaining items keep their old cells. iOS-style
 * docks instead always keep the icons packed to the start of the row: removing an item shifts
 * the following icons left, and an empty slot only exists after the last item.
 *
 * This layout compacts the children by visual order before measuring/laying out, without
 * touching the database. The binding ranks stay valid (the loader still validates them); the
 * next drag/reorder inside the dock persists the compacted positions naturally.
 *
 * When an item is added or removed after the dock is already on screen, the affected children
 * are animated from their old cell position to the compacted one (translationX/translationY),
 * which gives the iOS-style "remaining icons slide over" effect.
 */
class HotseatCellLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : CellLayout(context, attrs, defStyle) {

    private data class Shift(val child: View, val dx: Int, val dy: Int)

    private val pendingShifts = ArrayList<Shift>(4)
    private var lastChildCount = 0
    private var hasLaidOut = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        compactSlots()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        startPendingShifts()
        hasLaidOut = true
    }

    private fun startPendingShifts() {
        if (pendingShifts.isEmpty()) return
        val container = shortcutsAndWidgets
        for (shift in pendingShifts) {
            if (shift.child.parent !== container) continue
            shift.child.animate().cancel()
            shift.child.translationX = shift.dx.toFloat()
            shift.child.translationY = shift.dy.toFloat()
            shift.child.animate()
                .translationX(0f)
                .translationY(0f)
                .setDuration(SHIFT_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        pendingShifts.clear()
    }

    /**
     * Reassigns the cell of every child so the items occupy slots `0..n-1` in visual order.
     *
     * Positions are read from the children themselves (not from the model), which also makes
     * this work after a reorder has moved items via temporary cells.
     */
    private fun compactSlots() {
        val container = shortcutsAndWidgets
        val count = container.childCount
        if (count == 0) {
            pendingShifts.clear()
            lastChildCount = 0
            return
        }

        val countX = countX
        val countY = countY
        if (countX <= 0 || countY <= 0) return

        val entries = ArrayList<Triple<CellLayoutLayoutParams, Int, View>>(count)
        for (i in 0 until count) {
            val child = container.getChildAt(i)
            val lp = child.layoutParams as? CellLayoutLayoutParams ?: return
            // A reorder preview currently has temporary coordinates that differ from the
            // committed cells; leave the layout alone until the reorder settles.
            if (lp.useTmpCoords &&
                (lp.tmpCellX != lp.cellX || lp.tmpCellY != lp.cellY)
            ) {
                return
            }
            entries.add(Triple(lp, i, child))
        }

        // In a vertical dock rank 0 is the bottom-most icon, so compact towards the bottom.
        val vertical = countX == 1 && countY > 1
        if (vertical) {
            entries.sortWith(
                compareBy({ -it.first.cellY }, { it.first.cellX }, { it.second }),
            )
        } else {
            entries.sortWith(
                compareBy({ it.first.cellY }, { it.first.cellX }, { it.second }),
            )
        }

        val placements = ArrayList<Triple<CellLayoutLayoutParams, IntArray, View>>(count)
        var cursor = 0
        for ((lp, _, child) in entries) {
            val span = (if (vertical) lp.cellVSpan else lp.cellHSpan).coerceAtLeast(1)
            val x: Int
            val y: Int
            if (vertical) {
                x = 0
                y = countY - cursor - span
                cursor += span
            } else {
                if (cursor % countX + span > countX) {
                    cursor = ((cursor / countX) + 1) * countX
                }
                x = cursor % countX
                y = cursor / countX
                cursor += span
            }
            if (x < 0 || y < 0 || x + lp.cellHSpan > countX || y + lp.cellVSpan > countY) {
                // Not enough room for this span in the remaining slots; leave the layout alone.
                return
            }
            placements.add(Triple(lp, intArrayOf(x, y), child))
        }

        // Only animate when the number of dock items changed (add/remove) after the dock has
        // already been shown. Initial binding and page recreation stay static.
        val animateShifts = hasLaidOut && lastChildCount > 0 && count != lastChildCount
        if (animateShifts) {
            // A later measure pass can run before the layout pass. Keep the shifts queued for
            // that layout instead of clearing them on every no-op compaction.
            pendingShifts.clear()
            val stepX = cellWidth + mBorderSpace.x
            val stepY = cellHeight + mBorderSpace.y
            for ((lp, cell, child) in placements) {
                val dx = (lp.cellX - cell[0]) * stepX
                val dy = (lp.cellY - cell[1]) * stepY
                if (dx != 0 || dy != 0) {
                    pendingShifts.add(Shift(child, dx, dy))
                }
            }
        }

        val occupied = mOccupied
        occupied.clear()
        for ((lp, cell, _) in placements) {
            lp.cellX = cell[0]
            lp.cellY = cell[1]
            // Keep temporary coordinates in sync: after an external drop Launcher3 can leave
            // useTmpCoords set on the existing children, and ShortcutAndWidgetContainer would
            // otherwise lay them out using the stale temporary cells.
            lp.tmpCellX = cell[0]
            lp.tmpCellY = cell[1]
            lp.useTmpCoords = false
            occupied.markCells(cell[0], cell[1], lp.cellHSpan, lp.cellVSpan, true)
        }
        lastChildCount = count
    }

    private companion object {
        const val SHIFT_DURATION_MS = 220L
    }
}
