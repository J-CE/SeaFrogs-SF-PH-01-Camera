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
    @Test fun modeChordsIgnoreDirectionArrivalOrder() {
        for (pair in listOf(listOf(HidCommandGate.Command.LEFT,HidCommandGate.Command.UP),
            listOf(HidCommandGate.Command.UP,HidCommandGate.Command.LEFT))) {
            val gate=HidCommandGate(); gate.signal(pair[0],0); gate.signal(pair[1],20)
            assertEquals(HidCommandGate.ModeSwitch.CAMERA,gate.modeSwitch(gate.finish(170)!!))
        }
        for (pair in listOf(listOf(HidCommandGate.Command.RIGHT,HidCommandGate.Command.DOWN),
            listOf(HidCommandGate.Command.DOWN,HidCommandGate.Command.RIGHT))) {
            val gate=HidCommandGate(); gate.signal(pair[0],0); gate.signal(pair[1],20)
            assertEquals(HidCommandGate.ModeSwitch.CLASSIC,gate.modeSwitch(gate.finish(170)!!))
        }
    }
    @Test fun systemChordsAndExtraClickCannotSwitchModes() {
        val gate=HidCommandGate()
        assertNull(gate.modeSwitch(setOf(HidCommandGate.Command.UP,HidCommandGate.Command.DOWN)))
        assertNull(gate.modeSwitch(setOf(HidCommandGate.Command.LEFT,HidCommandGate.Command.RIGHT)))
        assertNull(gate.modeSwitch(setOf(HidCommandGate.Command.LEFT,HidCommandGate.Command.UP,HidCommandGate.Command.CLICK)))
    }
}
