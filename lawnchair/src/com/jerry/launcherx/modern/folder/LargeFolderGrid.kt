package com.jerry.launcherx.modern.folder

/**
 * Pure span → grid → capacity rules for large folders.
 *
 * The folder's workspace footprint (span) and its internal grid are two independent
 * concepts: the grid is derived from the span and the screen orientation — the axis
 * along the screen's long side gets `2n` cells, the other axis `2n - 1` (landscape:
 * `columns = 2 * spanX`, `rows = 2 * spanY - 1`; portrait: the other way round).
 * The span itself is capped by the configured maximum span (default 3, settings
 * allow up to 4). Capacity is a computed property (`columns * rows`) and is never
 * persisted; apps beyond capacity stay in the folder and are only reachable through
 * the opened folder.
 */
object LargeFolderGrid {

    /** Smallest / largest / default workspace span offered in settings. */
    const val MIN_MAX_SPAN = 2
    const val MAX_MAX_SPAN = 4
    const val DEFAULT_MAX_SPAN = 3

    data class Size(val columns: Int, val rows: Int) {
        val capacity: Int
            get() = columns * rows
    }

    /**
     * Internal grid for a folder spanning [spanX] x [spanY] workspace cells. The axis
     * that lies along the screen's long side (given by [landscape]) uses the `2n`
     * rule, the other axis `2n - 1`.
     */
    fun gridForSpan(spanX: Int, spanY: Int, landscape: Boolean): Size {
        val cols = if (landscape) 2 * spanX else 2 * spanX - 1
        val rows = if (landscape) 2 * spanY - 1 else 2 * spanY
        return Size(cols.coerceAtLeast(1), rows.coerceAtLeast(1))
    }

    /** Clamps a configured span limit to the range offered in settings. */
    fun clampMaxSpan(span: Int): Int = span.coerceIn(MIN_MAX_SPAN, MAX_MAX_SPAN)

    /**
     * What to show for [total] apps in a grid of [capacity] cells.
     *
     * The last cell becomes a stacked-deck affordance when apps overflow:
     *  - up to 3 overflow icons stack diagonally (top-left → bottom-right);
     *  - when more apps are hidden, a "+N" badge on the deck front shows the count;
     *    the rest are reachable by opening the folder.
     */
    data class OverflowPlan(
        val normalCount: Int,
        val stackItems: Int,
        val showPlus: Boolean,
        val hiddenCount: Int,
    ) {
        val hasOverflow: Boolean
            get() = stackItems > 0 || showPlus
    }

    fun planOverflow(total: Int, capacity: Int): OverflowPlan {
        if (capacity < 1 || total <= capacity) {
            return OverflowPlan(total.coerceAtLeast(0), 0, false, 0)
        }
        val normal = (capacity - 1).coerceAtLeast(0)
        val overflow = total - normal
        return if (overflow > MAX_STACKED_ICONS) {
            OverflowPlan(normal, MAX_STACKED_ICONS, true, overflow - MAX_STACKED_ICONS)
        } else {
            OverflowPlan(normal, overflow, false, 0)
        }
    }

    /** Maximum icons drawn in the stacked-deck overflow cell. */
    const val MAX_STACKED_ICONS = 3
}
