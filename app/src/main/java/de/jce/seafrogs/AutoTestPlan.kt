package de.jce.seafrogs

data class AutoTestStep(val id: String, val lens: PhotoLens, val zoom: Float = 1f,
    val ev: Float = 0f, val quality: String = "STANDARD")

/** Stable order and settings keep the comparison reproducible. */
object AutoTestPlan {
    fun create(macro: Boolean): List<AutoTestStep> = buildList {
        if (macro) {
            add(AutoTestStep("MACRO_UW_BASIS", PhotoLens.ULTRAWIDE))
            listOf(1f, 2f, 4f).forEach { add(AutoTestStep("MACRO_CROP_$it", PhotoLens.MACRO, it)) }
            listOf("AUTO", "HDR", "NIGHT").forEach { add(AutoTestStep("MACRO_$it", PhotoLens.MACRO, quality = it)) }
        } else {
            listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE).forEach { lens ->
                (if (lens == PhotoLens.MAIN) CameraControlCycles.zoomRatios else CameraControlCycles.ultrawideZoomRatios).forEach { add(AutoTestStep("NORMAL_${lens.name}_ZOOM_$it", lens, it)) }
                listOf(1f, 2f, -1f, -2f).forEach { add(AutoTestStep("NORMAL_${lens.name}_EV_$it", lens, ev = it)) }
                listOf("AUTO", "HDR", "NIGHT").forEach {
                    add(AutoTestStep("NORMAL_${lens.name}_$it", lens, quality = it))
                }
            }
        }
    }
}
