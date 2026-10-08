package com.daboua.captions

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/* ------------------------------------------------------------------ */
/* تنظیمات سرور (ذخیره در SharedPreferences)                             */
/* ------------------------------------------------------------------ */

object ServerSettings {

    private const val PREFS = "server_settings"

    fun url(context: Context): String =
        prefs(context).getString("url", "") ?: ""

    fun apiKey(context: Context): String =
        prefs(context).getString("key", "") ?: ""

    fun wordsPerCaption(context: Context): Int =
        prefs(context).getInt("words", 4)

    fun save(
        context: Context,
        url: String,
        key: String,
        words: Int
    ) {
        prefs(context).edit()
            .putString("url", url.trim())
            .putString("key", key.trim())
            .putInt("words", words.coerceIn(1, 12))
            .apply()
    }

    fun isConfigured(context: Context): Boolean =
        url(context).isNotBlank()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/* ------------------------------------------------------------------ */
/* تشخیص گفتار روی سرور                                                  */
/* ------------------------------------------------------------------ */

object RemoteTranscriber {

    private const val SAMPLE_RATE = 16_000
    private const val STEP_MS = 60_000L

    /**
     * صدای ویدیو را جدا می‌کند، به سرور می‌فرستد و کپشن‌های آماده را برمی‌گرداند.
     * اگر خطایی رخ دهد استثنا پرتاب می‌شود تا برنامه به حالت آفلاین برگردد.
     */
    fun transcribe(
        context: Context,
        uri: Uri,
        wordsPerCaption: Int
    ): List<LocalTranscriptSegment> {

        val wav = extractWav(context, uri)

        try {
            val json = upload(context, wav)
            return parse(json, wordsPerCaption.coerceIn(1, 12))
        } finally {
            wav.delete()
        }
    }

    /* ---------------------------- جدا کردن صدا ---------------------------- */

    private fun extractWav(context: Context, uri: Uri): File {

        val duration = LocalWhisper.audioDurationMs(context, uri)

        if (duration <= 0L) {
            throw IllegalStateException("صدای قابل پردازش از ویدیو پیدا نشد")
        }

        val file = File(context.cacheDir, "upload_${UUID.randomUUID()}.wav")

        RandomAccessFile(file, "rw").use { out ->

            out.write(ByteArray(44)) // جای هدر WAV

            var start = 0L
            var pcmBytes = 0L

            while (start < duration) {

                val end = (start + STEP_MS).coerceAtMost(duration)

                val samples = LocalWhisper.decodeChunk(
                    context, uri, start, end
                )

                val buffer = ByteArray(samples.size * 2)

                for (i in samples.indices) {
                    val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
                    buffer[i * 2] = (v and 0xFF).toByte()
                    buffer[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }

                out.write(buffer)
                pcmBytes += buffer.size
                start = end
            }

            writeWavHeader(out, pcmBytes)
        }

        return file
    }

    private fun writeWavHeader(out: RandomAccessFile, pcmBytes: Long) {

        fun le32(v: Long) = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte()
        )

        fun le16(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte()
        )

        out.seek(0)
        out.write("RIFF".toByteArray())
        out.write(le32(36 + pcmBytes))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(le32(16))
        out.write(le16(1))                       // PCM
        out.write(le16(1))                       // mono
        out.write(le32(SAMPLE_RATE.toLong()))
        out.write(le32(SAMPLE_RATE * 2L))        // byte rate
        out.write(le16(2))                       // block align
        out.write(le16(16))                      // bits
        out.write("data".toByteArray())
        out.write(le32(pcmBytes))
    }

    /* ------------------------------ ارسال ------------------------------ */

    private fun upload(context: Context, wav: File): String {

        var base = ServerSettings.url(context).trim().trimEnd('/')

        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://$base"
        }

        val boundary = "----CaptionsFa${UUID.randomUUID()}"

        val connection = URL("$base/v1/transcribe").openConnection()
            as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 180_000

            connection.setRequestProperty(
                "Content-Type",
                "multipart/form-data; boundary=$boundary"
            )

            val key = ServerSettings.apiKey(context)
            if (key.isNotBlank()) {
                connection.setRequestProperty("X-API-Key", key)
            }

            connection.setFixedLengthStreamingMode(
                headPart(boundary).size + wav.length() +
                    tailPart(boundary).size
            )

            connection.outputStream.use { os ->
                os.write(headPart(boundary))
                wav.inputStream().use { it.copyTo(os, 64 * 1024) }
                os.write(tailPart(boundary))
                os.flush()
            }

            val code = connection.responseCode

            if (code !in 200..299) {
                throw IllegalStateException("خطای سرور: $code")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }

        } finally {
            connection.disconnect()
        }
    }

    private fun headPart(boundary: String): ByteArray =
        (
            "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"file\"; " +
                "filename=\"audio.wav\"\r\n" +
                "Content-Type: audio/wav\r\n\r\n"
            ).toByteArray()

    private fun tailPart(boundary: String): ByteArray =
        (
            "\r\n--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"language\"\r\n\r\n" +
                "fa\r\n" +
                "--$boundary--\r\n"
            ).toByteArray()

    /* --------------------- تبدیل نتیجه به کپشن‌ها --------------------- */

    private fun parse(
        body: String,
        wordsPerCaption: Int
    ): List<LocalTranscriptSegment> {

        val segments = JSONObject(body).optJSONArray("segments")
            ?: return emptyList()

        val result = mutableListOf<LocalTranscriptSegment>()

        for (i in 0 until segments.length()) {

            val seg = segments.getJSONObject(i)
            val words = seg.optJSONArray("words")

            if (words == null || words.length() == 0) {
                val text = seg.optString("text").trim()
                if (text.isNotEmpty()) {
                    result.add(
                        LocalTranscriptSegment(
                            text = text,
                            startTime = (seg.optDouble("start") * 1000).toLong(),
                            endTime = (seg.optDouble("end") * 1000).toLong()
                        )
                    )
                }
                continue
            }

            var index = 0

            while (index < words.length()) {

                val last = minOf(index + wordsPerCaption, words.length())

                val parts = (index until last).map {
                    words.getJSONObject(it).optString("word").trim()
                }.filter { it.isNotEmpty() }

                if (parts.isNotEmpty()) {

                    val first = words.getJSONObject(index)
                    val tail = words.getJSONObject(last - 1)

                    val start = (first.optDouble("start") * 1000).toLong()
                    val end = (tail.optDouble("end") * 1000).toLong()
                        .coerceAtLeast(start + 200L)

                    result.add(
                        LocalTranscriptSegment(
                            text = parts.joinToString(" "),
                            startTime = start,
                            endTime = end
                        )
                    )
                }

                index = last
            }
        }

        return result
    }
}
