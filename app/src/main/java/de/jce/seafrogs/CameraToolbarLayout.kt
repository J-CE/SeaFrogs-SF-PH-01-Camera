package de.jce.seafrogs

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Stable children across mouse modes: no detach/attach during hover or capture changes. */
class CameraToolbarLayout(context: Context) : ViewGroup(context) {
    var columns = 3
        set(value) {
            val next = value.coerceAtLeast(1)
            if (field != next) { field = next; requestLayout() }
        }
    private fun visibleChildren() = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val children = visibleChildren()
        val count = columns.coerceAtMost(children.size.coerceAtLeast(1))
        var rowHeight = 0
        children.forEachIndexed { index, child ->
            val column = index % count
            val cellWidth = width * (column + 1) / count - width * column / count
            child.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(child.layoutParams.height.coerceAtLeast(0), MeasureSpec.EXACTLY))
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        val rows = (children.size + count - 1) / count
        setMeasuredDimension(width, resolveSize(rows * rowHeight, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val children = visibleChildren()
        val count = columns.coerceAtMost(children.size.coerceAtLeast(1))
        val rowHeight = children.maxOfOrNull { it.measuredHeight } ?: 0
        children.forEachIndexed { index, child ->
            val column = index % count
            val y = index / count * rowHeight
            child.layout(width * column / count, y, width * (column + 1) / count, y + rowHeight)
        }
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt())
}
