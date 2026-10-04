package com.daboua.captions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        setContent {
            CaptionsFaApp()
        }
    }
}

enum class CaptionAnimation {
    NONE,
    FADE,
    SLIDE_UP,
    SLIDE_DOWN,
    SLIDE_LEFT,
    SLIDE_RIGHT
}

data class CaptionStyle(
    val fontSize: Float = 28f,
    val textColor: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x99000000,
    val bold: Boolean = false,
    val alignment: String = "CENTER",
    val position: Float = 0.82f,
    val highlightEnabled: Boolean = false,
    val highlightColor: Long = 0xFFFFD54F,
    val animation: CaptionAnimation =
        CaptionAnimation.NONE,
    val animationDuration: Int = 350
)

data class Caption(
    val id: Int,
    val text: String,
    val startTime: Long,
    val endTime: Long,
    val style: CaptionStyle =
        CaptionStyle()
)

private data class LoadedProject(
    val captions: List<Caption>,
    val videoUri: Uri?,
    val selectedCaptionId: Int?
)

@Composable
fun CaptionsFaApp() {

    val context =
        androidx.compose.ui.platform.LocalContext
            .current

    val captions =
        remember {
            mutableStateListOf<Caption>()
        }

    var videoUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var currentPosition by remember {
        mutableLongStateOf(0L)
    }

    var videoDuration by remember {
        mutableLongStateOf(0L)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var selectedCaptionId by remember {
        mutableStateOf<Int?>(null)
    }

    var nextCaptionId by remember {
        mutableStateOf(1)
    }

    var message by remember {
        mutableStateOf("")
    }

    var isTranscribing by remember {
        mutableStateOf(false)
    }

    var playerReference by remember {
        mutableStateOf<ExoPlayer?>(null)
    }

    /*
     * هر بار ویدیو عوض شود این عدد تغییر می‌کند.
     *
     * بنابراین اگر پردازش ویدیوی قبلی هنوز در پس‌زمینه
     * باشد، نتیجه‌اش وارد ویدیوی جدید نمی‌شود.
     */
    val transcriptionGeneration =
        remember {
            AtomicInteger(0)
        }

    val mainHandler =
        remember {
            Handler(
                Looper.getMainLooper()
            )
        }

    /*
     * انتخاب ویدیو
     *
     * نکته مهم:
     * دیگر لازم نیست بعد از انتخاب ویدیو دکمه‌ای بزنی.
     * کپشن‌گذاری خودش شروع می‌شود.
     */
    val videoPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    context.contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )

                } catch (_: Exception) {
                }

                /*
                 * نسل جدید پردازش.
                 */
                val generation =
                    transcriptionGeneration
                        .incrementAndGet()

                videoUri =
                    uri

                currentPosition =
                    0L

                videoDuration =
                    0L

                isPlaying =
                    false

                playerReference =
                    null

                captions.clear()

                selectedCaptionId =
                    null

                nextCaptionId =
                    1

                isTranscribing =
                    true

                message =
                    "ویدیو آماده شد؛ کپشن‌گذاری همزمان شروع شد..."

                /*
                 * پردازش در Thread جدا.
                 *
                 * بنابراین UI و ویدیو قفل نمی‌شوند.
                 */
                thread {

                    try {

                        LocalWhisper
                            .transcribeVideoStreaming(

                                context =
                                    context,

                                uri =
                                    uri,

                                threads =
                                    4,

                                onSegment = {
                                    segment ->

                                    mainHandler.post {

                                        /*
                                         * اگر کاربر ویدیوی دیگری
                                         * انتخاب کرده باشد، نتیجه
                                         * این ویدیو دیگر معتبر نیست.
                                         */
                                        if (
                                            transcriptionGeneration
                                                .get() !=
                                            generation
                                        ) {
                                            return@post
                                        }

                                        /*
                                         * جلوگیری از کپشن تکراری.
                                         */
                                        val duplicate =
                                            captions.any {

                                                kotlin.math
                                                    .abs(
                                                        it.startTime -
                                                            segment.startTime
                                                    ) < 450L &&
                                                    it.text
                                                        .trim()
                                                        .equals(
                                                            segment.text
                                                                .trim(),
                                                            ignoreCase = true
                                                        )
                                            }

                                        if (
                                            duplicate
                                        ) {
                                            return@post
                                        }

                                        /*
                                         * کپشن قبلی را اگر با کپشن
                                         * جدید همپوشانی دارد کوتاه می‌کنیم.
                                         */
                                        val previous =
                                            captions
                                                .filter {
                                                    it.startTime <=
                                                        segment.startTime
                                                }
                                                .maxByOrNull {
                                                    it.startTime
                                                }

                                        if (
                                            previous != null &&
                                            previous.endTime >
                                                segment.startTime
                                        ) {

                                            updateCaption(
                                                captions,
                                                previous.id
                                            ) {

                                                it.copy(
                                                    endTime =
                                                        (
                                                            segment.startTime -
                                                                40L
                                                        ).coerceAtLeast(
                                                            it.startTime +
                                                                150L
                                                        )
                                                )
                                            }
                                        }

                                        val newId =
                                            nextCaptionId

                                        nextCaptionId++

                                        captions.add(
                                            Caption(
                                                id =
                                                    newId,
                                                text =
                                                    segment.text,
                                                startTime =
                                                    segment.startTime,
                                                endTime =
                                                    segment.endTime
                                            )
                                        )

                                        /*
                                         * مرتب‌سازی زمانی.
                                         */
                                        val sorted =
                                            captions
                                                .sortedBy {
                                                    it.startTime
                                                }

                                        captions.clear()

                                        captions.addAll(
                                            sorted
                                        )

                                        selectedCaptionId =
                                            newId

                                        message =
                                            "کپشن جدید ساخته شد: " +
                                                segment.text
                                    }
                                },

                                onProgress = {
                                    done,
                                    total ->

                                    mainHandler.post {

                                        if (
                                            transcriptionGeneration
                                                .get() !=
                                            generation
                                        ) {
                                            return@post
                                        }

                                        val percent =
                                            (
                                                done.toFloat() /
                                                    total
                                                        .coerceAtLeast(
                                                            1L
                                                        )
                                                        .toFloat()
                                            )
                                                .coerceIn(
                                                    0f,
                                                    1f
                                                ) * 100f

                                        message =
                                            "کپشن‌گذاری زنده: " +
                                                "${percent.toInt()}٪"
                                    }
                                }
                            )

                        mainHandler.post {

                            if (
                                transcriptionGeneration
                                    .get() !=
                                generation
                            ) {
                                return@post
                            }

                            isTranscribing =
                                false

                            message =
                                if (
                                    captions.isEmpty()
                                ) {

                                    "گفتاری برای تشخیص پیدا نشد"

                                } else {

                                    "${captions.size} کپشن ساخته شد"
                                }
                        }

                    } catch (e: Exception) {

                        mainHandler.post {

                            if (
                                transcriptionGeneration
                                    .get() !=
                                generation
                            ) {
                                return@post
                            }

                            isTranscribing =
                                false

                            message =
                                e.message
                                    ?: "خطا در کپشن‌گذاری"
                        }
                    }
                }
            }
        }

    /*
     * ورود SRT
     */
    val srtImportPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    val content =
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }

                    if (
                        content != null
                    ) {

                        val parsed =
                            parseSrt(
                                content
                            )

                        captions.clear()

                        var id =
                            1

                        parsed.forEach { item ->

                            captions.add(
                                Caption(
                                    id =
                                        id++,
                                    text =
                                        item.text,
                                    startTime =
                                        item.startTime,
                                    endTime =
                                        item.endTime
                                )
                            )
                        }

                        nextCaptionId =
                            id

                        selectedCaptionId =
                            captions
                                .firstOrNull()
                                ?.id

                        message =
                            "${parsed.size} کپشن وارد شد"
                    }

                } catch (_: Exception) {

                    message =
                        "خطا در خواندن SRT"
                }
            }
        }

    /*
     * خروجی SRT
     */
    val srtExportPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "text/plain"
            )
        ) { uri ->

            if (uri != null) {

                try {

                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use {

                            it.write(
                                buildSrt(
                                    captions
                                )
                            )
                        }

                    message =
                        "SRT ذخیره شد"

                } catch (_: Exception) {

                    message =
                        "خطا در ذخیره SRT"
                }
            }
        }

    /*
     * ذخیره پروژه
     */
    val projectSavePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/json"
            )
        ) { uri ->

            if (uri != null) {

                try {

                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use {

                            it.write(
                                buildProjectJson(
                                    captions,
                                    videoUri,
                                    selectedCaptionId
                                )
                            )
                        }

                    message =
                        "پروژه ذخیره شد"

                } catch (_: Exception) {

                    message =
                        "خطا در ذخیره پروژه"
                }
            }
        }

    /*
     * باز کردن پروژه
     */
    val projectOpenPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {

                    val content =
                        context.contentResolver
                            .openInputStream(uri)
                            ?.bufferedReader()
                            ?.use {
                                it.readText()
                            }

                    if (
                        content != null
                    ) {

                        val project =
                            parseProjectJson(
                                content
                            )

                        captions.clear()

                        captions.addAll(
                            project.captions
                                .sortedBy {
                                    it.startTime
                                }
                        )

                        nextCaptionId =
                            (
                                captions
                                    .maxOfOrNull {
                                        it.id
                                    }
                                    ?: 0
                            ) + 1

                        selectedCaptionId =
                            project
                                .selectedCaptionId
                                ?.takeIf { id ->
                                    captions.any {
                                        it.id == id
                                    }
                                }
                                ?: captions
                                    .firstOrNull()
                                    ?.id

                        videoUri =
                            project.videoUri

                        currentPosition =
                            0L

                        videoDuration =
                            0L

                        isPlaying =
                            false

                        playerReference =
                            null

                        /*
                         * پروژه باز شده است.
                         * برای جلوگیری از پردازش ناخواسته،
                         * خودکار Whisper را روی پروژه ذخیره‌شده
                         * اجرا نمی‌کنیم.
                         */
                        transcriptionGeneration
                            .incrementAndGet()

                        isTranscribing =
                            false

                        message =
                            "پروژه با ${captions.size} کپشن باز شد"
                    }

                } catch (_: Exception) {

                    message =
                        "فایل پروژه معتبر نیست"
                }
            }
        }

    /*
     * وضعیت Player
     */
    LaunchedEffect(
        playerReference
    ) {

        val player =
            playerReference
                ?: return@LaunchedEffect

        while (true) {

            currentPosition =
                player.currentPosition
                    .coerceAtLeast(
                        0L
                    )

            videoDuration =
                player.duration
                    .coerceAtLeast(
                        0L
                    )

            isPlaying =
                player.isPlaying

            delay(100)
        }
    }

    /*
     * کپشن فعال بر اساس زمان ویدیو.
     */
    val activeCaption =
        captions.firstOrNull {

            currentPosition >=
                it.startTime &&
                currentPosition <=
                it.endTime
        }

    LaunchedEffect(
        activeCaption?.id
    ) {

        if (
            activeCaption != null
        ) {

            selectedCaptionId =
                activeCaption.id
        }
    }

    MaterialTheme {

        Surface(
            modifier =
                Modifier.fillMaxSize(),
            color =
                Color(0xFF101010)
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp)
            ) {

                Text(
                    text =
                        "Captions FA",
                    color =
                        Color.White,
                    fontSize =
                        24.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text =
                        "تبدیل خودکار گفتار ویدئو به کپشن فارسی",
                    color =
                        Color.LightGray,
                    fontSize =
                        13.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    Button(
                        onClick = {

                            videoPicker.launch(
                                arrayOf(
                                    "video/*"
                                )
                            )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {

                        Text(
                            "انتخاب ویدیو"
                        )
                    }

                    OutlinedButton(
                        onClick = {

                            srtImportPicker.launch(
                                arrayOf(
                                    "text/plain",
                                    "application/x-subrip",
                                    "*/*"
                                )
                            )
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {

                        Text(
                            "ورود SRT"
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                /*
                 * این دکمه برای اجرای دوباره
                 * پردازش روی همان ویدیو نگه داشته شده.
                 */
                Button(
                    onClick = {

                        val uri =
                            videoUri
                                ?: return@Button

                        if (
                            isTranscribing
                        ) {

                            message =
                                "کپشن‌گذاری در حال انجام است..."

                            return@Button
                        }

                        val generation =
                            transcriptionGeneration
                                .incrementAndGet()

                        captions.clear()

                        nextCaptionId =
                            1

                        selectedCaptionId =
                            null

                        isTranscribing =
                            true

                        message =
                            "بازسازی کپشن‌ها شروع شد..."

                        thread {

                            try {

                                LocalWhisper
                                    .transcribeVideoStreaming(

                                        context =
                                            context,

                                        uri =
                                            uri,

                                        threads =
                                            4,

                                        onSegment = {
                                            segment ->

                                            mainHandler.post {

                                                if (
                                                    transcriptionGeneration
                                                        .get() !=
                                                    generation
                                                ) {
                                                    return@post
                                                }

                                                val duplicate =
                                                    captions.any {

                                                        kotlin.math
                                                            .abs(
                                                                it.startTime -
                                                                    segment.startTime
                                                            ) < 450L &&
                                                            it.text
                                                                .trim() ==
                                                            segment.text
                                                                .trim()
                                                    }

                                                if (
                                                    duplicate
                                                ) {
                                                    return@post
                                                }

                                                val id =
                                                    nextCaptionId

                                                nextCaptionId++

                                                captions.add(
                                                    Caption(
                                                        id =
                                                            id,
                                                        text =
                                                            segment.text,
                                                        startTime =
                                                            segment.startTime,
                                                        endTime =
                                                            segment.endTime
                                                    )
                                                )

                                                captions.sortBy {
                                                    it.startTime
                                                }

                                                message =
                                                    "کپشن جدید: " +
                                                        segment.text
                                            }
                                        },

                                        onProgress = {
                                            done,
                                            total ->

                                            mainHandler.post {

                                                if (
                                                    transcriptionGeneration
                                                        .get() !=
                                                    generation
                                                ) {
                                                    return@post
                                                }

                                                val percent =
                                                    (
                                                        done.toFloat() /
                                                            total
                                                                .coerceAtLeast(
                                                                    1L
                                                                )
                                                                .toFloat()
                                                    )
                                                        .coerceIn(
                                                            0f,
                                                            1f
                                                        ) *
                                                        100f

                                                message =
                                                    "در حال ساخت کپشن: " +
                                                        "${percent.toInt()}٪"
                                            }
                                        }
                                    )

                                mainHandler.post {

                                    if (
                                        transcriptionGeneration
                                            .get() !=
                                        generation
                                    ) {
                                        return@post
                                    }

                                    isTranscribing =
                                        false

                                    message =
                                        "${captions.size} کپشن ساخته شد"
                                }

                            } catch (
                                e: Exception
                            ) {

                                mainHandler.post {

                                    if (
                                        transcriptionGeneration
                                            .get() !=
                                        generation
                                    ) {
                                        return@post
                                    }

                                    isTranscribing =
                                        false

                                    message =
                                        e.message
                                            ?: "خطا در ساخت کپشن"
                                }
                            }
                        }
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    enabled =
                        !isTranscribing &&
                            videoUri != null
                ) {

                    Text(
                        if (
                            isTranscribing
                        )
                            "⏳ کپشن‌گذاری همزمان..."
                        else
                            "🔄 بازسازی کپشن‌ها"
                    )
                }

                if (
                    isTranscribing
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(
                                4.dp
                            )
                    )

                    Text(
                        text =
                            "ویدیو متوقف نمی‌شود؛ کپشن‌ها همزمان و مرحله‌به‌مرحله ساخته می‌شوند.",
                        color =
                            Color(0xFFFFD54F),
                        fontSize =
                            11.sp,
                        modifier =
                            Modifier.fillMaxWidth(),
                        textAlign =
                            TextAlign.Center
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(
                                rememberScrollState()
                            ),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    OutlinedButton(
                        onClick = {

                            srtExportPicker.launch(
                                "captions.srt"
                            )
                        }
                    ) {

                        Text(
                            "خروجی SRT"
                        )
                    }

                    OutlinedButton(
                        onClick = {

                            projectSavePicker.launch(
                                "captions_project.json"
                            )
                        }
                    ) {

                        Text(
                            "ذخیره پروژه"
                        )
                    }

                    OutlinedButton(
                        onClick = {

                            projectOpenPicker.launch(
                                arrayOf(
                                    "application/json",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        }
                    ) {

                        Text(
                            "باز کردن پروژه"
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                videoUri?.let { uri ->

                    VideoPreview(
                        uri =
                            uri,
                        currentPosition =
                            currentPosition,
                        activeCaption =
                            activeCaption,
                        onPlayerReady = {
                            playerReference =
                                it
                        }
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Timeline(
                        captions =
                            captions,
                        currentPosition =
                            currentPosition,
                        duration =
                            videoDuration,
                        onPositionClick = {
                            playerReference
                                ?.seekTo(it)
                        }
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.Center
                    ) {

                        Button(
                            onClick = {

                                playerReference
                                    ?.seekTo(
                                        (
                                            currentPosition -
                                                5000L
                                        ).coerceAtLeast(
                                            0L
                                        )
                                    )
                            }
                        ) {

                            Text(
                                "-5s"
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Button(
                            onClick = {

                                val player =
                                    playerReference
                                        ?: return@Button

                                if (
                                    player.isPlaying
                                ) {

                                    player.pause()

                                } else {

                                    player.play()
                                }
                            }
                        ) {

                            Text(
                                if (
                                    isPlaying
                                )
                                    "⏸ توقف"
                                else
                                    "▶ پخش"
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Button(
                            onClick = {

                                playerReference
                                    ?.seekTo(
                                        (
                                            currentPosition +
                                                5000L
                                        ).coerceAtMost(
                                            videoDuration
                                        )
                                    )
                            }
                        ) {

                            Text(
                                "+5s"
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            "${formatTime(currentPosition)} / " +
                                formatTime(
                                    videoDuration
                                ),
                        color =
                            Color.LightGray,
                        modifier =
                            Modifier.fillMaxWidth(),
                        textAlign =
                            TextAlign.Center
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )
                }

                if (
                    message.isNotBlank()
                ) {

                    Text(
                        text =
                            message,
                        color =
                            Color(0xFFFFD54F),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical =
                                        4.dp
                                ),
                        textAlign =
                            TextAlign.Center
                    )
                }

                CaptionEditor(
                    captions =
                        captions,
                    selectedCaptionId =
                        selectedCaptionId,

                    onSelect = {
                        selectedCaptionId =
                            it
                    },

                    onAdd = {

                        val start =
                            currentPosition

                        val end =
                            if (
                                videoDuration >
                                    start
                            ) {

                                (
                                    start +
                                        2000L
                                ).coerceAtMost(
                                    videoDuration
                                )

                            } else {

                                start +
                                    2000L
                            }

                        captions.add(
                            Caption(
                                id =
                                    nextCaptionId,
                                text =
                                    "",
                                startTime =
                                    start,
                                endTime =
                                    end
                            )
                        )

                        selectedCaptionId =
                            nextCaptionId

                        nextCaptionId++
                    },

                    onDelete = { id ->

                        captions.removeAll {
                            it.id == id
                        }

                        selectedCaptionId =
                            captions
                                .firstOrNull()
                                ?.id
                    },

                    onUpdate = {
                        id,
                        transform ->

                        updateCaption(
                            captions,
                            id,
                            transform
                        )
                    },

                    onSetStart = { id ->

                        val caption =
                            captions.firstOrNull {
                                it.id == id
                            }

                        if (
                            caption != null &&
                            currentPosition <
                                caption.endTime
                        ) {

                            updateCaption(
                                captions,
                                id
                            ) {

                                it.copy(
                                    startTime =
                                        currentPosition
                                )
                            }

                            message =
                                "شروع ثبت شد"
                        }
                    },

                    onSetEnd = { id ->

                        val caption =
                            captions.firstOrNull {
                                it.id == id
                            }

                        if (
                            caption != null &&
                            currentPosition >
                                caption.startTime
                        ) {

                            updateCaption(
                                captions,
                                id
                            ) {

                                it.copy(
                                    endTime =
                                        currentPosition
                                )
                            }

                            message =
                                "پایان ثبت شد"
                        }
                    },

                    onGoToStart = { id ->

                        captions.firstOrNull {
                            it.id == id
                        }?.let {

                            playerReference
                                ?.seekTo(
                                    it.startTime
                                )
                        }
                    },

                    onGoToEnd = { id ->

                        captions.firstOrNull {
                            it.id == id
                        }?.let {

                            playerReference
                                ?.seekTo(
                                    it.endTime
                                )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VideoPreview(
    uri: Uri,
    currentPosition: Long,
    activeCaption: Caption?,
    onPlayerReady:
        (ExoPlayer) -> Unit
) {

    val context =
        androidx.compose.ui.platform.LocalContext
            .current

    val player =
        remember(uri) {

            ExoPlayer.Builder(
                context
            )
                .build()
                .apply {

                    setMediaItem(
                        MediaItem.fromUri(
                            uri
                        )
                    )

                    prepare()

                    seekTo(
                        currentPosition
                    )

                    /*
                     * مهم:
                     * ویدیو بلافاصله شروع می‌شود.
                     */
                    playWhenReady =
                        true
                }
        }

    DisposableEffect(
        player
    ) {

        onPlayerReady(
            player
        )

        onDispose {

            player.release()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(
                    RoundedCornerShape(
                        12.dp
                    )
                )
    ) {

        AndroidView(
            factory = { ctx ->

                PlayerView(
                    ctx
                ).apply {

                    this.player =
                        player

                    useController =
                        true
                }
            },
            modifier =
                Modifier.fillMaxSize()
        )

        activeCaption?.let {
            caption ->

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal =
                                8.dp
                        ),
                contentAlignment =
                    Alignment.TopCenter
            ) {

                CaptionOverlay(
                    caption =
                        caption,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                180.dp
                            )
                )
            }
        }
    }
}

@Composable
private fun Timeline(
    captions: List<Caption>,
    currentPosition: Long,
    duration: Long,
    onPositionClick:
        (Long) -> Unit
) {

    val safeDuration =
        duration.coerceAtLeast(
            1L
        )

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            text =
                "Timeline",
            color =
                Color.White,
            fontSize =
                14.sp
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .clip(
                        RoundedCornerShape(
                            8.dp
                        )
                    )
                    .background(
                        Color(0xFF252525)
                    )
        ) {

            captions.forEach {
                caption ->

                val startFraction =
                    (
                        caption.startTime
                            .toFloat() /
                            safeDuration
                                .toFloat()
                    ).coerceIn(
                        0f,
                        1f
                    )

                val widthFraction =
                    (
                        (
                            caption.endTime -
                                caption.startTime
                        ).toFloat() /
                            safeDuration
                                .toFloat()
                    ).coerceIn(
                        0.01f,
                        1f
                    )

                Box(
                    modifier =
                        Modifier
                            .offset(
                                x =
                                    (
                                        startFraction *
                                            1000f
                                    ).dp
                            )
                            .fillMaxHeight()
                            .fillMaxWidth(
                                widthFraction
                            )
                            .padding(
                                3.dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    5.dp
                                )
                            )
                            .background(
                                Color(
                                    0xFF6D4C41
                                )
                            )
                )
            }

            val positionFraction =
                (
                    currentPosition
                        .toFloat() /
                        safeDuration
                            .toFloat()
                ).coerceIn(
                    0f,
                    1f
                )

            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .offset(
                            x =
                                (
                                    positionFraction *
                                        1000f
                                ).dp
                        )
                        .background(
                            Color(
                                0xFFFFD54F
                            )
                        )
            )
        }

        Slider(
            value =
                currentPosition
                    .coerceAtMost(
                        safeDuration
                    )
                    .toFloat(),

            onValueChange = {
                onPositionClick(
                    it.toLong()
                )
            },

            valueRange =
                0f..safeDuration
                    .toFloat(),

            modifier =
                Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CaptionEditor(
    captions: List<Caption>,
    selectedCaptionId: Int?,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Int) -> Unit,
    onUpdate:
        (
            Int,
            (Caption) -> Caption
        ) -> Unit,
    onSetStart: (Int) -> Unit,
    onSetEnd: (Int) -> Unit,
    onGoToStart: (Int) -> Unit,
    onGoToEnd: (Int) -> Unit
) {

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    "کپشن‌ها",
                color =
                    Color.White,
                fontSize =
                    18.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Button(
                onClick =
                    onAdd
            ) {

                Text(
                    "＋ افزودن"
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        captions.forEach {
            caption ->

            CaptionCard(
                caption =
                    caption,
                selected =
                    caption.id ==
                        selectedCaptionId,

                onSelect = {
                    onSelect(
                        caption.id
                    )
                },

                onDelete = {
                    onDelete(
                        caption.id
                    )
                },

                onUpdate = {
                    transform ->

                    onUpdate(
                        caption.id,
                        transform
                    )
                },

                onSetStart = {
                    onSetStart(
                        caption.id
                    )
                },

                onSetEnd = {
                    onSetEnd(
                        caption.id
                    )
                },

                onGoToStart = {
                    onGoToStart(
                        caption.id
                    )
                },

                onGoToEnd = {
                    onGoToEnd(
                        caption.id
                    )
                }
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )
        }
    }
}

@Composable
private fun CaptionCard(
    caption: Caption,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onUpdate:
        ((Caption) -> Caption) -> Unit,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onGoToStart: () -> Unit,
    onGoToEnd: () -> Unit
) {

    var text by remember(
        caption.id
    ) {
        mutableStateOf(
            caption.text
        )
    }

    LaunchedEffect(
        caption.text
    ) {

        if (
            text !=
            caption.text
        ) {

            text =
                caption.text
        }
    }

    val style =
        caption.style

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected)
                        Color(
                            0xFF303030
                        )
                    else
                        Color(
                            0xFF1C1C1C
                        )
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        10.dp
                    )
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        "#${caption.id}",
                    color =
                        Color(
                            0xFFFFD54F
                        ),
                    fontSize =
                        13.sp
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                TextButton(
                    onClick =
                        onSelect
                ) {

                    Text(
                        if (selected)
                            "انتخاب شده"
                        else
                            "انتخاب"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                )

                TextButton(
                    onClick =
                        onDelete
                ) {

                    Text(
                        "حذف",
                        color =
                            Color(
                                0xFFFF8A80
                            )
                    )
                }
            }

            OutlinedTextField(
                value =
                    text,

                onValueChange = {
                    value ->

                    text =
                        value

                    onUpdate {

                        it.copy(
                            text =
                                value
                        )
                    }
                },

                label = {
                    Text(
                        "متن کپشن"
                    )
                },

                modifier =
                    Modifier.fillMaxWidth(),

                minLines =
                    2,

                maxLines =
                    5
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Text(
                text =
                    "${formatTime(caption.startTime)} → " +
                        formatTime(
                            caption.endTime
                        ),
                color =
                    Color.LightGray,
                fontSize =
                    12.sp
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    )
            ) {

                OutlinedButton(
                    onClick =
                        onSetStart,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        "ثبت شروع"
                    )
                }

                OutlinedButton(
                    onClick =
                        onSetEnd,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        "ثبت پایان"
                    )
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    )
            ) {

                TextButton(
                    onClick =
                        onGoToStart,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        "برو شروع"
                    )
                }

                TextButton(
                    onClick =
                        onGoToEnd,
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        "برو پایان"
                    )
                }
            }

            Divider(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical =
                                8.dp
                        )
            )

            Text(
                text =
                    "استایل",
                color =
                    Color.White
            )

            Text(
                text =
                    "اندازه فونت: " +
                        style.fontSize
                            .roundToInt(),
                color =
                    Color.LightGray
            )

            Slider(
                value =
                    style.fontSize,

                onValueChange = {
                    value ->

                    onUpdate {

                        it.copy(
                            style =
                                it.style.copy(
                                    fontSize =
                                        value
                                )
                        )
                    }
                },

                valueRange =
                    14f..60f
            )

            TextButton(
                onClick = {

                    onUpdate {

                        it.copy(
                            style =
                                it.style.copy(
                                    bold =
                                        !it.style.bold
                                )
                        )
                    }
                }
            ) {

                Text(
                    if (
                        style.bold
                    )
                        "✓ Bold"
                    else
                        "Bold"
                )
            }

            Text(
                text =
                    "موقعیت عمودی: " +
                        (
                            style.position *
                                100
                        ).roundToInt() +
                        "%",
                color =
                    Color.LightGray
            )

            Slider(
                value =
                    style.position,

                onValueChange = {
                    value ->

                    onUpdate {

                        it.copy(
                            style =
                                it.style.copy(
                                    position =
                                        value
                                )
                        )
                    }
                },

                valueRange =
                    0.05f..0.95f
            )

            Text(
                text =
                    "چیدمان",
                color =
                    Color.LightGray
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
            ) {

                listOf(
                    "LEFT",
                    "CENTER",
                    "RIGHT"
                ).forEach {
                    alignment ->

                    TextButton(
                        onClick = {

                            onUpdate {

                                it.copy(
                                    style =
                                        it.style.copy(
                                            alignment =
                                                alignment
                                        )
                                )
                            }
                        }
                    ) {

                        Text(
                            if (
                                style.alignment ==
                                    alignment
                            )
                                "✓ $alignment"
                            else
                                alignment
                        )
                    }
                }
            }

            Text(
                text =
                    "Animation",
                color =
                    Color.LightGray
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        )
            ) {

                CaptionAnimation
                    .entries
                    .forEach {
                        animation ->

                        TextButton(
                            onClick = {

                                onUpdate {

                                    it.copy(
                                        style =
                                            it.style.copy(
                                                animation =
                                                    animation
                                            )
                                    )
                                }
                            }
                        ) {

                            Text(
                                if (
                                    style.animation ==
                                        animation
                                )
                                    "✓ ${animation.name}"
                                else
                                    animation.name
                            )
                        }
                    }
            }

            CaptionOverlay(
                caption =
                    caption,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            120.dp
                        )
            )
        }
    }
}

@Composable
private fun CaptionOverlay(
    caption: Caption,
    modifier: Modifier =
        Modifier
) {

    val style =
        caption.style

    val horizontalAlignment =
        when (
            style.alignment
        ) {

            "LEFT" ->
                Alignment.CenterStart

            "RIGHT" ->
                Alignment.CenterEnd

            else ->
                Alignment.Center
        }

    val textAlign =
        when (
            style.alignment
        ) {

            "LEFT" ->
                TextAlign.Left

            "RIGHT" ->
                TextAlign.Right

            else ->
                TextAlign.Center
        }

    Box(
        modifier =
            modifier.clip(
                RoundedCornerShape(
                    8.dp
                )
            )
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top =
                            (
                                style.position *
                                    80
                            ).dp
                    ),
            contentAlignment =
                horizontalAlignment
        ) {

            AnimatedVisibility(
                visible =
                    true,

                enter =
                    animationEnter(
                        style.animation,
                        style.animationDuration
                    ),

                exit =
                    animationExit(
                        style.animation,
                        style.animationDuration
                    )
            ) {

                Text(
                    text =
                        caption.text,

                    color =
                        Color(
                            style.textColor
                        ),

                    fontSize =
                        style.fontSize.sp,

                    fontWeight =
                        if (
                            style.bold
                        )
                            FontWeight.Bold
                        else
                            FontWeight.Normal,

                    textAlign =
                        textAlign,

                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(
                                    6.dp
                                )
                            )
                            .background(
                                Color(
                                    style.backgroundColor
                                )
                            )
                            .padding(
                                horizontal =
                                    10.dp,
                                vertical =
                                    6.dp
                            )
                )
            }
        }
    }
}

private fun animationEnter(
    animation:
        CaptionAnimation,
    duration: Int
): EnterTransition {

    return when (
        animation
    ) {

        CaptionAnimation.NONE,
        CaptionAnimation.FADE ->

            fadeIn(
                animationSpec =
                    tween<Float>(
                        duration
                    )
            )

        CaptionAnimation.SLIDE_UP ->

            slideInVertically(
                animationSpec =
                    tween<IntOffset>(
                        duration
                    ),
                initialOffsetY = {
                    it
                }
            )

        CaptionAnimation.SLIDE_DOWN ->

            slideInVertically(
                animationSpec =
                    tween<IntOffset>(
                        duration
                    ),
                initialOffsetY = {
                    -it
                }
            )

        CaptionAnimation.SLIDE_LEFT ->

            slideInHorizontally(
                animationSpec =
                    tween<IntOffset>(
                        duration
                    ),
                initialOffsetX = {
                    it
                }
            )

        CaptionAnimation.SLIDE_RIGHT ->

            slideInHorizontally(
                animationSpec =
                    tween<IntOffset>(
                        duration
                    ),
                initialOffsetX = {
                    -it
                }
            )
    }
}

private fun animationExit(
    animation:
        CaptionAnimation,
    duration: Int
): ExitTransition {

    return when (
        animation
    ) {

        CaptionAnimation.NONE,
        CaptionAnimation.FADE ->

            fadeOut(
                animationSpec =
                    tween<Float>(
                        duration
                    )
            )

        CaptionAnimation.SLIDE_UP ->

            slideOutVertically(
                animationSpec =
                    tween<IntOffset>(
                        duration
                    ),
                targetOffsetY = {
                    -it
                }
            )

        CaptionAnimation.SLIDE_DOWN ->

            slideOutVertically(
                animationSpec =
                    tween<IntOffset>(
                        duration
                    ),
                targetOffsetY = {
                    it
                }
            )

        CaptionAnimation.SLIDE_LEFT ->

            slideOutHorizontally(
                animationSpec =
                    tween<IntOffset>(
                        duration
                    ),
                targetOffsetX = {
                    -it
                }
            )

        CaptionAnimation.SLIDE_RIGHT ->

            slideOutHorizontally(
                animationSpec =
                    tween<IntOffset>(
                        duration
                    ),
                targetOffsetX = {
                    it
                }
            )
    }
}

private fun updateCaption(
    captions:
        MutableList<Caption>,
    id: Int,
    transform:
        (Caption) -> Caption
) {

    val index =
        captions.indexOfFirst {
            it.id == id
        }

    if (
        index >= 0
    ) {

        captions[index] =
            transform(
                captions[index]
            )
    }
}

private fun parseSrt(
    content: String
): List<LocalTranscriptSegment> {

    val normalized =
        content
            .replace(
                "\r\n",
                "\n"
            )
            .replace(
                "\r",
                "\n"
            )

    val blocks =
        normalized.split(
            Regex(
                "\n\\s*\n"
            )
        )

    val result =
        mutableListOf<LocalTranscriptSegment>()

    for (
        block in blocks
    ) {

        val lines =
            block.lines()

        val timeIndex =
            lines.indexOfFirst {
                it.contains(
                    "-->"
                )
            }

        if (
            timeIndex < 0
        ) {
            continue
        }

        val parts =
            lines[timeIndex]
                .split(
                    "-->"
                )

        if (
            parts.size < 2
        ) {
            continue
        }

        val start =
            parseSrtTime(
                parts[0].trim()
            )

        val end =
            parseSrtTime(
                parts[1]
                    .trim()
                    .split(
                        " "
                    )
                    .firstOrNull()
                    ?: ""
            )

        if (
            start == null ||
            end == null ||
            end <= start
        ) {
            continue
        }

        val text =
            lines
                .drop(
                    timeIndex + 1
                )
                .joinToString(
                    "\n"
                )
                .trim()

        if (
            text.isNotBlank()
        ) {

            result.add(
                LocalTranscriptSegment(
                    text =
                        text,
                    startTime =
                        start,
                    endTime =
                        end
                )
            )
        }
    }

    return result.sortedBy {
        it.startTime
    }
}

private fun parseSrtTime(
    value: String
): Long? {

    val regex =
        Regex(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})"
        )

    val match =
        regex.find(
            value
        )
            ?: return null

    val h =
        match.groupValues[1]
            .toLongOrNull()
            ?: return null

    val m =
        match.groupValues[2]
            .toLongOrNull()
            ?: return null

    val s =
        match.groupValues[3]
            .toLongOrNull()
            ?: return null

    val ms =
        match.groupValues[4]
            .toLongOrNull()
            ?: return null

    return h * 3_600_000L +
        m * 60_000L +
        s * 1_000L +
        ms
}

private fun formatSrtTime(
    milliseconds: Long
): String {

    val safe =
        milliseconds.coerceAtLeast(
            0L
        )

    val hours =
        safe /
            3_600_000L

    val minutes =
        (
            safe %
                3_600_000L
        ) /
            60_000L

    val seconds =
        (
            safe %
                60_000L
        ) /
            1_000L

    val millis =
        safe %
            1_000L

    return String.format(
        "%02d:%02d:%02d,%03d",
        hours,
        minutes,
        seconds,
        millis
    )
}

private fun buildSrt(
    captions:
        List<Caption>
): String {

    val sorted =
        captions.sortedBy {
            it.startTime
        }

    return buildString {

        sorted.forEachIndexed {
            index,
            caption ->

            append(
                index + 1
            )

            append(
                "\n"
            )

            append(
                formatSrtTime(
                    caption.startTime
                )
            )

            append(
                " --> "
            )

            append(
                formatSrtTime(
                    caption.endTime
                )
            )

            append(
                "\n"
            )

            append(
                caption.text.trim()
            )

            append(
                "\n\n"
            )
        }
    }
}

private fun buildProjectJson(
    captions:
        List<Caption>,
    videoUri:
        Uri?,
    selectedCaptionId:
        Int?
): String {

    val root =
        JSONObject()

    root.put(
        "format",
        "CaptionsFA"
    )

    root.put(
        "version",
        3
    )

    root.put(
        "videoUri",
        videoUri?.toString()
            ?: JSONObject.NULL
    )

    root.put(
        "selectedCaptionId",
        selectedCaptionId
            ?: JSONObject.NULL
    )

    val array =
        JSONArray()

    captions.forEach {
        caption ->

        val item =
            JSONObject()

        item.put(
            "id",
            caption.id
        )

        item.put(
            "text",
            caption.text
        )

        item.put(
            "startTime",
            caption.startTime
        )

        item.put(
            "endTime",
            caption.endTime
        )

        val style =
            JSONObject()

        style.put(
            "fontSize",
            caption.style.fontSize
        )

        style.put(
            "textColor",
            caption.style.textColor
        )

        style.put(
            "backgroundColor",
            caption.style.backgroundColor
        )

        style.put(
            "bold",
            caption.style.bold
        )

        style.put(
            "alignment",
            caption.style.alignment
        )

        style.put(
            "position",
            caption.style.position
        )

        style.put(
            "highlightEnabled",
            caption.style.highlightEnabled
        )

        style.put(
            "highlightColor",
            caption.style.highlightColor
        )

        style.put(
            "animation",
            caption.style.animation.name
        )

        style.put(
            "animationDuration",
            caption.style.animationDuration
        )

        item.put(
            "style",
            style
        )

        array.put(
            item
        )
    }

    root.put(
        "captions",
        array
    )

    return root.toString(
        2
    )
}

private fun parseProjectJson(
    content: String
): LoadedProject {

    val root =
        JSONObject(
            content
        )

    val savedVideo =
        if (
            root.isNull(
                "videoUri"
            )
        ) {

            null

        } else {

            root.optString(
                "videoUri",
                ""
            )
                .takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Uri.parse(it)
                }
        }

    val selected =
        if (
            root.isNull(
                "selectedCaptionId"
            )
        ) {

            null

        } else {

            root.optInt(
                "selectedCaptionId",
                -1
            )
                .takeIf {
                    it > 0
                }
        }

    val array =
        root.optJSONArray(
            "captions"
        )
            ?: JSONArray()

    val result =
        mutableListOf<Caption>()

    for (
        index in
        0 until array.length()
    ) {

        val item =
            array.optJSONObject(
                index
            )
                ?: continue

        val text =
            item.optString(
                "text",
                ""
            )

        val start =
            item.optLong(
                "startTime",
                0L
            )

        val end =
            item.optLong(
                "endTime",
                0L
            )

        if (
            text.isBlank() ||
            end <= start
        ) {
            continue
        }

        val styleJson =
            item.optJSONObject(
                "style"
            )

        val animation =
            try {

                CaptionAnimation
                    .valueOf(
                        styleJson?.optString(
                            "animation",
                            CaptionAnimation
                                .NONE
                                .name
                        )
                            ?: CaptionAnimation
                                .NONE
                                .name
                    )

            } catch (
                _: Exception
            ) {

                CaptionAnimation.NONE
            }

        val style =
            CaptionStyle(

                fontSize =
                    styleJson
                        ?.optDouble(
                            "fontSize",
                            28.0
                        )
                        ?.toFloat()
                        ?: 28f,

                textColor =
                    styleJson
                        ?.optLong(
                            "textColor",
                            0xFFFFFFFF
                        )
                        ?: 0xFFFFFFFF,

                backgroundColor =
                    styleJson
                        ?.optLong(
                            "backgroundColor",
                            0x99000000
                        )
                        ?: 0x99000000,

                bold =
                    styleJson
                        ?.optBoolean(
                            "bold",
                            false
                        )
                        ?: false,

                alignment =
                    styleJson
                        ?.optString(
                            "alignment",
                            "CENTER"
                        )
                        ?: "CENTER",

                position =
                    styleJson
                        ?.optDouble(
                            "position",
                            0.82
                        )
                        ?.toFloat()
                        ?: 0.82f,

                highlightEnabled =
                    styleJson
                        ?.optBoolean(
                            "highlightEnabled",
                            false
                        )
                        ?: false,

                highlightColor =
                    styleJson
                        ?.optLong(
                            "highlightColor",
                            0xFFFFD54F
                        )
                        ?: 0xFFFFD54F,

                animation =
                    animation,

                animationDuration =
                    styleJson
                        ?.optInt(
                            "animationDuration",
                            350
                        )
                        ?: 350
            )

        result.add(
            Caption(
                id =
                    item.optInt(
                        "id",
                        index + 1
                    ),

                text =
                    text,

                startTime =
                    start,

                endTime =
                    end,

                style =
                    style
            )
        )
    }

    return LoadedProject(
        captions =
            result.sortedBy {
                it.startTime
            },

        videoUri =
            savedVideo,

        selectedCaptionId =
            selected
    )
}

private fun formatTime(
    milliseconds: Long
): String {

    val totalSeconds =
        milliseconds /
            1000L

    val minutes =
        totalSeconds /
            60L

    val seconds =
        totalSeconds %
            60L

    val millis =
        milliseconds %
            1000L

    return String.format(
        "%02d:%02d.%03d",
        minutes,
        seconds,
        millis
    )
}
