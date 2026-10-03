package com.daboua.captions

object WhisperNative {

    init {
        System.loadLibrary("whisper_jni")
    }

    external fun init(
        modelPath: String
    ): Long

    external fun free(
        context: Long
    )

    external fun transcribe(
        context: Long,
        audio: FloatArray,
        numThreads: Int
    ): Int

    external fun getSegmentCount(
        context: Long
    ): Int

    external fun getSegmentText(
        context: Long,
        index: Int
    ): String

    external fun getSegmentStart(
        context: Long,
        index: Int
    ): Long

    external fun getSegmentEnd(
        context: Long,
        index: Int
    ): Long
}
