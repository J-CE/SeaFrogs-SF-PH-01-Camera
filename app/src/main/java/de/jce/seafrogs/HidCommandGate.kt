package de.jce.seafrogs

/** The measured device sends movement repeats, but no directional key-up.
 * Commit a burst after quiet time. Mixed directions/clicks never invoke a camera command. Mode chords are handled separately.
 * A fast double press and a held button can be indistinguishable in this protocol.
 */
class HidCommandGate {
    enum class Command { LEFT, RIGHT, UP, DOWN, CLICK }
    private val commands = linkedSetOf<Command>()
    private var lastSignal = Long.MIN_VALUE
    fun signal(command: Command, time: Long) { commands.add(command); lastSignal = time }
    fun movement(x: Float, y: Float, time: Long) {
        if (x < 0) signal(Command.LEFT, time)
        if (x > 0) signal(Command.RIGHT, time)
        if (y < 0) signal(Command.UP, time)
        if (y > 0) signal(Command.DOWN, time)
    }
    fun finish(time: Long): Set<Command>? {
        if (commands.isEmpty() || time - lastSignal < QUIET_MS) return null
        return commands.toSet().also { reset() }
    }
    enum class ModeSwitch { CAMERA, CLASSIC }
    fun modeSwitch(commands: Set<Command>): ModeSwitch? = when(commands) {
        setOf(Command.LEFT,Command.UP) -> ModeSwitch.CAMERA
        setOf(Command.RIGHT,Command.DOWN) -> ModeSwitch.CLASSIC
        else -> null
    }
    fun reset() { commands.clear(); lastSignal = Long.MIN_VALUE }
    companion object { const val QUIET_MS = 150L }
}
