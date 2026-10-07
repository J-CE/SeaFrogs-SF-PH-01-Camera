package de.jce.seafrogs
import org.junit.Assert.*
import org.junit.Test

class AutoTestPlanTest {
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
        assertEquals(listOf(1f, 2f, 4f), steps.filter { it.lens == PhotoLens.MACRO && it.quality == "STANDARD" }.map { it.zoom })
        assertTrue(steps.all { it.ev == 0f })
    }
}
