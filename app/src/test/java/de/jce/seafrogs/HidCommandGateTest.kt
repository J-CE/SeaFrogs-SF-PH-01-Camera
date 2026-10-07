package de.jce.seafrogs

import org.junit.Assert.*
import org.junit.Test

class HidCommandGateTest {
    @Test fun heldButtonCommitsOnlyAfterQuiet() {
        val gate = HidCommandGate()
        for (time in 0L..1000L step 100L) { gate.movement(-16f, 0f, time); assertNull(gate.finish(time + 90)) }
        assertEquals(setOf(HidCommandGate.Command.LEFT), gate.finish(1150))
        assertNull(gate.finish(1300))
    }
    @Test fun diagonalIsACombination() {
        val gate = HidCommandGate(); gate.movement(-16f, -16f, 10)
        assertEquals(setOf(HidCommandGate.Command.LEFT, HidCommandGate.Command.UP), gate.finish(160))
    }
    @Test fun directionAndClickCannotBecomeAShutter() {
        val gate = HidCommandGate(); gate.movement(15f, 0f, 10)
        gate.signal(HidCommandGate.Command.CLICK, 40)
        assertEquals(setOf(HidCommandGate.Command.RIGHT, HidCommandGate.Command.CLICK), gate.finish(190))
    }
    @Test fun resetPreventsDelayedCommands() {
        val gate = HidCommandGate(); gate.movement(0f, 15f, 10); gate.reset()
        assertNull(gate.finish(500))
    }
    @Test fun separatedPressesAreSeparateCommands() {
        val gate = HidCommandGate(); gate.movement(0f, -16f, 10)
        assertEquals(setOf(HidCommandGate.Command.UP), gate.finish(160))
        gate.movement(0f, -16f, 300)
        assertEquals(setOf(HidCommandGate.Command.UP), gate.finish(450))
    }
    @Test fun closeDoublePressIsIndistinguishableFromHold() {
        val gate = HidCommandGate(); gate.movement(0f, -16f, 10); gate.movement(0f, -16f, 110)
        assertNull(gate.finish(160))
        assertEquals(setOf(HidCommandGate.Command.UP), gate.finish(260))
    }
}
