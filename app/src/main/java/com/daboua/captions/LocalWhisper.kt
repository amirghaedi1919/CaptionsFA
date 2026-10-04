package com.daboua.captions

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class LocalTranscriptSegment(
    val text: String,
    val startTime: Long,
    val endTime: Long
)

object LocalWhisper {

    private const val MODEL_NAME =
        "ggml-tiny-q5_1.bin"

    private const val MODEL_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"

    /*
     * هر بار فقط چند ثانیه از صدا را به Whisper می‌دهیم.
     *
     * مزیت:
     * ویدیو لازم نیست کامل پردازش شود.
     * اولین کپشن خیلی زودتر ظاهر می‌شود.
     */
    private const val CHUNK_MS = 4000L

    /*
     * کمی همپوشانی برای اینکه کلمه‌های مرزی
     * بین دو قطعه از دست نروند.
     */
    private const val OVERLAP_MS = 700L

    fun transcribeVideoStreaming(
        context: Context,
        uri: Uri,
        threads: Int = 4,
        onSegment: (
            LocalTranscriptSegment
        ) -> Unit,
        onProgress: (
            Long,
            Long
        ) -> Unit = { _, _ -> }
    ) {

        val modelFile =
            ensureModel(context)

        val nativeContext =
            WhisperNative.init(
                modelFile.absolutePath
            )

        if (nativeContext == 0L) {
            throw IllegalStateException(
                "بارگذاری مدل Whisper ناموفق بود"
            )
        }

        try {

            val duration =
                getAudioDuration(
                    context,
                    uri
                )

            if (duration <= 0L) {
                throw IllegalStateException(
                    "مدت صدای ویدیو قابل تشخیص نیست"
                )
            }

            var chunkStart = 0L

            var lastEmittedEnd = 0L

            while (chunkStart < duration) {

                val chunkEnd =
                    (
                        chunkStart +
                            CHUNK_MS
                    ).coerceAtMost(
                        duration
                    )

                val audio =
                    decodeAudioChunk(
                        context = context,
                        uri = uri,
                        startMs = chunkStart,
                        endMs = chunkEnd
                    )

                if (audio.isNotEmpty()) {

                    val result =
                        WhisperNative.transcribe(
                            context =
                                nativeContext,
                            audio =
                                audio,
                            numThreads =
                                threads.coerceIn(
                                    1,
                                    8
                                )
                        )

                    if (result != 0) {
                        throw IllegalStateException(
                            "Whisper خطا داد: $result"
                        )
                    }

                    val count =
                        WhisperNative
                            .getSegmentCount(
                                nativeContext
                            )

                    for (
                        index in
                        0 until count
                    ) {

                        val rawText =
                            WhisperNative
                                .getSegmentText(
                                    nativeContext,
                                    index
                                )

                        val text =
                            cleanText(
                                rawText
                            )

                        if (text.isBlank()) {
                            continue
                        }

                        /*
                         * timestampهای Whisper
                         * در واحد 10ms هستند.
                         */
                        val relativeStart =
                            WhisperNative
                                .getSegmentStart(
                                    nativeContext,
                                    index
                                ) * 10L

                        val relativeEnd =
                            WhisperNative
                                .getSegmentEnd(
                                    nativeContext,
                                    index
                                ) * 10L

                        var absoluteStart =
                            chunkStart +
                                relativeStart

                        var absoluteEnd =
                            chunkStart +
                                relativeEnd

                        /*
                         * محدوده را به chunk محدود می‌کنیم.
                         */
                        absoluteStart =
                            absoluteStart.coerceIn(
                                chunkStart,
                                chunkEnd
                            )

                        absoluteEnd =
                            absoluteEnd.coerceIn(
                                absoluteStart + 100L,
                                chunkEnd
                            )

                        /*
                         * همپوشانی بین chunkها باعث تکرار
                         * Caption می‌شود؛ اینجا حذفش می‌کنیم.
                         */
                        if (
                            absoluteEnd <=
                                lastEmittedEnd + 150L
                        ) {
                            continue
                        }

                        if (
                            absoluteEnd <=
                                absoluteStart
                        ) {
                            continue
                        }

                        val segment =
                            LocalTranscriptSegment(
                                text = text,
                                startTime =
                                    absoluteStart,
                                endTime =
                                    absoluteEnd
                            )

                        onSegment(
                            segment
                        )

                        lastEmittedEnd =
                            absoluteEnd
                    }
                }

                onProgress(
                    chunkEnd,
                    duration
                )

                /*
                 * chunk بعدی کمی قبل از انتهای قبلی
                 * شروع می‌شود تا کلمه‌های مرزی از دست نروند.
                 */
                chunkStart =
                    (
                        chunkEnd -
                            OVERLAP_MS
                    ).coerceAtLeast(
                        chunkStart + 500L
                    )
            }

        } finally {

            WhisperNative.free(
                nativeContext
            )
        }
    }

    /*
     * برای سازگاری با نسخه قبلی پروژه.
     */
    fun transcribeVideo(
        context: Context,
        uri: Uri,
        threads: Int = 4
    ): List<LocalTranscriptSegment> {

        val result =
            ArrayList<LocalTranscriptSegment>()

        transcribeVideoStreaming(
            context = context,
            uri = uri,
            threads = threads,
            onSegment = {
                result.add(it)
            }
        )

        return result
    }

    private fun cleanText(
        value: String
    ): String {

        return value
            .replace(
                Regex("\\s+"),
                " "
            )
            .replace(
                " ،",
                "،"
            )
            .replace(
                " .",
                "."
            )
            .replace(
                " ؟",
                "؟"
            )
            .replace(
                " !",
                "!"
            )
            .trim()
    }

    private fun ensureModel(
        context: Context
    ): File {

        val directory =
            File(
                context.filesDir,
                "whisper_models"
            )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val modelFile =
            File(
                directory,
                MODEL_NAME
            )

        if (
            modelFile.exists() &&
            modelFile.length() >
                10_000_000L
        ) {
            return modelFile
        }

        val tempFile =
            File(
                directory,
                "$MODEL_NAME.download"
            )

        if (tempFile.exists()) {
            tempFile.delete()
        }

        val connection =
            URL(
                MODEL_URL
            )
                .openConnection()
                as HttpURLConnection

        try {

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                30_000

            connection.readTimeout =
                300_000

            connection.instanceFollowRedirects =
                true

            connection.setRequestProperty(
                "User-Agent",
                "CaptionsFA/1.0"
            )

            val code =
                connection.responseCode

            if (code !in 200..299) {
                throw IllegalStateException(
                    "دانلود مدل ناموفق بود: HTTP $code"
                )
            }

            connection.inputStream.use { input ->

                FileOutputStream(
                    tempFile
                ).use { output ->

                    val buffer =
                        ByteArray(
                            64 * 1024
                        )

                    while (true) {

                        val read =
                            input.read(
                                buffer
                            )

                        if (read <= 0) {
                            break
                        }

                        output.write(
                            buffer,
                            0,
                            read
                        )
                    }

                    output.flush()
                }
            }

            if (
                !tempFile.exists() ||
                tempFile.length() <
                    10_000_000L
            ) {
                throw IllegalStateException(
                    "فایل مدل ناقص دانلود شده است"
                )
            }

            if (modelFile.exists()) {
                modelFile.delete()
            }

            if (
                !tempFile.renameTo(
                    modelFile
                )
            ) {

                tempFile.copyTo(
                    modelFile,
                    overwrite = true
                )

                tempFile.delete()
            }

            return modelFile

        } finally {

            connection.disconnect()
        }
    }

    private fun getAudioDuration(
        context: Context,
        uri: Uri
    ): Long {

        val extractor =
            MediaExtractor()

        try {

            val descriptor =
                context.contentResolver
                    .openFileDescriptor(
                        uri,
                        "r"
                    )
                    ?: throw IllegalStateException(
                        "فایل ویدیو قابل خواندن نیست"
                    )

            descriptor.use {
                extractor.setDataSource(
                    it.fileDescriptor
                )
            }

            for (
                index in
                0 until extractor.trackCount
            ) {

                val format =
                    extractor.getTrackFormat(
                        index
                    )

                val mime =
                    format.getString(
                        MediaFormat.KEY_MIME
                    ) ?: ""

                if (
                    mime.startsWith(
                        "audio/"
                    )
                ) {

                    if (
                        format.containsKey(
                            MediaFormat.KEY_DURATION
                        )
                    ) {

                        return (
                            format.getLong(
                                MediaFormat.KEY_DURATION
                            ) / 1000L
                        )
                    }
                }
            }

            return 0L

        } finally {

            extractor.release()
        }
    }

    private fun decodeAudioChunk(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long
    ): FloatArray {

        val extractor =
            MediaExtractor()

        try {

            val descriptor =
                context.contentResolver
                    .openFileDescriptor(
                        uri,
                        "r"
                    )
                    ?: throw IllegalStateException(
                        "فایل ویدیو قابل خواندن نیست"
                    )

            descriptor.use {
                extractor.setDataSource(
                    it.fileDescriptor
                )
            }

            var audioTrack =
                -1

            for (
                index in
                0 until extractor.trackCount
            ) {

                val format =
                    extractor.getTrackFormat(
                        index
                    )

                val mime =
                    format.getString(
                        MediaFormat.KEY_MIME
                    ) ?: ""

                if (
                    mime.startsWith(
                        "audio/"
                    )
                ) {

                    audioTrack =
                        index

                    break
                }
            }

            if (audioTrack < 0) {
                return FloatArray(0)
            }

            extractor.selectTrack(
                audioTrack
            )

            val inputFormat =
                extractor.getTrackFormat(
                    audioTrack
                )

            val mime =
                inputFormat.getString(
                    MediaFormat.KEY_MIME
                )
                    ?: throw IllegalStateException(
                        "فرمت صوتی مشخص نیست"
                    )

            val sourceRate =
                inputFormat.getInteger(
                    MediaFormat.KEY_SAMPLE_RATE
                )

            val sourceChannels =
                inputFormat.getInteger(
                    MediaFormat.KEY_CHANNEL_COUNT
                )

            val decoder =
                MediaCodec.createDecoderByType(
                    mime
                )

            decoder.configure(
                inputFormat,
                null,
                null,
                0
            )

            decoder.start()

            try {

                extractor.seekTo(
                    startMs * 1000L,
                    MediaExtractor
                        .SEEK_TO_CLOSEST_SYNC
                )

                val samples =
                    ArrayList<Float>()

                val bufferInfo =
                    MediaCodec.BufferInfo()

                var inputDone =
                    false

                var outputDone =
                    false

                var pcmEncoding =
                    AudioFormat.ENCODING_PCM_16BIT

                while (!outputDone) {

                    if (!inputDone) {

                        val inputIndex =
                            decoder.dequeueInputBuffer(
                                10_000
                            )

                        if (inputIndex >= 0) {

                            val inputBuffer =
                                decoder.getInputBuffer(
                                    inputIndex
                                )

                            if (
                                inputBuffer != null
                            ) {

                                val sampleTime =
                                    extractor.sampleTime

                                if (
                                    sampleTime < 0 ||
                                    sampleTime >=
                                        endMs * 1000L
                                ) {

                                    decoder.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0,
                                        MediaCodec
                                            .BUFFER_FLAG_END_OF_STREAM
                                    )

                                    inputDone =
                                        true

                                } else {

                                    val size =
                                        extractor.readSampleData(
                                            inputBuffer,
                                            0
                                        )

                                    if (size < 0) {

                                        decoder.queueInputBuffer(
                                            inputIndex,
                                            0,
                                            0,
                                            0,
                                            MediaCodec
                                                .BUFFER_FLAG_END_OF_STREAM
                                        )

                                        inputDone =
                                            true

                                    } else {

                                        decoder.queueInputBuffer(
                                            inputIndex,
                                            0,
                                            size,
                                            sampleTime,
                                            0
                                        )

                                        extractor.advance()
                                    }
                                }
                            }
                        }
                    }

                    val outputIndex =
                        decoder.dequeueOutputBuffer(
                            bufferInfo,
                            10_000
                        )

                    when {

                        outputIndex >= 0 -> {

                            val outputBuffer =
                                decoder.getOutputBuffer(
                                    outputIndex
                                )

                            if (
                                outputBuffer != null &&
                                bufferInfo.size > 0
                            ) {

                                val format =
                                    decoder.outputFormat

                                if (
                                    format.containsKey(
                                        MediaFormat.KEY_PCM_ENCODING
                                    )
                                ) {

                                    pcmEncoding =
                                        format.getInteger(
                                            MediaFormat.KEY_PCM_ENCODING
                                        )
                                }

                                outputBuffer.position(
                                    bufferInfo.offset
                                )

                                outputBuffer.limit(
                                    bufferInfo.offset +
                                        bufferInfo.size
                                )

                                if (
                                    pcmEncoding ==
                                        AudioFormat
                                            .ENCODING_PCM_FLOAT
                                ) {

                                    while (
                                        outputBuffer.remaining()
                                            >=
                                        4 *
                                            sourceChannels
                                    ) {

                                        var mono =
                                            0f

                                        repeat(
                                            sourceChannels
                                        ) {

                                            mono +=
                                                outputBuffer
                                                    .float
                                        }

                                        samples.add(
                                            (
                                                mono /
                                                    sourceChannels
                                            )
                                                .coerceIn(
                                                    -1f,
                                                    1f
                                                )
                                        )
                                    }

                                } else {

                                    while (
                                        outputBuffer.remaining()
                                            >=
                                        2 *
                                            sourceChannels
                                    ) {

                                        var mono =
                                            0f

                                        repeat(
                                            sourceChannels
                                        ) {

                                            val value =
                                                outputBuffer
                                                    .short
                                                    .toInt()

                                            mono +=
                                                value /
                                                    32768f
                                        }

                                        samples.add(
                                            (
                                                mono /
                                                    sourceChannels
                                            )
                                                .coerceIn(
                                                    -1f,
                                                    1f
                                                )
                                        )
                                    }
                                }
                            }

                            decoder.releaseOutputBuffer(
                                outputIndex,
                                false
                            )

                            if (
                                bufferInfo.flags and
                                    MediaCodec
                                        .BUFFER_FLAG_END_OF_STREAM
                                    != 0
                            ) {

                                outputDone =
                                    true
                            }
                        }

                        outputIndex ==
                            MediaCodec
                                .INFO_OUTPUT_FORMAT_CHANGED -> {

                            val format =
                                decoder.outputFormat

                            if (
                                format.containsKey(
                                    MediaFormat.KEY_PCM_ENCODING
                                )
                            ) {

                                pcmEncoding =
                                    format.getInteger(
                                        MediaFormat.KEY_PCM_ENCODING
                                    )
                            }
                        }
                    }
                }

                return resample(
                    source =
                        samples,
                    sourceRate =
                        sourceRate,
                    targetRate =
                        16_000
                )

            } finally {

                try {
                    decoder.stop()
                } catch (_: Exception) {
                }

                decoder.release()
            }

        } finally {

            extractor.release()
        }
    }

    private fun resample(
        source: List<Float>,
        sourceRate: Int,
        targetRate: Int
    ): FloatArray {

        if (source.isEmpty()) {
            return FloatArray(0)
        }

        if (
            sourceRate <= 0 ||
            targetRate <= 0
        ) {
            return FloatArray(0)
        }

        if (
            sourceRate ==
                targetRate
        ) {
            return source.toFloatArray()
        }

        val outputSize =
            (
                source.size.toDouble() *
                    targetRate.toDouble() /
                    sourceRate.toDouble()
            )
                .roundToInt()
                .coerceAtLeast(1)

        val output =
            FloatArray(
                outputSize
            )

        val ratio =
            sourceRate.toDouble() /
                targetRate.toDouble()

        for (
            index in
            output.indices
        ) {

            val position =
                index * ratio

            val left =
                position
                    .toInt()
                    .coerceIn(
                        0,
                        source.lastIndex
                    )

            val right =
                (
                    left + 1
                )
                    .coerceAtMost(
                        source.lastIndex
                    )

            val fraction =
                (
                    position -
                        left.toDouble()
                ).toFloat()

            output[index] =
                source[left] +
                    (
                        source[right] -
                            source[left]
                    ) *
                    fraction
        }

        return output
    }
}
