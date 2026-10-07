package de.jce.seafrogs
import org.junit.Assert.*
import org.junit.Test

class AutoTestPlanTest {
    @Test fun libraryInputsAreFrozenRawSeriesPerLensWithSeparateNightReference() {
        val steps = AutoTestPlan.libraries()
        assertEquals(5, steps.size)
        assertEquals(listOf("AUTO", "HDR", "NIGHT"), steps.drop(2).map { it.quality })
        assertEquals(listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE), steps.take(2).map { it.lens })
        assertTrue(steps.take(2).all { it.raw && it.nativeCapture && it.frameCount == 5 &&
            it.processing == ProcessingVariant.DEFAULT && it.quality == "STANDARD" &&
            it.zoom == 1f && it.ev == 0f && it.isoCap == 0 })
        assertEquals("NIGHT", steps.last().quality)
        assertFalse(steps.last().raw)
        assertEquals(1, steps.last().frameCount)
    }

    @Test fun processingTestHasPerLensBaselineThenVariantsAndIndependentNightReference() {
        val steps = AutoTestPlan.processing()
        assertEquals(7, steps.size)
        for (lens in listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE)) {
            val variants = steps.filter { it.lens == lens && it.nativeCapture }
            assertEquals(listOf(ProcessingVariant.DEFAULT, ProcessingVariant.NR_HIGH_QUALITY,
                ProcessingVariant.NR_EDGE_HIGH_QUALITY), variants.map { it.processing })
            assertTrue(variants.all { it.quality == "STANDARD" })
        }
        assertEquals("NIGHT", steps.last().quality)
        assertEquals(PhotoLens.MAIN, steps.last().lens)
        assertFalse(steps.last().nativeCapture)
        assertEquals(ProcessingVariant.NONE, steps.last().processing)
        assertTrue(steps.all { !it.raw && it.isoCap == 0 && it.zoom == 1f && it.ev == 0f })
        assertEquals(steps.size, steps.map { it.id }.toSet().size)
    }
    @Test fun isoComparisonUsesSameBackendAndUncroppedEvZeroPairs() {
        val steps = AutoTestPlan.iso(8_000_000L)
        assertEquals(6, steps.size)
        for (lens in listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE)) {
            assertEquals(listOf(0, 800, 400), steps.filter { it.lens == lens }.map { it.isoCap })
        }
        assertTrue(steps.all { it.nativeCapture && !it.raw && it.zoom == 1f && it.ev == 0f &&
            it.quality == "STANDARD" && it.longestTimeNs == 8_000_000L })
        assertEquals(steps.size, steps.map { it.id }.toSet().size)
    }
    @Test fun normalTestStartsEachLensWithoutCropAndCoversAllEvValues() {
        val steps = AutoTestPlan.create(false)
        for (lens in listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE)) {
            val selected = steps.filter { it.lens == lens }
            assertEquals(1f, selected.first().zoom)
            assertEquals("STANDARD", selected.first().quality)
            assertEquals(setOf(0f, 1f, 2f, -1f, -2f), selected.map { it.ev }.toSet())
        }
        assertEquals(steps.size, steps.map { it.id }.toSet().size)
    }
    @Test fun macroTestHasUncroppedReferenceAndIndependentCropSteps() {
        val steps = AutoTestPlan.create(true)
        assertEquals(PhotoLens.ULTRAWIDE, steps.first().lens)
        assertEquals(listOf(1f, 2f), steps.filter { it.lens == PhotoLens.MACRO && it.quality == "STANDARD" }.map { it.zoom })
        assertTrue(steps.all { it.ev == 0f })
    }
    @Test fun rawTestCoversEachRearLensWithoutCropOrExtensions() {
        val steps = AutoTestPlan.raw()
        assertEquals(listOf(PhotoLens.MAIN, PhotoLens.ULTRAWIDE, PhotoLens.MACRO), steps.map { it.lens })
        assertTrue(steps.all { it.raw && it.zoom == 1f && it.ev == 0f && it.quality == "STANDARD" })
    }
}
