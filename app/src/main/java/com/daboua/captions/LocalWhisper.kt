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

    /*
     * مدل Base برای فارسی از Tiny مناسب‌تر است
     * و هنوز برای موبایل اندازه قابل‌قبولی دارد.
     *
     * حدود 60MB.
     */
    private const val MODEL_NAME =
        "ggml-base-q5_1.bin"

    private const val MODEL_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin"

    fun transcribeVideo(
        context: Context,
        uri: Uri,
        threads: Int = 4
    ): List<LocalTranscriptSegment> {

        val modelFile =
            ensureModel(context)

        val audio =
            decodeAudioTo16kMono(
                context,
                uri
            )

        if (audio.isEmpty()) {
            throw IllegalStateException(
                "صدای قابل پردازش از ویدیو پیدا نشد"
            )
        }

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

            val result =
                WhisperNative.transcribe(
                    context = nativeContext,
                    audio = audio,
                    numThreads =
                        threads.coerceIn(
                            1,
                            8
                        )
                )

            if (result != 0) {
                throw IllegalStateException(
                    "پردازش صوت توسط Whisper ناموفق بود: $result"
                )
            }

            val count =
                WhisperNative.getSegmentCount(
                    nativeContext
                )

            val rawSegments =
                ArrayList<LocalTranscriptSegment>(
                    count
                )

            for (index in 0 until count) {

                val text =
                    WhisperNative
                        .getSegmentText(
                            nativeContext,
                            index
                        )
                        .cleanCaptionText()

                if (text.isBlank()) {
                    continue
                }

                /*
                 * whisper.cpp timestamp:
                 * هر واحد = 10ms
                 */
                val start =
                    WhisperNative
                        .getSegmentStart(
                            nativeContext,
                            index
                        ) * 10L

                val end =
                    WhisperNative
                        .getSegmentEnd(
                            nativeContext,
                            index
                        ) * 10L

                if (
                    end > start &&
                    end - start >= 150L
                ) {

                    rawSegments.add(
                        LocalTranscriptSegment(
                            text = text,
                            startTime =
                                start.coerceAtLeast(0L),
                            endTime =
                                end.coerceAtLeast(
                                    start + 150L
                                )
                        )
                    )
                }
            }

            return normalizeSegments(
                rawSegments
            )

        } finally {

            WhisperNative.free(
                nativeContext
            )
        }
    }

    private fun String.cleanCaptionText(): String {

        return this
            .replace(
                Regex(
                    "\\s+"
                ),
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

    private fun normalizeSegments(
        source: List<LocalTranscriptSegment>
    ): List<LocalTranscriptSegment> {

        if (source.isEmpty()) {
            return emptyList()
        }

        val sorted =
            source
                .sortedBy {
                    it.startTime
                }

        val result =
            ArrayList<LocalTranscriptSegment>()

        var previousEnd = 0L

        for (segment in sorted) {

            var start =
                segment.startTime

            var end =
                segment.endTime

            if (start < previousEnd) {
                start = previousEnd
            }

            if (end <= start) {
                continue
            }

            /*
             * فاصله‌های غیرمنطقی بین دو Segment
             * را دستکاری نمی‌کنیم؛ timestamp واقعی
             * Whisper حفظ می‌شود.
             */

            result.add(
                LocalTranscriptSegment(
                    text = segment.text.trim(),
                    startTime = start,
                    endTime = end
                )
            )

            previousEnd = end
        }

        return result
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

        /*
         * Base Q5 حدود 60MB است.
         */
        if (
            modelFile.exists() &&
            modelFile.length() > 50_000_000L
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

            val responseCode =
                connection.responseCode

            if (
                responseCode !in 200..299
            ) {
                throw IllegalStateException(
                    "دانلود مدل ناموفق بود: HTTP $responseCode"
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
                    50_000_000L
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

    private fun decodeAudioTo16kMono(
        context: Context,
        uri: Uri
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
                throw IllegalStateException(
                    "این ویدیو ترک صوتی ندارد"
                )
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
                        "فرمت صوتی ویدیو مشخص نیست"
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

                val samples =
                    ArrayList<Float>()

                var sourceSampleRate =
                    inputFormat.getInteger(
                        MediaFormat.KEY_SAMPLE_RATE
                    )

                var sourceChannels =
                    inputFormat.getInteger(
                        MediaFormat.KEY_CHANNEL_COUNT
                    )

                var pcmEncoding =
                    AudioFormat.ENCODING_PCM_16BIT

                var inputDone =
                    false

                var outputDone =
                    false

                val bufferInfo =
                    MediaCodec.BufferInfo()

                while (!outputDone) {

                    if (!inputDone) {

                        val inputIndex =
                            decoder.dequeueInputBuffer(
                                10_000
                            )

                        if (
                            inputIndex >= 0
                        ) {

                            val inputBuffer =
                                decoder.getInputBuffer(
                                    inputIndex
                                )

                            if (
                                inputBuffer != null
                            ) {

                                val sampleSize =
                                    extractor.readSampleData(
                                        inputBuffer,
                                        0
                                    )

                                if (
                                    sampleSize < 0
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

                                    decoder.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        0
                                    )

                                    extractor.advance()
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

                                /*
                                 * بعضی دستگاه‌ها اطلاعات PCM
                                 * را در outputFormat اعلام می‌کنند.
                                 */
                                val outputFormat =
                                    decoder.outputFormat

                                if (
                                    outputFormat.containsKey(
                                        MediaFormat.KEY_SAMPLE_RATE
                                    )
                                ) {

                                    sourceSampleRate =
                                        outputFormat.getInteger(
                                            MediaFormat.KEY_SAMPLE_RATE
                                        )
                                }

                                if (
                                    outputFormat.containsKey(
                                        MediaFormat.KEY_CHANNEL_COUNT
                                    )
                                ) {

                                    sourceChannels =
                                        outputFormat.getInteger(
                                            MediaFormat.KEY_CHANNEL_COUNT
                                        )
                                }

                                if (
                                    outputFormat.containsKey(
                                        MediaFormat.KEY_PCM_ENCODING
                                    )
                                ) {

                                    pcmEncoding =
                                        outputFormat.getInteger(
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

                                when (
                                    pcmEncoding
                                ) {

                                    AudioFormat.ENCODING_PCM_FLOAT -> {

                                        while (
                                            outputBuffer.remaining()
                                                >=
                                            4 * sourceChannels
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

                                            mono /=
                                                sourceChannels

                                            samples.add(
                                                mono.coerceIn(
                                                    -1f,
                                                    1f
                                                )
                                            )
                                        }
                                    }

                                    else -> {

                                        while (
                                            outputBuffer.remaining()
                                                >=
                                            2 * sourceChannels
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
                                                    value / 32768f
                                            }

                                            mono /=
                                                sourceChannels

                                            samples.add(
                                                mono.coerceIn(
                                                    -1f,
                                                    1f
                                                )
                                            )
                                        }
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
                                    MediaFormat.KEY_SAMPLE_RATE
                                )
                            ) {

                                sourceSampleRate =
                                    format.getInteger(
                                        MediaFormat.KEY_SAMPLE_RATE
                                    )
                            }

                            if (
                                format.containsKey(
                                    MediaFormat.KEY_CHANNEL_COUNT
                                )
                            ) {

                                sourceChannels =
                                    format.getInteger(
                                        MediaFormat.KEY_CHANNEL_COUNT
                                    )
                            }

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
                        sourceSampleRate,
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

        if (sourceRate <= 0) {
            throw IllegalStateException(
                "Sample rate صوت نامعتبر است"
            )
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
            i in
            output.indices
        ) {

            val position =
                i * ratio

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

            output[i] =
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
