package de.jce.seafrogs

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Stable children across mouse modes: no detach/attach during hover or capture changes. */
class CameraToolbarLayout(context: Context) : ViewGroup(context) {
    var columns = 3
        set(value) {
            val requestedColumnCount = value.coerceAtLeast(1)
            if (field != requestedColumnCount) {
                field = requestedColumnCount
                requestLayout()
            }
        }

    private fun visibleChildren() =
        (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val visibleButtons = visibleChildren()
        val effectiveColumnCount = columns.coerceAtMost(visibleButtons.size.coerceAtLeast(1))
        var rowHeight = 0
        visibleButtons.forEachIndexed { index, child ->
            val column = index % effectiveColumnCount
            val cellWidth =
                width * (column + 1) / effectiveColumnCount - width * column / effectiveColumnCount
            child.measure(
                MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(
                    child.layoutParams.height.coerceAtLeast(0),
                    MeasureSpec.EXACTLY,
                ),
            )
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        val rows = (visibleButtons.size + effectiveColumnCount - 1) / effectiveColumnCount
        setMeasuredDimension(width, resolveSize(rows * rowHeight, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val visibleButtons = visibleChildren()
        val effectiveColumnCount = columns.coerceAtMost(visibleButtons.size.coerceAtLeast(1))
        val rowHeight = visibleButtons.maxOfOrNull { it.measuredHeight } ?: 0
        visibleButtons.forEachIndexed { index, child ->
            val column = index % effectiveColumnCount
            val rowTop = index / effectiveColumnCount * rowHeight
            child.layout(
                width * column / effectiveColumnCount,
                rowTop,
                width * (column + 1) / effectiveColumnCount,
                rowTop + rowHeight,
            )
        }
    }

    override fun generateDefaultLayoutParams() =
        LayoutParams(LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
}
