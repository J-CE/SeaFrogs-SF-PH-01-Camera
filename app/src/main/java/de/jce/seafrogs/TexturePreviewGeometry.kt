package de.jce.seafrogs

/** Camera2 TextureView already compensates sensor orientation. This transform
 * undoes its default stretch, fits the complete image and compensates display rotation.
 * JPEG/DNG orientation is independent of this view-only transform.
 */
data class TexturePreviewGeometry(val scaleX: Float, val scaleY: Float, val rotationDegrees: Float) {
    companion object {
        fun fit(viewWidth: Int, viewHeight: Int, bufferWidth: Int, bufferHeight: Int,
                sensorOrientation: Int, displayRotation: Int): TexturePreviewGeometry {
            require(viewWidth > 0 && viewHeight > 0 && bufferWidth > 0 && bufferHeight > 0)
            require(sensorOrientation in listOf(0,90,180,270) && displayRotation in 0..3)
            val sensorSwapsAxes = sensorOrientation % 180 != 0
            val sourceWidth = if(sensorSwapsAxes) bufferHeight else bufferWidth
            val sourceHeight = if(sensorSwapsAxes) bufferWidth else bufferHeight
            val displaySwapsAxes = displayRotation % 2 != 0
            val outputWidth = if(displaySwapsAxes) sourceHeight else sourceWidth
            val outputHeight = if(displaySwapsAxes) sourceWidth else sourceHeight
            val scale = minOf(viewWidth.toFloat()/outputWidth,viewHeight.toFloat()/outputHeight)
            return TexturePreviewGeometry(sourceWidth*scale/viewWidth,sourceHeight*scale/viewHeight,-90f*displayRotation)
        }
    }
}
