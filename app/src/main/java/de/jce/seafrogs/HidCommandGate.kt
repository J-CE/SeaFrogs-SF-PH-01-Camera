package de.jce.seafrogs

/** The measured device sends movement repeats, but no directional key-up.
 * Commit a burst after quiet time. Mixed directions/clicks never invoke a camera command. Mode chords are handled separately.
 * A fast double press and a held button can be indistinguishable in this protocol.
 */
class HidCommandGate {
    enum class Command { LEFT, RIGHT, UP, DOWN, CLICK }
    private val commands = linkedSetOf<Command>()
    private var lastSignal = Long.MIN_VALUE
    private var chordConsumed = false
    fun signal(command: Command, time: Long) {
        if (chordConsumed && time - lastSignal >= QUIET_MS) reset()
        commands.add(command); lastSignal = time
    }
    fun movement(x: Float, y: Float, time: Long) {
        if (x < 0) signal(Command.LEFT, time)
        if (x > 0) signal(Command.RIGHT, time)
        if (y < 0) signal(Command.UP, time)
        if (y > 0) signal(Command.DOWN, time)
    }
    fun finish(time: Long): Set<Command>? {
        if (commands.isEmpty() || time - lastSignal < QUIET_MS) return null
        val result = if (chordConsumed) null else commands.toSet()
        reset()
        return result
    }
    enum class ModeSwitch { CAMERA, CLASSIC }
    fun modeSwitch(commands: Set<Command>): ModeSwitch? = when(commands) {
        setOf(Command.LEFT,Command.UP) -> ModeSwitch.CAMERA
        setOf(Command.RIGHT,Command.DOWN) -> ModeSwitch.CLASSIC
        else -> null
    }
    /** Switch while held; consume trailing reports until the same burst ends. */
    fun takeModeSwitch(): ModeSwitch? {
        if (chordConsumed) return null
        return modeSwitch(commands)?.also { chordConsumed = true }
    }
    fun reset(preserveChord: Boolean = false) {
        if (preserveChord && chordConsumed) return
        commands.clear(); lastSignal = Long.MIN_VALUE; chordConsumed = false
    }
    companion object { const val QUIET_MS = 150L }
}
