package com.daboua.captions

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

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
    val alignment: Int = 1,
    val position: Float = 0.80f,
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
    val style: CaptionStyle = CaptionStyle()
)

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            CaptionsFaApp()
        }
    }
}

@Composable
private fun CaptionsFaApp() {

    val context = LocalContext.current

    var videoUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val captions = remember {
        mutableStateListOf<Caption>()
    }

    var nextCaptionId by remember {
        mutableStateOf(1)
    }

    var selectedCaptionId by remember {
        mutableStateOf<Int?>(null)
    }

    var currentPosition by remember {
        mutableStateOf(0L)
    }

    var duration by remember {
        mutableStateOf(0L)
    }

    var playerReference by remember {
        mutableStateOf<ExoPlayer?>(null)
    }

    var operationMessage by remember {
        mutableStateOf("")
    }

    val videoPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.GetContent()
        ) { uri ->

            videoUri = uri
            currentPosition = 0L
            duration = 0L
            selectedCaptionId = null
            operationMessage = ""
        }

    val srtImporter =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            try {

                val content =
                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use {
                            it.readText()
                        }

                if (content.isNullOrBlank()) {

                    operationMessage =
                        "فایل SRT خالی است."

                } else {

                    val imported =
                        parseSrt(
                            content
                        )

                    if (imported.isEmpty()) {

                        operationMessage =
                            "هیچ کپشن معتبری در فایل SRT پیدا نشد."

                    } else {

                        captions.clear()

                        imported.forEachIndexed {
                            index,
                            parsed ->

                            captions.add(
                                Caption(
                                    id =
                                        index + 1,
                                    text =
                                        parsed.text,
                                    startTime =
                                        parsed.startTime,
                                    endTime =
                                        parsed.endTime
                                )
                            )
                        }

                        nextCaptionId =
                            imported.size + 1

                        selectedCaptionId =
                            captions.firstOrNull()
                                ?.id

                        operationMessage =
                            "${imported.size} کپشن از فایل SRT وارد شد."
                    }
                }

            } catch (
                exception: Exception
            ) {

                operationMessage =
                    "خطا در خواندن فایل SRT: " +
                        (
                            exception.message
                                ?: "خطای نامشخص"
                        )
            }
        }

    val srtExporter =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.CreateDocument(
                    "application/x-subrip"
                )
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            try {

                val srtText =
                    buildSrt(
                        captions
                    )

                context.contentResolver
                    .openOutputStream(uri)
                    ?.bufferedWriter()
                    ?.use {
                        it.write(srtText)
                    }

                operationMessage =
                    "${captions.size} کپشن با موفقیت به SRT صادر شد."

            } catch (
                exception: Exception
            ) {

                operationMessage =
                    "خطا در ذخیره SRT: " +
                        (
                            exception.message
                                ?: "خطای نامشخص"
                        )
            }
        }

    LaunchedEffect(playerReference) {

        while (playerReference != null) {

            val player =
                playerReference

            currentPosition =
                player?.currentPosition
                    ?: 0L

            duration =
                player?.duration
                    ?.takeIf {
                        it > 0L
                    }
                    ?: 0L

            delay(100L)
        }
    }

    val activeCaption =
        captions.firstOrNull {

            currentPosition >=
                it.startTime &&
                currentPosition <=
                it.endTime
        }

    MaterialTheme {

        Surface(
            modifier =
                Modifier.fillMaxSize(),
            color =
                MaterialTheme
                    .colorScheme
                    .background
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp)
            ) {

                Text(
                    text =
                        "Captions FA",
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Button(
                    onClick = {
                        videoPicker.launch(
                            "video/*"
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "انتخاب ویدئو"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    OutlinedButton(
                        onClick = {

                            srtImporter.launch(
                                arrayOf(
                                    "application/x-subrip",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "📥 وارد کردن SRT"
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    OutlinedButton(
                        onClick = {

                            if (
                                captions.isEmpty()
                            ) {

                                operationMessage =
                                    "ابتدا حداقل یک کپشن اضافه کنید."

                            } else {

                                srtExporter.launch(
                                    "captions.srt"
                                )
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "📤 خروجی SRT"
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                if (videoUri == null) {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.Center
                    ) {

                        Text(
                            "هنوز ویدئویی انتخاب نشده"
                        )
                    }

                } else {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                    ) {

                        VideoPreview(
                            uri =
                                videoUri!!,
                            onPlayerReady = {
                                player ->
                                playerReference =
                                    player
                            },
                            modifier =
                                Modifier.fillMaxSize()
                        )

                        if (
                            activeCaption != null
                        ) {

                            CaptionOverlay(
                                caption =
                                    activeCaption,
                                modifier =
                                    Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "${formatTime(currentPosition)} / " +
                            formatTime(duration),
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                if (
                    operationMessage.isNotBlank()
                ) {

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            operationMessage,
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                if (
                    duration > 0L
                ) {

                    Timeline(
                        captions =
                            captions,
                        duration =
                            duration,
                        selectedCaptionId =
                            selectedCaptionId,
                        onCaptionSelected = {
                            id ->

                            selectedCaptionId =
                                id

                            val caption =
                                captions.firstOrNull {
                                    it.id == id
                                }

                            if (
                                caption != null
                            ) {

                                playerReference
                                    ?.seekTo(
                                        caption.startTime
                                    )

                                currentPosition =
                                    caption.startTime
                            }
                        }
                    )
                }

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

                            val newPosition =
                                (
                                    currentPosition -
                                        5000L
                                ).coerceAtLeast(
                                    0L
                                )

                            playerReference
                                ?.seekTo(
                                    newPosition
                                )

                            currentPosition =
                                newPosition
                        }
                    ) {

                        Text("-5s")
                    }

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Button(
                        onClick = {

                            playerReference
                                ?.let {
                                    player ->

                                    if (
                                        player.isPlaying
                                    ) {
                                        player.pause()
                                    } else {
                                        player.play()
                                    }
                                }
                        }
                    ) {

                        Text(
                            if (
                                playerReference
                                    ?.isPlaying ==
                                    true
                            ) {
                                "توقف"
                            } else {
                                "پخش"
                            }
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.width(8.dp)
                    )

                    Button(
                        onClick = {

                            val newPosition =
                                (
                                    currentPosition +
                                        5000L
                                ).coerceAtMost(
                                    duration
                                )

                            playerReference
                                ?.seekTo(
                                    newPosition
                                )

                            currentPosition =
                                newPosition
                        }
                    ) {

                        Text("+5s")
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text =
                            "کپشن‌ها",
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        modifier =
                            Modifier.weight(1f)
                    )

                    Button(
                        onClick = {

                            val defaultEnd =
                                (
                                    currentPosition +
                                        3000L
                                ).coerceAtMost(
                                    if (
                                        duration > 0L
                                    ) {
                                        duration
                                    } else {
                                        currentPosition +
                                            3000L
                                    }
                                )

                            if (
                                defaultEnd <=
                                    currentPosition
                            ) {

                                operationMessage =
                                    "امکان افزودن کپشن در این زمان وجود ندارد."

                            } else {

                                val newCaption =
                                    Caption(
                                        id =
                                            nextCaptionId,
                                        text =
                                            "کپشن جدید",
                                        startTime =
                                            currentPosition,
                                        endTime =
                                            defaultEnd
                                    )

                                captions.add(
                                    newCaption
                                )

                                selectedCaptionId =
                                    nextCaptionId

                                nextCaptionId++

                                operationMessage =
                                    "کپشن اضافه شد."
                            }
                        }
                    ) {

                        Text(
                            "افزودن"
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                if (
                    captions.isEmpty()
                ) {

                    Text(
                        text =
                            "هنوز کپشنی اضافه نشده",
                        modifier =
                            Modifier.padding(
                                vertical = 16.dp
                            )
                    )

                } else {

                    LazyColumn(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        items(
                            items =
                                captions,
                            key = {
                                it.id
                            }
                        ) { caption ->

                            val captionIndex =
                                captions.indexOfFirst {
                                    it.id ==
                                        caption.id
                                }

                            val hasNext =
                                captionIndex >= 0 &&
                                    captionIndex <
                                        captions.lastIndex

                            CaptionItem(
                                caption =
                                    caption,

                                selected =
                                    selectedCaptionId ==
                                        caption.id,

                                onSelect = {

                                    selectedCaptionId =
                                        caption.id

                                    playerReference
                                        ?.seekTo(
                                            caption.startTime
                                        )

                                    currentPosition =
                                        caption.startTime

                                    operationMessage =
                                        ""
                                },

                                onTextChange = {
                                    newText ->

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        copy(
                                            text =
                                                newText
                                        )
                                    }
                                },

                                onStartChange = {
                                    newStart ->

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        val safeStart =
                                            newStart
                                                .coerceAtLeast(
                                                    0L
                                                )

                                        val safeEnd =
                                            endTime
                                                .coerceAtLeast(
                                                    safeStart +
                                                        1L
                                                )

                                        copy(
                                            startTime =
                                                safeStart,
                                            endTime =
                                                safeEnd
                                        )
                                    }
                                },

                                onEndChange = {
                                    newEnd ->

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        val safeEnd =
                                            newEnd
                                                .coerceAtLeast(
                                                    startTime +
                                                        1L
                                                )

                                        copy(
                                            endTime =
                                                safeEnd
                                        )
                                    }
                                },

                                onSetStart = {

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        val safeStart =
                                            currentPosition

                                        val safeEnd =
                                            endTime
                                                .coerceAtLeast(
                                                    safeStart +
                                                        1L
                                                )

                                        copy(
                                            startTime =
                                                safeStart,
                                            endTime =
                                                safeEnd
                                        )
                                    }

                                    operationMessage =
                                        "شروع کپشن ثبت شد."
                                },

                                onSetEnd = {

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        val safeEnd =
                                            currentPosition
                                                .coerceAtLeast(
                                                    startTime +
                                                        1L
                                                )

                                        copy(
                                            endTime =
                                                safeEnd
                                        )
                                    }

                                    operationMessage =
                                        "پایان کپشن ثبت شد."
                                },

                                onGoToStart = {

                                    playerReference
                                        ?.seekTo(
                                            caption.startTime
                                        )

                                    currentPosition =
                                        caption.startTime
                                },

                                onGoToEnd = {

                                    playerReference
                                        ?.seekTo(
                                            caption.endTime
                                        )

                                    currentPosition =
                                        caption.endTime
                                },

                                onSplit = {

                                    val result =
                                        splitCaption(
                                            captions =
                                                captions,
                                            captionId =
                                                caption.id,
                                            splitTime =
                                                currentPosition,
                                            newId =
                                                nextCaptionId
                                        )

                                    if (
                                        result
                                    ) {

                                        nextCaptionId++

                                        val index =
                                            captions
                                                .indexOfFirst {
                                                    it.id ==
                                                        caption.id
                                                }

                                        if (
                                            index >= 0 &&
                                            index + 1 <
                                                captions.size
                                        ) {

                                            selectedCaptionId =
                                                captions[
                                                    index + 1
                                                ].id
                                        }

                                        operationMessage =
                                            "کپشن در زمان ${formatTime(currentPosition)} تقسیم شد."

                                    } else {

                                        operationMessage =
                                            "برای Split باید زمان فعلی داخل بازه کپشن باشد."
                                    }
                                },

                                onMergeNext = {

                                    if (
                                        hasNext
                                    ) {

                                        val next =
                                            captions[
                                                captionIndex +
                                                    1
                                            ]

                                        if (
                                            next.startTime <=
                                                caption.endTime
                                        ) {

                                            val merged =
                                                mergeCaptions(
                                                    first =
                                                        caption,
                                                    second =
                                                        next
                                                )

                                            captions[
                                                captionIndex
                                            ] =
                                                merged

                                            captions.removeAt(
                                                captionIndex +
                                                    1
                                            )

                                            selectedCaptionId =
                                                merged.id

                                            operationMessage =
                                                "کپشن با کپشن بعدی ادغام شد."

                                        } else {

                                            operationMessage =
                                                "برای ادغام، دو کپشن باید به هم چسبیده یا هم‌پوشان باشند."
                                        }
                                    }
                                },

                                canMergeNext =
                                    hasNext &&
                                        captionIndex >=
                                            0 &&
                                        captionIndex <
                                            captions.lastIndex &&
                                        captions[
                                            captionIndex +
                                                1
                                        ].startTime <=
                                            caption.endTime,

                                onDelete = {

                                    captions.removeAll {
                                        it.id ==
                                            caption.id
                                    }

                                    if (
                                        selectedCaptionId ==
                                            caption.id
                                    ) {

                                        selectedCaptionId =
                                            null
                                    }

                                    operationMessage =
                                        "کپشن حذف شد."
                                },

                                onStyleChange = {
                                    transform ->

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        copy(
                                            style =
                                                style.transform()
                                        )
                                    }
                                }
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ParsedSrtCaption(
    val text: String,
    val startTime: Long,
    val endTime: Long
)

private fun parseSrt(
    content: String
): List<ParsedSrtCaption> {

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
        normalized
            .split(
                Regex(
                    "\n\\s*\n"
                )
            )

    val result =
        mutableListOf<ParsedSrtCaption>()

    for (
        block in blocks
    ) {

        val lines =
            block
                .lines()
                .map {
                    it.trimEnd()
                }

        if (
            lines.isEmpty()
        ) {
            continue
        }

        val timeLineIndex =
            lines.indexOfFirst {
                it.contains("-->")
            }

        if (
            timeLineIndex < 0
        ) {
            continue
        }

        val timeLine =
            lines[
                timeLineIndex
            ]

        val timeParts =
            timeLine
                .split(
                    "-->"
                )

        if (
            timeParts.size < 2
        ) {
            continue
        }

        val start =
            parseSrtTime(
                timeParts[0].trim()
            )

        val end =
            parseSrtTime(
                timeParts[1]
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
                    timeLineIndex + 1
                )
                .joinToString(
                    "\n"
                )
                .trim()

        if (
            text.isBlank()
        ) {
            continue
        }

        result.add(
            ParsedSrtCaption(
                text =
                    text,
                startTime =
                    start,
                endTime =
                    end
            )
        )
    }

    return result.sortedBy {
        it.startTime
    }
}

private fun parseSrtTime(
    value: String
): Long? {

    val cleaned =
        value.trim()

    val regex =
        Regex(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})"
        )

    val match =
        regex.find(
            cleaned
        ) ?: return null

    val hours =
        match.groupValues[1]
            .toLongOrNull()
            ?: return null

    val minutes =
        match.groupValues[2]
            .toLongOrNull()
            ?: return null

    val seconds =
        match.groupValues[3]
            .toLongOrNull()
            ?: return null

    val millis =
        match.groupValues[4]
            .toLongOrNull()
            ?: return null

    if (
        minutes > 59 ||
        seconds > 59
    ) {
        return null
    }

    return (
        hours * 60L * 60L * 1000L
    ) +
        (
            minutes * 60L * 1000L
        ) +
        (
            seconds * 1000L
        ) +
        millis
}

private fun buildSrt(
    captions: List<Caption>
): String {

    val sorted =
        captions.sortedWith(
            compareBy<Caption> {
                it.startTime
            }.thenBy {
                it.endTime
            }
        )

    val builder =
        StringBuilder()

    sorted.forEachIndexed {
        index,
        caption ->

        builder.append(
            index + 1
        )

        builder.append(
            "\n"
        )

        builder.append(
            formatSrtTime(
                caption.startTime
            )
        )

        builder.append(
            " --> "
        )

        builder.append(
            formatSrtTime(
                caption.endTime
            )
        )

        builder.append(
            "\n"
        )

        builder.append(
            caption.text.trim()
        )

        builder.append(
            "\n\n"
        )
    }

    return builder.toString()
}

private fun formatSrtTime(
    milliseconds: Long
): String {

    val safe =
        milliseconds.coerceAtLeast(
            0L
        )

    val hours =
        safe / 3_600_000L

    val minutes =
        (
            safe % 3_600_000L
        ) / 60_000L

    val seconds =
        (
            safe % 60_000L
        ) / 1000L

    val millis =
        safe % 1000L

    return String.format(
        "%02d:%02d:%02d,%03d",
        hours,
        minutes,
        seconds,
        millis
    )
}

private fun updateCaption(
    captions: MutableList<Caption>,
    id: Int,
    transform: Caption.() -> Caption
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

private fun splitCaption(
    captions: MutableList<Caption>,
    captionId: Int,
    splitTime: Long,
    newId: Int
): Boolean {

    val index =
        captions.indexOfFirst {
            it.id == captionId
        }

    if (
        index < 0
    ) {
        return false
    }

    val caption =
        captions[index]

    if (
        splitTime <=
            caption.startTime ||
        splitTime >=
            caption.endTime
    ) {
        return false
    }

    val totalDuration =
        caption.endTime -
            caption.startTime

    if (
        totalDuration <= 1L
    ) {
        return false
    }

    val elapsed =
        splitTime -
            caption.startTime

    val ratio =
        (
            elapsed.toDouble() /
                totalDuration.toDouble()
        ).coerceIn(
            0.0,
            1.0
        )

    val parts =
        splitCaptionText(
            caption.text,
            ratio
        )

    val firstCaption =
        caption.copy(
            text =
                parts.first,
            endTime =
                splitTime
        )

    val secondCaption =
        caption.copy(
            id =
                newId,
            text =
                parts.second,
            startTime =
                splitTime
        )

    captions[index] =
        firstCaption

    captions.add(
        index + 1,
        secondCaption
    )

    return true
}

private fun splitCaptionText(
    text: String,
    ratio: Double
): Pair<String, String> {

    val trimmed =
        text.trim()

    if (
        trimmed.isEmpty()
    ) {

        return Pair(
            "کپشن",
            "کپشن"
        )
    }

    val words =
        trimmed.split(
            Regex(
                "\\s+"
            )
        )

    if (
        words.size <= 1
    ) {

        if (
            trimmed.length <= 1
        ) {

            return Pair(
                trimmed,
                trimmed
            )
        }

        val cut =
            (
                trimmed.length *
                    ratio
            )
                .toInt()
                .coerceIn(
                    1,
                    trimmed.length - 1
                )

        return Pair(
            trimmed.substring(
                0,
                cut
            ).trim(),
            trimmed.substring(
                cut
            ).trim()
        )
    }

    var bestIndex =
        1

    var bestDifference =
        Double.MAX_VALUE

    for (
        index in 1 until words.size
    ) {

        val currentRatio =
            index.toDouble() /
                words.size.toDouble()

        val difference =
            kotlin.math.abs(
                currentRatio -
                    ratio
            )

        if (
            difference <
                bestDifference
        ) {

            bestDifference =
                difference

            bestIndex =
                index
        }
    }

    val firstText =
        words
            .take(
                bestIndex
            )
            .joinToString(
                " "
            )

    val secondText =
        words
            .drop(
                bestIndex
            )
            .joinToString(
                " "
            )

    return Pair(
        firstText.ifBlank {
            "کپشن"
        },
        secondText.ifBlank {
            "کپشن"
        }
    )
}

private fun mergeCaptions(
    first: Caption,
    second: Caption
): Caption {

    val firstText =
        first.text.trim()

    val secondText =
        second.text.trim()

    val mergedText =
        when {

            firstText.isBlank() ->
                secondText

            secondText.isBlank() ->
                firstText

            else ->
                "$firstText $secondText"
        }

    return first.copy(
        text =
            mergedText,
        startTime =
            minOf(
                first.startTime,
                second.startTime
            ),
        endTime =
            maxOf(
                first.endTime,
                second.endTime
            )
    )
}

@Composable
private fun CaptionOverlay(
    caption: Caption,
    modifier: Modifier
) {

    val style =
        caption.style

    val textColor =
        Color(
            style.textColor
        )

    val backgroundColor =
        Color(
            style.backgroundColor
        )

    val highlightColor =
        Color(
            style.highlightColor
        )

    val enterTransition:
        EnterTransition

    val exitTransition:
        ExitTransition

    when (
        style.animation
    ) {

        CaptionAnimation.NONE -> {

            enterTransition =
                EnterTransition.None

            exitTransition =
                ExitTransition.None
        }

        CaptionAnimation.FADE -> {

            enterTransition =
                fadeIn(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        )
                )

            exitTransition =
                fadeOut(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        )
                )
        }

        CaptionAnimation.SLIDE_UP -> {

            enterTransition =
                slideInVertically(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        ),
                    initialOffsetY = {
                        it
                    }
                )

            exitTransition =
                slideOutVertically(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        ),
                    targetOffsetY = {
                        it
                    }
                )
        }

        CaptionAnimation.SLIDE_DOWN -> {

            enterTransition =
                slideInVertically(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        ),
                    initialOffsetY = {
                        -it
                    }
                )

            exitTransition =
                slideOutVertically(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        ),
                    targetOffsetY = {
                        -it
                    }
                )
        }

        CaptionAnimation.SLIDE_LEFT -> {

            enterTransition =
                slideInHorizontally(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        ),
                    initialOffsetX = {
                        it
                    }
                )

            exitTransition =
                slideOutHorizontally(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        ),
                    targetOffsetX = {
                        -it
                    }
                )
        }

        CaptionAnimation.SLIDE_RIGHT -> {

            enterTransition =
                slideInHorizontally(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        ),
                    initialOffsetX = {
                        -it
                    }
                )

            exitTransition =
                slideOutHorizontally(
                    animationSpec =
                        tween(
                            durationMillis =
                                style.animationDuration
                        ),
                    targetOffsetX = {
                        it
                    }
                )
        }
    }

    Box(
        modifier =
            modifier
    ) {

        val verticalAlignment =
            when {

                style.position < 0.33f ->
                    Alignment.TopCenter

                style.position < 0.66f ->
                    Alignment.Center

                else ->
                    Alignment.BottomCenter
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 12.dp
                    ),
            contentAlignment =
                verticalAlignment
        ) {

            AnimatedVisibility(
                visible = true,
                enter =
                    enterTransition,
                exit =
                    exitTransition
            ) {

                Box(
                    modifier =
                        Modifier
                            .clip(
                                RoundedCornerShape(
                                    10.dp
                                )
                            )
                            .background(
                                backgroundColor
                            )
                            .padding(
                                horizontal = 14.dp,
                                vertical = 8.dp
                            )
                ) {

                    Text(
                        text =
                            caption.text,
                        color =
                            if (
                                style.highlightEnabled
                            ) {
                                highlightColor
                            } else {
                                textColor
                            },
                        fontSize =
                            style.fontSize.sp,
                        fontWeight =
                            if (
                                style.bold
                            ) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        textAlign =
                            when (
                                style.alignment
                            ) {

                                0 ->
                                    TextAlign.Start

                                2 ->
                                    TextAlign.End

                                else ->
                                    TextAlign.Center
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionItem(
    caption: Caption,
    selected: Boolean,
    onSelect: () -> Unit,
    onTextChange: (String) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onGoToStart: () -> Unit,
    onGoToEnd: () -> Unit,
    onSplit: () -> Unit,
    onMergeNext: () -> Unit,
    canMergeNext: Boolean,
    onDelete: () -> Unit,
    onStyleChange:
        ((CaptionStyle.() -> CaptionStyle) -> Unit)
) {

    var textValue by remember(
        caption.id,
        caption.text
    ) {

        mutableStateOf(
            caption.text
        )
    }

    var startValue by remember(
        caption.id,
        caption.startTime
    ) {

        mutableStateOf(
            caption.startTime.toString()
        )
    }

    var endValue by remember(
        caption.id,
        caption.endTime
    ) {

        mutableStateOf(
            caption.endTime.toString()
        )
    }

    var showStyle by remember {
        mutableStateOf(false)
    }

    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(12.dp)
        ) {

            Button(
                onClick =
                    onSelect,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    if (
                        selected
                    ) {
                        "کپشن انتخاب شده"
                    } else {
                        "انتخاب این کپشن"
                    }
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            OutlinedTextField(
                value =
                    textValue,
                onValueChange = {

                    textValue =
                        it

                    onTextChange(
                        it
                    )
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text(
                        "متن کپشن"
                    )
                }
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                OutlinedTextField(
                    value =
                        startValue,
                    onValueChange = {
                        value ->

                        if (
                            value.all {
                                it.isDigit()
                            }
                        ) {

                            startValue =
                                value

                            value
                                .toLongOrNull()
                                ?.let {
                                    onStartChange(
                                        it
                                    )
                                }
                        }
                    },
                    modifier =
                        Modifier.weight(1f),
                    label = {
                        Text(
                            "شروع (ms)"
                        )
                    },
                    singleLine = true
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                OutlinedTextField(
                    value =
                        endValue,
                    onValueChange = {
                        value ->

                        if (
                            value.all {
                                it.isDigit()
                            }
                        ) {

                            endValue =
                                value

                            value
                                .toLongOrNull()
                                ?.let {
                                    onEndChange(
                                        it
                                    )
                                }
                        }
                    },
                    modifier =
                        Modifier.weight(1f),
                    label = {
                        Text(
                            "پایان (ms)"
                        )
                    },
                    singleLine = true
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Button(
                    onClick =
                        onSetStart,
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "ثبت شروع"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Button(
                    onClick =
                        onSetEnd,
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "ثبت پایان"
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                TextButton(
                    onClick =
                        onGoToStart,
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "↤ شروع"
                    )
                }

                TextButton(
                    onClick =
                        onGoToEnd,
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "پایان ↦"
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                OutlinedButton(
                    onClick =
                        onSplit,
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "✂ Split"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                OutlinedButton(
                    onClick =
                        onMergeNext,
                    enabled =
                        canMergeNext,
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "ادغام با بعدی"
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Button(
                onClick = {

                    showStyle =
                        !showStyle
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    if (
                        showStyle
                    ) {
                        "بستن تنظیمات استایل"
                    } else {
                        "تنظیمات استایل"
                    }
                )
            }

            if (
                showStyle
            ) {

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    "اندازه فونت: " +
                        "${caption.style.fontSize.toInt()}"
                )

                Slider(
                    value =
                        caption.style.fontSize,
                    onValueChange = {
                        value ->

                        onStyleChange {
                            copy(
                                fontSize =
                                    value
                            )
                        }
                    },
                    valueRange =
                        14f..64f
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    "موقعیت عمودی کپشن"
                )

                Slider(
                    value =
                        caption.style.position,
                    onValueChange = {
                        value ->

                        onStyleChange {
                            copy(
                                position =
                                    value
                            )
                        }
                    },
                    valueRange =
                        0.05f..0.95f
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text("بالا")
                    Text("وسط")
                    Text("پایین")
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        "متن ضخیم",
                        modifier =
                            Modifier.weight(1f)
                    )

                    Switch(
                        checked =
                            caption.style.bold,
                        onCheckedChange = {
                            checked ->

                            onStyleChange {
                                copy(
                                    bold =
                                        checked
                                )
                            }
                        }
                    )
                }

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        "هایلایت",
                        modifier =
                            Modifier.weight(1f)
                    )

                    Switch(
                        checked =
                            caption.style
                                .highlightEnabled,
                        onCheckedChange = {
                            checked ->

                            onStyleChange {
                                copy(
                                    highlightEnabled =
                                        checked
                                )
                            }
                        }
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    "تراز متن"
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    TextButton(
                        onClick = {

                            onStyleChange {
                                copy(
                                    alignment =
                                        0
                                )
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "چپ"
                        )
                    }

                    TextButton(
                        onClick = {

                            onStyleChange {
                                copy(
                                    alignment =
                                        1
                                )
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "وسط"
                        )
                    }

                    TextButton(
                        onClick = {

                            onStyleChange {
                                copy(
                                    alignment =
                                        2
                                )
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            "راست"
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    "رنگ متن"
                )

                ColorChoices(
                    selectedColor =
                        caption.style
                            .textColor,
                    colors =
                        listOf(
                            0xFFFFFFFF,
                            0xFFFFEB3B,
                            0xFFFF9800,
                            0xFFFF5252,
                            0xFF69F0AE,
                            0xFF40C4FF
                        ),
                    onColorSelected = {
                        color ->

                        onStyleChange {
                            copy(
                                textColor =
                                    color
                            )
                        }
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    "رنگ پس‌زمینه"
                )

                ColorChoices(
                    selectedColor =
                        caption.style
                            .backgroundColor,
                    colors =
                        listOf(
                            0x99000000,
                            0xCC000000,
                            0x99FFFFFF,
                            0x990D47A1,
                            0x99004640,
                            0x99B71C1C
                        ),
                    onColorSelected = {
                        color ->

                        onStyleChange {
                            copy(
                                backgroundColor =
                                    color
                            )
                        }
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    "رنگ هایلایت"
                )

                ColorChoices(
                    selectedColor =
                        caption.style
                            .highlightColor,
                    colors =
                        listOf(
                            0xFFFFD54F,
                            0xFFFF5252,
                            0xFF69F0AE,
                            0xFF40C4FF,
                            0xFFFFFFFF,
                            0xFFFF9800
                        ),
                    onColorSelected = {
                        color ->

                        onStyleChange {
                            copy(
                                highlightColor =
                                    color
                            )
                        }
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Text(
                    "انیمیشن کپشن",
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                AnimationChoice(
                    title =
                        "بدون انیمیشن",
                    selected =
                        caption.style
                            .animation ==
                            CaptionAnimation.NONE,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation
                                        .NONE
                            )
                        }
                    }
                )

                AnimationChoice(
                    title =
                        "Fade",
                    selected =
                        caption.style
                            .animation ==
                            CaptionAnimation.FADE,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation
                                        .FADE
                            )
                        }
                    }
                )

                AnimationChoice(
                    title =
                        "Slide Up",
                    selected =
                        caption.style
                            .animation ==
                            CaptionAnimation
                                .SLIDE_UP,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation
                                        .SLIDE_UP
                            )
                        }
                    }
                )

                AnimationChoice(
                    title =
                        "Slide Down",
                    selected =
                        caption.style
                            .animation ==
                            CaptionAnimation
                                .SLIDE_DOWN,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation
                                        .SLIDE_DOWN
                            )
                        }
                    }
                )

                AnimationChoice(
                    title =
                        "Slide Left",
                    selected =
                        caption.style
                            .animation ==
                            CaptionAnimation
                                .SLIDE_LEFT,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation
                                        .SLIDE_LEFT
                            )
                        }
                    }
                )

                AnimationChoice(
                    title =
                        "Slide Right",
                    selected =
                        caption.style
                            .animation ==
                            CaptionAnimation
                                .SLIDE_RIGHT,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation
                                        .SLIDE_RIGHT
                            )
                        }
                    }
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    "مدت انیمیشن: " +
                        "${caption.style.animationDuration} ms"
                )

                Slider(
                    value =
                        caption.style
                            .animationDuration
                            .toFloat(),
                    onValueChange = {
                        value ->

                        val durationValue =
                            value
                                .toInt()
                                .coerceIn(
                                    100,
                                    1500
                                )

                        onStyleChange {
                            copy(
                                animationDuration =
                                    durationValue
                            )
                        }
                    },
                    valueRange =
                        100f..1500f
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text(
                        "100ms"
                    )

                    Text(
                        "1500ms"
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            TextButton(
                onClick =
                    onDelete
            ) {

                Text(
                    "حذف کپشن"
                )
            }
        }
    }
}

@Composable
private fun AnimationChoice(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    OutlinedButton(
        onClick =
            onClick,
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            if (
                selected
            ) {
                "✓ $title"
            } else {
                title
            }
        )
    }

    Spacer(
        modifier =
            Modifier.height(4.dp)
    )
}

@Composable
private fun ColorChoices(
    selectedColor: Long,
    colors: List<Long>,
    onColorSelected:
        (Long) -> Unit
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        colors.forEach {
            colorValue ->

            val selected =
                colorValue ==
                    selectedColor

            Box(
                modifier =
                    Modifier
                        .width(42.dp)
                        .height(42.dp)
                        .clip(
                            RoundedCornerShape(
                                8.dp
                            )
                        )
                        .background(
                            Color(
                                colorValue
                            )
                        )
                        .border(
                            width =
                                if (
                                    selected
                                ) {
                                    3.dp
                                } else {
                                    1.dp
                                },
                            color =
                                if (
                                    selected
                                ) {
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .outline
                                },
                            shape =
                                RoundedCornerShape(
                                    8.dp
                                )
                        )
                        .clickable {

                            onColorSelected(
                                colorValue
                            )
                        }
            )
        }
    }
}

@Composable
private fun Timeline(
    captions: List<Caption>,
    duration: Long,
    selectedCaptionId: Int?,
    onCaptionSelected:
        (Int) -> Unit
) {

    val safeDuration =
        duration.coerceAtLeast(
            1L
        )

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(
                        RoundedCornerShape(
                            10.dp
                        )
                    )
                    .background(
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    )
                    .padding(6.dp)
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

                val endFraction =
                    (
                        caption.endTime
                            .toFloat() /
                            safeDuration
                                .toFloat()
                    ).coerceIn(
                        startFraction,
                        1f
                    )

                val widthFraction =
                    (
                        endFraction -
                            startFraction
                    ).coerceAtLeast(
                        0.02f
                    )

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(
                                widthFraction
                            )
                            .height(
                                58.dp
                            )
                            .padding(
                                start =
                                    (
                                        startFraction *
                                            1000
                                    ).dp
                            )
                            .clip(
                                RoundedCornerShape(
                                    8.dp
                                )
                            )
                            .border(
                                width =
                                    if (
                                        selectedCaptionId ==
                                            caption.id
                                    ) {
                                        2.dp
                                    } else {
                                        1.dp
                                    },
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,
                                shape =
                                    RoundedCornerShape(
                                        8.dp
                                    )
                            )
                            .clickable {

                                onCaptionSelected(
                                    caption.id
                                )
                            }
                ) {

                    Text(
                        text =
                            caption.text,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                        maxLines = 2,
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                "00:00.000",
                style =
                    MaterialTheme
                        .typography
                        .labelSmall
            )

            Text(
                formatTime(
                    duration
                ),
                style =
                    MaterialTheme
                        .typography
                        .labelSmall
            )
        }
    }
}

@Composable
private fun VideoPreview(
    uri: Uri,
    onPlayerReady:
        (ExoPlayer) -> Unit,
    modifier: Modifier =
        Modifier
) {

    val context =
        LocalContext.current

    val player =
        remember(uri) {

            ExoPlayer
                .Builder(
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

                    playWhenReady =
                        false
                }
        }

    LaunchedEffect(
        player
    ) {

        onPlayerReady(
            player
        )
    }

    DisposableEffect(
        player
    ) {

        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier =
            modifier,
        factory = {
            viewContext ->

            PlayerView(
                viewContext
            ).apply {

                this.player =
                    player

                useController =
                    true
            }
        }
    )
}

private fun formatTime(
    milliseconds: Long
): String {

    val safe =
        milliseconds.coerceAtLeast(
            0L
        )

    val totalSeconds =
        safe / 1000L

    val minutes =
        totalSeconds / 60L

    val seconds =
        totalSeconds % 60L

    val millis =
        safe % 1000L

    return String.format(
        "%02d:%02d.%03d",
        minutes,
        seconds,
        millis
    )
}
