/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jerry.launcherx.modern.folder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFolderGridTest {

    // --- gridForSpan: the long-side 2n / short-side 2n-1 rule ----------------

    @Test
    fun portrait_twoByTwo_yieldsThreeByFour() {
        // Portrait: horizontal is the short side (2n-1), vertical the long side (2n).
        val size = LargeFolderGrid.gridForSpan(spanX = 2, spanY = 2, landscape = false)
        assertEquals(3, size.columns)
        assertEquals(4, size.rows)
        assertEquals(12, size.capacity)
    }

    @Test
    fun landscape_twoByTwo_yieldsFourByThree() {
        val size = LargeFolderGrid.gridForSpan(spanX = 2, spanY = 2, landscape = true)
        assertEquals(4, size.columns)
        assertEquals(3, size.rows)
        assertEquals(12, size.capacity)
    }

    @Test
    fun portrait_twoByOne_yieldsThreeByTwo() {
        val size = LargeFolderGrid.gridForSpan(spanX = 2, spanY = 1, landscape = false)
        assertEquals(3, size.columns)
        assertEquals(2, size.rows)
        assertEquals(6, size.capacity)
    }

    @Test
    fun landscape_twoByOne_yieldsFourByOne() {
        val size = LargeFolderGrid.gridForSpan(spanX = 2, spanY = 1, landscape = true)
        assertEquals(4, size.columns)
        assertEquals(1, size.rows)
        assertEquals(4, size.capacity)
    }

    @Test
    fun portrait_oneByTwo_yieldsOneByFour() {
        val size = LargeFolderGrid.gridForSpan(spanX = 1, spanY = 2, landscape = false)
        assertEquals(1, size.columns)
        assertEquals(4, size.rows)
        assertEquals(4, size.capacity)
    }

    @Test
    fun landscape_oneByTwo_yieldsTwoByThree() {
        val size = LargeFolderGrid.gridForSpan(spanX = 1, spanY = 2, landscape = true)
        assertEquals(2, size.columns)
        assertEquals(3, size.rows)
        assertEquals(6, size.capacity)
    }

    @Test
    fun oneByOne_followsTheSameAxisRuleButNeverRendersInline() {
        // 1x1 never renders inline (the inline gate requires span > 1), but the pure
        // mapping still has to follow the orientation rule.
        assertEquals(1, LargeFolderGrid.gridForSpan(1, 1, landscape = false).columns)
        assertEquals(2, LargeFolderGrid.gridForSpan(1, 1, landscape = false).rows)
        assertEquals(2, LargeFolderGrid.gridForSpan(1, 1, landscape = true).columns)
        assertEquals(1, LargeFolderGrid.gridForSpan(1, 1, landscape = true).rows)
    }

    @Test
    fun rotating_flipsWhichAxisGetsTwoN() {
        // Same span, rotated screen: the 2n side moves from vertical to horizontal.
        val portrait = LargeFolderGrid.gridForSpan(3, 2, landscape = false)
        val landscape = LargeFolderGrid.gridForSpan(3, 2, landscape = true)
        assertEquals(5, portrait.columns)
        assertEquals(4, portrait.rows)
        assertEquals(6, landscape.columns)
        assertEquals(3, landscape.rows)
    }

    @Test
    fun largeSpans_scaleLinearly() {
        val size = LargeFolderGrid.gridForSpan(4, 4, landscape = true)
        assertEquals(8, size.columns)
        assertEquals(7, size.rows)
        assertEquals(56, size.capacity)
    }

    // --- clampMaxSpan: the span cap offered in settings ----------------------

    @Test
    fun clampMaxSpan_boundsToTheSettingsRange() {
        assertEquals(2, LargeFolderGrid.clampMaxSpan(1))
        assertEquals(3, LargeFolderGrid.clampMaxSpan(3))
        assertEquals(4, LargeFolderGrid.clampMaxSpan(9))
    }

    @Test
    fun maxSpan_neverProducesNegativeGridAxes() {
        for (span in LargeFolderGrid.MIN_MAX_SPAN..LargeFolderGrid.MAX_MAX_SPAN) {
            for (landscape in listOf(true, false)) {
                val size = LargeFolderGrid.gridForSpan(span, span, landscape)
                assertTrue(size.columns >= 1)
                assertTrue(size.rows >= 1)
            }
        }
    }

    // --- planOverflow: the 2x2 mini grid in the last cell --------------------

    @Test
    fun itemsWithinCapacity_showEverything() {
        val plan = LargeFolderGrid.planOverflow(total = 12, capacity = 12)
        assertEquals(12, plan.normalCount)
        assertFalse(plan.hasOverflow)
        assertEquals(0, plan.hiddenCount)
    }

    @Test
    fun overflowCellTakesTheLastSlot() {
        // 13 apps in a 12-cell grid: 11 regular cells + the stacked deck slot.
        val plan = LargeFolderGrid.planOverflow(total = 13, capacity = 12)
        assertEquals(11, plan.normalCount)
        assertEquals(2, plan.stackItems)
        assertFalse(plan.showPlus)
        assertEquals(0, plan.hiddenCount)
    }

    @Test
    fun overflowBeyondThreeIconsUsesPlusBadge() {
        val plan = LargeFolderGrid.planOverflow(total = 15, capacity = 12)
        assertEquals(11, plan.normalCount)
        assertEquals(3, plan.stackItems)
        assertTrue(plan.showPlus)
        assertEquals(1, plan.hiddenCount)
    }

    @Test
    fun moreThanThreeOverflowAppsShowPlusBadge() {
        val plan = LargeFolderGrid.planOverflow(total = 17, capacity = 12)
        assertEquals(11, plan.normalCount)
        assertEquals(3, plan.stackItems)
        assertTrue(plan.showPlus)
        assertEquals(3, plan.hiddenCount)
    }

    @Test
    fun tinyGrid_overflowOnFiveItems() {
        // 4-cell grid (2x1 folder in landscape) with 5 apps: 3 regular + 2 stacked.
        val plan = LargeFolderGrid.planOverflow(total = 5, capacity = 4)
        assertEquals(3, plan.normalCount)
        assertEquals(2, plan.stackItems)
        assertFalse(plan.showPlus)
        assertEquals(0, plan.hiddenCount)
    }

    @Test
    fun tinyGrid_sevenAppsFillStackAndEightTriggerPlus() {
        val full = LargeFolderGrid.planOverflow(total = 7, capacity = 4)
        assertEquals(3, full.normalCount)
        assertEquals(3, full.stackItems)
        assertTrue(full.showPlus)
        assertEquals(1, full.hiddenCount)

        val plan = LargeFolderGrid.planOverflow(total = 8, capacity = 4)
        assertEquals(3, plan.normalCount)
        assertEquals(3, plan.stackItems)
        assertTrue(plan.showPlus)
        assertEquals(2, plan.hiddenCount)
    }

    @Test
    fun hiddenAppsAreNeverLostFromTheFolder() {
        // normal + stacked + hidden always accounts for every app.
        for (capacity in listOf(4, 12, 56)) {
            for (total in 0..capacity * 3) {
                val plan = LargeFolderGrid.planOverflow(total, capacity)
                assertEquals(
                    "total=$total capacity=$capacity",
                    total,
                    plan.normalCount + plan.stackItems + plan.hiddenCount,
                )
            }
        }
    }
}
