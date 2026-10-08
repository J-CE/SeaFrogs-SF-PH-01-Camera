package de.jce.seafrogs

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.min
import kotlin.math.roundToInt

/** Largest complete photo/video rectangle; controls occupy spare space or overlay it. */
class CameraScreenLayout(context: Context) : ViewGroup(context) {
    var videoMode = false
    var housingControl = false
    private lateinit var preview: View
    private lateinit var status: View
    private lateinit var notice: View
    private lateinit var controls: View
    private lateinit var setup: View
    private var sidebarWidth = 0

    fun attach(preview: View, status: View, notice: View, controls: View, setup: View) {
        this.preview = preview; this.status = status; this.notice = notice
        this.controls = controls; this.setup = setup
        listOf(preview, status, notice, controls, setup).forEach { addView(it) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val availableHeight = (height - paddingTop - paddingBottom).coerceAtLeast(0)
        val landscape = availableWidth > availableHeight
        val longSideRatio = if (videoMode) 16.0 / 9.0 else 4.0 / 3.0
        val ratio = if (landscape) longSideRatio else 1.0 / longSideRatio
        val photoWidth = min(availableWidth.toDouble(), availableHeight * ratio).roundToInt()
        val photoHeight = min(availableHeight, (photoWidth / ratio).roundToInt())
        preview.measure(exact(photoWidth), exact(photoHeight))
        val spareSide = (availableWidth - photoWidth) / 2
        sidebarWidth = if (landscape && spareSide >= dp(140)) spareSide else 0
        (status as? android.widget.TextView)?.maxLines = if (sidebarWidth > 0) 8 else 3
        val informationWidth = if (sidebarWidth > 0) sidebarWidth else availableWidth
        status.measure(exact(informationWidth), atMost(availableHeight))
        notice.measure(exact(informationWidth), atMost((availableHeight - status.measuredHeight).coerceAtLeast(0)))
        val controlsWidth = if (housingControl) min(availableWidth, dp(88)) else if(sidebarWidth > 0) sidebarWidth else availableWidth
        (controls as? android.widget.GridLayout)?.columnCount = when {
            housingControl || sidebarWidth > 0 -> 1
            landscape -> 6
            else -> 3
        }
        controls.measure(exact(controlsWidth), atMost(availableHeight))
        setup.measure(exact(min(availableWidth, dp(360))), exact(availableHeight))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left - paddingLeft - paddingRight
        val height = bottom - top - paddingTop - paddingBottom
        val photoLeft = paddingLeft + (width - preview.measuredWidth) / 2
        val photoTop = paddingTop + (height - preview.measuredHeight) / 2
        preview.layout(photoLeft, photoTop, photoLeft + preview.measuredWidth, photoTop + preview.measuredHeight)
        status.layout(paddingLeft, paddingTop, paddingLeft + status.measuredWidth, paddingTop + status.measuredHeight)
        // On tall phones this banner sits in the letterbox, before the photo.
        val noticeTop = paddingTop + status.measuredHeight
        notice.layout(paddingLeft, noticeTop, paddingLeft + notice.measuredWidth, noticeTop + notice.measuredHeight)
        val controlsLeft = paddingLeft + width - controls.measuredWidth
        val controlsTop = paddingTop + height - controls.measuredHeight
        controls.layout(controlsLeft, controlsTop, controlsLeft + controls.measuredWidth, paddingTop + height)
        val setupLeft = paddingLeft + width - setup.measuredWidth
        setup.layout(setupLeft, paddingTop, paddingLeft + width, paddingTop + height)
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)
    private fun atMost(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.AT_MOST)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
