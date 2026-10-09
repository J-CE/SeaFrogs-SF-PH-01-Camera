package de.jce.seafrogs

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class RawPlaneCopyTest {
    @Test
    fun preservesSamplesButDropsPaddingAndRespectsBufferPosition() {
        val input =
            ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3, 4, 88, 88, 5, 6, 7, 8)).apply { position(1) }
        val output =
            ByteBuffer.allocate(16).apply {
                put(ByteArray(16) { 42 })
                position(3)
            }
        RawPlaneCopy.copy(input, 6, 2, 2, 2, output, 1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), output.array().copyOfRange(8, 16))
        assertTrue(output.array().take(8).all { it == 42.toByte() })
        assertEquals(1, input.position())
        assertEquals(3, output.position())
    }

    @Test
    fun handlesSeparatedUint16Pixels() {
        val input = ByteBuffer.wrap(byteArrayOf(1, 2, 88, 88, 3, 4, 88, 88, 5, 6, 88, 88, 7, 8))
        val output = ByteBuffer.allocate(8)
        RawPlaneCopy.copy(input, 8, 4, 2, 2, output, 0)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), output.array())
    }

    @Test
    fun truncatedFinalRowIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            RawPlaneCopy.copy(ByteBuffer.allocate(7), 4, 2, 2, 2, ByteBuffer.allocate(8), 0)
        }
    }

    @Test
    fun rejectsTooSmallDestinationAndNegativeFrame() {
        assertThrows(IllegalArgumentException::class.java) {
            RawPlaneCopy.copy(ByteBuffer.allocate(8), 4, 2, 2, 2, ByteBuffer.allocate(15), 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RawPlaneCopy.copy(ByteBuffer.allocate(8), 4, 2, 2, 2, ByteBuffer.allocate(8), -1)
        }
    }
}
