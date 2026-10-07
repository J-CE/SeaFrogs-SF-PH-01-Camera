package de.jce.seafrogs

data class AutoTestStep(val id: String, val lens: PhotoLens, val zoom: Float = 1f,
    val ev: Float = 0f, val quality: String = "STANDARD", val raw: Boolean = false,
    val nativeCapture: Boolean = false, val isoCap: Int = 0, val longestTimeNs: Long = 33_333_333L,
    val processing: ProcessingVariant = ProcessingVariant.NONE, val frameCount: Int = 1, val fusion: Boolean = false)

/** Stable order and settings keep the comparison reproducible. */
object AutoTestPlan {
    fun fusion(): List<AutoTestStep> = listOf(
        AutoTestStep("FUSION_MAIN", PhotoLens.MAIN, quality = "MEHRBILD", nativeCapture = true,
            processing = ProcessingVariant.DEFAULT, frameCount = 5, fusion = true),
        AutoTestStep("FUSION_ULTRAWIDE", PhotoLens.ULTRAWIDE, quality = "MEHRBILD", nativeCapture = true,
            processing = ProcessingVariant.DEFAULT, frameCount = 5, fusion = true),
        AutoTestStep("FUSION_MAIN_ISO800", PhotoLens.MAIN, quality = "MEHRBILD", nativeCapture = true,
            isoCap = 800, processing = ProcessingVariant.DEFAULT, frameCount = 5, fusion = true)
    )

    fun libraries(): List<AutoTestStep> = listOf(
        AutoTestStep("LIBRARY_MAIN_RAW_SERIES", PhotoLens.MAIN, raw = true,
            nativeCapture = true, processing = ProcessingVariant.DEFAULT, frameCount = 5),
        AutoTestStep("LIBRARY_ULTRAWIDE_RAW_SERIES", PhotoLens.ULTRAWIDE, raw = true,
            nativeCapture = true, processing = ProcessingVariant.DEFAULT, frameCount = 5),
        AutoTestStep("LIBRARY_MAIN_AUTO_REFERENCE", PhotoLens.MAIN, quality = "AUTO"),
        AutoTestStep("LIBRARY_MAIN_HDR_REFERENCE", PhotoLens.MAIN, quality = "HDR"),
        AutoTestStep("LIBRARY_MAIN_NIGHT_REFERENCE", PhotoLens.MAIN, quality = "NIGHT")
    )

    fun processing(): List<AutoTestStep> = buildList {
        for (lens in listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE)) {
            for (variant in listOf(ProcessingVariant.DEFAULT, ProcessingVariant.NR_HIGH_QUALITY,
                ProcessingVariant.NR_EDGE_HIGH_QUALITY)) {
                add(AutoTestStep("QUALITY_${lens.name}_${variant.name}", lens,
                    nativeCapture = true, processing = variant))
            }
        }
        // Extension controls its own exposure/WB/focus and resolution. This
        // is a separately labelled reference, not a locked processing pair.
        add(AutoTestStep("QUALITY_MAIN_NIGHT_REFERENCE", PhotoLens.MAIN, quality = "NIGHT"))
    }

    fun create(macro: Boolean): List<AutoTestStep> = buildList {
        if (macro) {
            add(AutoTestStep("MACRO_UW_BASIS", PhotoLens.ULTRAWIDE))
            CameraControlCycles.macroCropRatios.forEach { add(AutoTestStep("MACRO_CROP_$it", PhotoLens.MACRO, it)) }
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

    fun iso(longestTimeNs: Long): List<AutoTestStep> = listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE)
        .flatMap { lens -> listOf(0, 800, 400).map { cap ->
            AutoTestStep("ISO_${lens.name}_${if (cap == 0) "AUTO" else cap.toString()}", lens,
                nativeCapture = true, isoCap = cap, longestTimeNs = longestTimeNs)
        } }

    fun raw(): List<AutoTestStep> = listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE, PhotoLens.MACRO)
        .map { AutoTestStep("RAW_${it.name}", it, raw = true) }
}
