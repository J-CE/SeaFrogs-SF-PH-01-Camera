package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class AutofocusStabilityTest {
    @Test
    fun requiresContinuousFreshFocusedFrames() {
        val focus = AutofocusStability()
        focus.frame(true, 1, 1000)
        focus.frame(true, 2, 1200)
        assertFalse(focus.stable(1399))
        focus.frame(true, 3, 1400)
        assertTrue(focus.stable(1400))
        assertFalse(focus.stable(1901))
    }

    @Test
    fun searchingResetsConfirmation() {
        val focus = AutofocusStability()
        focus.frame(true, 1, 1000)
        focus.frame(true, 2, 1200)
        focus.frame(false, 3, 1400)
        focus.frame(true, 4, 1500)
        assertFalse(focus.stable(1500))
    }

    @Test
    fun duplicateAndMissingFramesCannotConfirmFocus() {
        val focus = AutofocusStability()
        focus.frame(true, 1, 1000)
        focus.frame(true, 1, 1200)
        focus.frame(true, 1, 1400)
        assertFalse(focus.stable(1400))
        focus.frame(true, null, 1401)
        assertFalse(focus.stable(1401))
    }

    @Test
    fun gapAndSessionResetInvalidateEarlierFocus() {
        val focus = AutofocusStability()
        focus.frame(true, 1, 1000)
        focus.frame(true, 2, 1200)
        focus.frame(true, 3, 1800)
        assertFalse(focus.stable(1800))
        focus.frame(true, 4, 2000)
        focus.frame(true, 5, 2200)
        assertTrue(focus.stable(2200))
        focus.reset()
        assertFalse(focus.stable(2200))
    }
}
