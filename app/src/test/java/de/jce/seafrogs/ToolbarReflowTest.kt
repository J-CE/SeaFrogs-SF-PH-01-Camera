package de.jce.seafrogs

import android.view.View
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ToolbarReflowTest {
    @Test fun measuredToolbarSurvivesCameraAndClassicModeSwitches() {
        val context = RuntimeEnvironment.getApplication()
        val grid = CameraToolbarLayout(context).apply { columns = 3 }
        val buttons = (0..5).map { Button(context) }
        buttons.forEach { button ->
            grid.addView(button, android.view.ViewGroup.LayoutParams(0, 48))
        }
        fun measure() {
            grid.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST))
            grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
        }
        measure()
        repeat(3) {
            buttons.take(5).forEach { it.visibility = View.GONE }
            grid.columns = 1
            measure()
            assertEquals(1, grid.columns)
            assertTrue(buttons.last().measuredWidth > 0)
            buttons.forEach { it.visibility = View.VISIBLE }
            grid.columns = 3
            measure()
            assertEquals(6, grid.childCount)
            buttons.forEachIndexed { index, button -> assertSame(button, grid.getChildAt(index)) }
            grid.columns = 6
            measure()
            grid.columns = 1 // sidebar changes may also reduce the columns
            measure()
        }
    }
}
