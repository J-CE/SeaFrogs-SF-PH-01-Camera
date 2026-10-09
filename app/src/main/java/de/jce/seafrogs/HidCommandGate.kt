package de.jce.seafrogs

/**
 * The measured device sends movement repeats, but no directional key-up. Commit a burst after quiet
 * time. Mixed directions/clicks never invoke a camera command. Mode chords are handled separately.
 * A fast double press and a held button can be indistinguishable in this protocol.
 */
class HidCommandGate {
    enum class Command {
        LEFT,
        RIGHT,
        UP,
        DOWN,
        CLICK,
    }

    private val burstCommands = linkedSetOf<Command>()
    private var lastSignalUptimeMs = Long.MIN_VALUE
    private var modeSwitchConsumed = false

    fun signal(command: Command, time: Long) {
        if (modeSwitchConsumed && time - lastSignalUptimeMs >= QUIET_MS) reset()
        burstCommands.add(command)
        lastSignalUptimeMs = time
    }

    fun movement(x: Float, y: Float, time: Long) {
        if (x < 0) signal(Command.LEFT, time)
        if (x > 0) signal(Command.RIGHT, time)
        if (y < 0) signal(Command.UP, time)
        if (y > 0) signal(Command.DOWN, time)
    }

    fun finish(time: Long): Set<Command>? {
        if (burstCommands.isEmpty() || time - lastSignalUptimeMs < QUIET_MS) return null
        val result = if (modeSwitchConsumed) null else burstCommands.toSet()
        reset()
        return result
    }

    enum class ModeSwitch {
        CAMERA,
        CLASSIC,
    }

    fun modeSwitch(burstCommands: Set<Command>): ModeSwitch? =
        when (burstCommands) {
            setOf(Command.LEFT, Command.UP) -> ModeSwitch.CAMERA
            setOf(Command.RIGHT, Command.DOWN) -> ModeSwitch.CLASSIC
            else -> null
        }

    /** Switch while held; consume trailing reports until the same burst ends. */
    fun takeModeSwitch(): ModeSwitch? {
        if (modeSwitchConsumed) return null
        return modeSwitch(burstCommands)?.also { modeSwitchConsumed = true }
    }

    fun reset(preserveChord: Boolean = false) {
        if (preserveChord && modeSwitchConsumed) return
        burstCommands.clear()
        lastSignalUptimeMs = Long.MIN_VALUE
        modeSwitchConsumed = false
    }

    companion object {
        const val QUIET_MS = 150L
    }
}
