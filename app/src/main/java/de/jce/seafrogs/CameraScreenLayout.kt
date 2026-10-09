package de.jce.seafrogs

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.min
import kotlin.math.roundToInt

/** Largest complete photo/video rectangle; controls occupy spare space or overlay it. */
class CameraScreenLayout(context: Context) : ViewGroup(context) {
    var videoMode = false
    private lateinit var previewView: View
    private lateinit var statusView: View
    private lateinit var noticeView: View
    private lateinit var controlsView: View
    private lateinit var setupView: View
    private var sidebarWidth = 0

    fun attach(
        previewView: View,
        statusView: View,
        noticeView: View,
        controlsView: View,
        setupView: View,
    ) {
        this.previewView = previewView
        this.statusView = statusView
        this.noticeView = noticeView
        this.controlsView = controlsView
        this.setupView = setupView
        listOf(previewView, statusView, noticeView, controlsView, setupView).forEach { addView(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        val landscape = availableWidth > availableHeight
        val longSideRatio = if (videoMode) 16.0 / 9.0 else 4.0 / 3.0
        val previewAspectRatio = if (landscape) longSideRatio else 1.0 / longSideRatio
        val previewWidth =
            min(availableWidth.toDouble(), availableHeight * previewAspectRatio).roundToInt()
        val previewHeight = min(availableHeight, (previewWidth / previewAspectRatio).roundToInt())
        previewView.measure(exact(previewWidth), exact(previewHeight))
        val availableSideMargin = (availableWidth - previewWidth) / 2
        sidebarWidth = if (landscape && availableSideMargin >= dp(140)) availableSideMargin else 0
        val statusLines = if (sidebarWidth > 0) 8 else 3
        (statusView as? android.widget.TextView)?.let {
            if (it.maxLines != statusLines) it.maxLines = statusLines
        }
        val informationWidth = if (sidebarWidth > 0) sidebarWidth else availableWidth
        statusView.measure(exact(informationWidth), atMost(availableHeight))
        noticeView.measure(
            exact(informationWidth),
            atMost((availableHeight - statusView.measuredHeight).coerceAtLeast(0)),
        )
        val controlsWidth = if (sidebarWidth > 0) sidebarWidth else availableWidth
        val columns =
            when {
                sidebarWidth > 0 -> 1
                landscape -> 6
                else -> 3
            }
        (controlsView as? CameraToolbarLayout)?.columns = columns
        controlsView.measure(exact(controlsWidth), atMost(availableHeight))
        setupView.measure(
            exact(min(availableWidth, dp(360))),
            exact((availableHeight - controlsView.measuredHeight).coerceAtLeast(0)),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left - paddingLeft - paddingRight
        val height = bottom - top - paddingTop - paddingBottom
        val previewLeft = paddingLeft + (width - previewView.measuredWidth) / 2
        val previewTop = paddingTop + (height - previewView.measuredHeight) / 2
        previewView.layout(
            previewLeft,
            previewTop,
            previewLeft + previewView.measuredWidth,
            previewTop + previewView.measuredHeight,
        )
        statusView.layout(
            paddingLeft,
            paddingTop,
            paddingLeft + statusView.measuredWidth,
            paddingTop + statusView.measuredHeight,
        )
        // On tall phones this banner sits in the letterbox, before the photo.
        val noticeTop = paddingTop + statusView.measuredHeight
        noticeView.layout(
            paddingLeft,
            noticeTop,
            paddingLeft + noticeView.measuredWidth,
            noticeTop + noticeView.measuredHeight,
        )
        val controlsLeft = paddingLeft + width - controlsView.measuredWidth
        val controlsTop = paddingTop + height - controlsView.measuredHeight
        controlsView.layout(
            controlsLeft,
            controlsTop,
            controlsLeft + controlsView.measuredWidth,
            paddingTop + height,
        )
        val setupLeft = paddingLeft + width - setupView.measuredWidth
        setupView.layout(
            setupLeft,
            paddingTop,
            paddingLeft + width,
            paddingTop + setupView.measuredHeight,
        )
    }

    override fun generateDefaultLayoutParams() =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)

    private fun atMost(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.AT_MOST)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
