package com.lonx.lyrico.utils

import java.nio.ByteBuffer

class LibEbuR128(val channels: Int, sampleRate: Int) : AutoCloseable {

    companion object {
        init {
            System.loadLibrary("ebur128")
        }
        const val FORMAT_SHORT = 1
        const val FORMAT_FLOAT = 2
    }

    private var nativePtr: Long = 0
    var sampleCount: Long = 0
        private set

    init {
        nativePtr = initNative(channels, sampleRate)
        if (nativePtr == 0L) {
            throw IllegalStateException("Failed to initialize libebur128")
        }
    }

    fun processDirect(buffer: ByteBuffer, isFloat: Boolean, frameCount: Int) {
        if (nativePtr == 0L || frameCount <= 0) return
        val format = if (isFloat) FORMAT_FLOAT else FORMAT_SHORT
        processDirectNative(nativePtr, buffer, format, frameCount)
        sampleCount += frameCount
    }

    val loudness: Double
        get() = if (nativePtr == 0L) -70.0 else getLoudnessNative(nativePtr)

    val truePeak: Double
        get() = if (nativePtr == 0L) 0.0 else getTruePeakNative(nativePtr)

    override fun close() {
        if (nativePtr != 0L) {
            destroyNative(nativePtr)
            nativePtr = 0L
        }
    }

    private external fun initNative(channels: Int, sampleRate: Int): Long
    private external fun destroyNative(statePtr: Long)
    private external fun processDirectNative(statePtr: Long, buffer: ByteBuffer, format: Int, frames: Int)
    private external fun getLoudnessNative(statePtr: Long): Double
    private external fun getTruePeakNative(statePtr: Long): Double
}