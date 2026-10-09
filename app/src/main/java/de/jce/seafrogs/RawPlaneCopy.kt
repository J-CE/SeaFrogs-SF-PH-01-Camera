package de.jce.seafrogs

import java.nio.ByteBuffer

/** Pack RAW_SENSOR uint16 samples without copying row padding or assuming pixel stride. */
object RawPlaneCopy {
    fun copy(
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        destination: ByteBuffer,
        frameIndex: Int,
    ) {
        require(
            width > 0 &&
                height > 0 &&
                pixelStride >= 2 &&
                rowStride >= (width - 1) * pixelStride + 2
        )
        val frameBytes = Math.multiplyExact(Math.multiplyExact(width, height), 2)
        val offset = Math.multiplyExact(frameIndex, frameBytes)
        require(frameIndex >= 0 && destination.capacity().toLong() >= offset.toLong() + frameBytes)
        val input = source.duplicate()
        val base = input.position()
        require(
            input.remaining().toLong() >= (height - 1L) * rowStride + (width - 1L) * pixelStride + 2
        )
        val output = destination.duplicate().apply { position(offset) }
        val row = ByteArray(width * 2)
        for (y in 0 until height) {
            if (pixelStride == 2) {
                input.position(base + y * rowStride)
                input.get(row)
            } else
                for (x in 0 until width) {
                    val index = base + y * rowStride + x * pixelStride
                    row[x * 2] = input.get(index)
                    row[x * 2 + 1] = input.get(index + 1)
                }
            output.put(row)
        }
    }
}
