package de.jce.seafrogs

import android.view.View
import android.widget.Button
import android.widget.GridLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ToolbarReflowTest {
    @Test fun measuredToolbarSurvivesCameraAndClassicModeSwitches() {
        val context = RuntimeEnvironment.getApplication()
        val grid = GridLayout(context).apply { columnCount = 3 }
        val buttons = (0..5).map { Button(context) }
        buttons.forEach { button ->
            grid.addView(button, GridLayout.LayoutParams().apply {
                width = 0; height = 48
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        fun measure() {
            grid.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST))
            grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
        }
        measure() // resolves automatic columns, as on the device before mouse selection
        repeat(3) {
            buttons.take(5).forEach { it.visibility = View.GONE }
            reflowToolbar(grid, 1)
            measure()
            assertEquals(1, grid.columnCount)
            assertTrue(buttons.last().measuredWidth > 0)
            buttons.forEach { it.visibility = View.VISIBLE }
            reflowToolbar(grid, 3)
            measure()
            assertEquals(6, grid.childCount)
            buttons.forEachIndexed { index, button -> assertSame(button, grid.getChildAt(index)) }
            reflowToolbar(grid, 6)
            measure()
            reflowToolbar(grid, 1) // sidebar changes may also reduce the columns
            measure()
        }
    }
}
