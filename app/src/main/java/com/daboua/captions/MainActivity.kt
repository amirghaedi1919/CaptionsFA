package com.daboua.captions

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
    val animation: CaptionAnimation = CaptionAnimation.NONE,
    val animationDuration: Int = 350
)

data class Caption(
    val id: Int,
    val text: String,
    val startTime: Long,
    val endTime: Long,
    val style: CaptionStyle = CaptionStyle()
)

private data class ParsedSrtCaption(
    val text: String,
    val startTime: Long,
    val endTime: Long
)

private data class LoadedProject(
    val captions: List<Caption>,
    val videoUri: Uri?,
    val selectedCaptionId: Int?
)

@Composable
fun CaptionsFaApp() {

    val captions = remember {
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

    var playerReference by remember {
        mutableStateOf<ExoPlayer?>(null)
    }

    val context =
        androidx.compose.ui.platform.LocalContext.current

    val videoPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {

                try {
                    context.contentResolver
                        .takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                } catch (_: SecurityException) {
                }

                videoUri = uri
                currentPosition = 0L
                videoDuration = 0L
                isPlaying = false
                playerReference = null

                message = "ویدیو انتخاب شد"
            }
        }

    val srtImportPicker =
        rememberLauncherForActivityResult(
            contract =
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

                    if (content != null) {

                        val parsed =
                            parseSrt(content)

                        captions.clear()

                        var id = 1

                        parsed.forEach { item ->

                            captions.add(
                                Caption(
                                    id = id++,
                                    text = item.text,
                                    startTime =
                                        item.startTime,
                                    endTime =
                                        item.endTime
                                )
                            )
                        }

                        nextCaptionId = id

                        selectedCaptionId =
                            captions
                                .firstOrNull()
                                ?.id

                        message =
                            if (parsed.isEmpty()) {
                                "هیچ کپشن معتبری پیدا نشد"
                            } else {
                                "${parsed.size} کپشن وارد شد"
                            }
                    }

                } catch (_: Exception) {

                    message =
                        "خطا در خواندن فایل SRT"
                }
            }
        }

    val srtExportPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.CreateDocument(
                    "text/plain"
                )
        ) { uri ->

            if (uri != null) {

                try {

                    val srt =
                        buildSrt(captions)

                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use {
                            it.write(srt)
                        }

                    message =
                        "فایل SRT ذخیره شد"

                } catch (_: Exception) {

                    message =
                        "خطا در ذخیره SRT"
                }
            }
        }

    val projectSavePicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.CreateDocument(
                    "application/octet-stream"
                )
        ) { uri ->

            if (uri != null) {

                try {

                    val projectJson =
                        buildProjectJson(
                            captions = captions,
                            videoUri = videoUri,
                            selectedCaptionId =
                                selectedCaptionId
                        )

                    context.contentResolver
                        .openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use {
                            it.write(projectJson)
                        }

                    message =
                        "پروژه با موفقیت ذخیره شد"

                } catch (_: Exception) {

                    message =
                        "خطا در ذخیره پروژه"
                }
            }
        }

    val projectOpenPicker =
        rememberLauncherForActivityResult(
            contract =
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

                    if (content != null) {

                        val project =
                            parseProjectJson(content)

                        captions.clear()

                        captions.addAll(
                            project.captions
                                .sortedBy {
                                    it.startTime
                                }
                        )

                        nextCaptionId =
                            (
                                captions.maxOfOrNull {
                                    it.id
                                } ?: 0
                            ) + 1

                        selectedCaptionId =
                            project.selectedCaptionId
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

                        currentPosition = 0L
                        videoDuration = 0L
                        isPlaying = false
                        playerReference = null

                        message =
                            "پروژه با ${captions.size} کپشن باز شد"
                    }

                } catch (_: Exception) {

                    message =
                        "فایل پروژه معتبر نیست"
                }
            }
        }

    LaunchedEffect(playerReference) {

        val player =
            playerReference
                ?: return@LaunchedEffect

        while (true) {

            currentPosition =
                player.currentPosition
                    .coerceAtLeast(0L)

            videoDuration =
                player.duration
                    .coerceAtLeast(0L)

            isPlaying =
                player.isPlaying

            delay(100)
        }
    }

    val activeCaption =
        captions.firstOrNull {
            currentPosition >= it.startTime &&
                currentPosition <= it.endTime
        }

    LaunchedEffect(activeCaption?.id) {

        if (activeCaption != null) {
            selectedCaptionId =
                activeCaption.id
        }
    }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF101010)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {

                Text(
                    text = "Captions FA",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        onClick = {
                            videoPicker.launch(
                                arrayOf("video/*")
                            )
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("انتخاب ویدیو")
                    }

                    Button(
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
                            Modifier.weight(1f)
                    ) {
                        Text("ورود SRT")
                    }
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
                        Arrangement.spacedBy(8.dp)
                ) {

                    OutlinedButton(
                        onClick = {
                            srtExportPicker.launch(
                                "captions.srt"
                            )
                        }
                    ) {
                        Text("خروجی SRT")
                    }

                    OutlinedButton(
                        onClick = {
                            projectSavePicker.launch(
                                "captions_project.capfa"
                            )
                        }
                    ) {
                        Text("💾 ذخیره پروژه")
                    }

                    OutlinedButton(
                        onClick = {
                            projectOpenPicker.launch(
                                arrayOf(
                                    "application/octet-stream",
                                    "application/json",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        }
                    ) {
                        Text("📂 باز کردن پروژه")
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                videoUri?.let { uri ->

                    VideoPreview(
                        uri = uri,
                        currentPosition =
                            currentPosition,
                        onPlayerReady = {
                            playerReference = it
                        }
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Timeline(
                        captions = captions,
                        currentPosition =
                            currentPosition,
                        duration =
                            videoDuration,
                        onPositionClick = {
                            position ->
                            playerReference
                                ?.seekTo(position)
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
                            Text("-5s")
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

                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }
                        ) {
                            Text(
                                if (isPlaying)
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
                            Text("+5s")
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            "${formatTime(currentPosition)} / " +
                                formatTime(videoDuration),
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

                if (message.isNotBlank()) {

                    Text(
                        text = message,
                        color =
                            Color(0xFFFFD54F),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 4.dp
                                ),
                        textAlign =
                            TextAlign.Center
                    )
                }

                CaptionEditor(
                    captions = captions,
                    currentPosition =
                        currentPosition,
                    selectedCaptionId =
                        selectedCaptionId,
                    onSelect = {
                        selectedCaptionId = it
                    },
                    onAdd = {

                        val start =
                            currentPosition

                        val end =
                            if (videoDuration > start) {
                                (
                                    start + 2000L
                                ).coerceAtMost(
                                    videoDuration
                                )
                            } else {
                                start + 2000L
                            }

                        captions.add(
                            Caption(
                                id = nextCaptionId,
                                text = "کپشن جدید",
                                startTime = start,
                                endTime = end
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
                            captions.firstOrNull()?.id
                    },
                    onUpdate = { id, transform ->

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

                        if (caption != null) {

                            if (
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
                                    "شروع کپشن ثبت شد"

                            } else {

                                message =
                                    "شروع باید قبل از پایان باشد"
                            }
                        }
                    },
                    onSetEnd = { id ->

                        val caption =
                            captions.firstOrNull {
                                it.id == id
                            }

                        if (caption != null) {

                            if (
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
                                    "پایان کپشن ثبت شد"

                            } else {

                                message =
                                    "پایان باید بعد از شروع باشد"
                            }
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
                    },
                    onSplit = { id ->

                        val caption =
                            captions.firstOrNull {
                                it.id == id
                            }

                        if (caption == null) {

                            message =
                                "کپشن پیدا نشد"

                        } else {

                            val position =
                                currentPosition

                            if (
                                position <=
                                    caption.startTime ||
                                position >=
                                    caption.endTime
                            ) {

                                message =
                                    "برای Split باید داخل بازه کپشن باشید"

                            } else {

                                val total =
                                    caption.endTime -
                                        caption.startTime

                                val elapsed =
                                    position -
                                        caption.startTime

                                val ratio =
                                    elapsed.toFloat() /
                                        total.toFloat()

                                val parts =
                                    splitCaptionText(
                                        caption.text,
                                        ratio
                                    )

                                if (parts == null) {

                                    message =
                                        "متن برای Split مناسب نیست"

                                } else {

                                    val newId =
                                        nextCaptionId++

                                    val first =
                                        caption.copy(
                                            text =
                                                parts.first,
                                            endTime =
                                                position
                                        )

                                    val second =
                                        caption.copy(
                                            id = newId,
                                            text =
                                                parts.second,
                                            startTime =
                                                position
                                        )

                                    val index =
                                        captions
                                            .indexOfFirst {
                                                it.id == id
                                            }

                                    if (index >= 0) {

                                        captions[index] =
                                            first

                                        captions.add(
                                            index + 1,
                                            second
                                        )

                                        selectedCaptionId =
                                            second.id

                                        message =
                                            "کپشن Split شد"
                                    }
                                }
                            }
                        }
                    },
                    onMerge = { id ->

                        val index =
                            captions.indexOfFirst {
                                it.id == id
                            }

                        if (
                            index < 0 ||
                            index + 1 >=
                                captions.size
                        ) {

                            message =
                                "کپشن بعدی وجود ندارد"

                        } else {

                            val first =
                                captions[index]

                            val second =
                                captions[index + 1]

                            if (
                                second.startTime >
                                    first.endTime
                            ) {

                                message =
                                    "بین دو کپشن فاصله وجود دارد"

                            } else {

                                val merged =
                                    first.copy(
                                        text =
                                            first.text
                                                .trim() +
                                                " " +
                                                second.text
                                                    .trim(),
                                        endTime =
                                            maxOf(
                                                first.endTime,
                                                second.endTime
                                            )
                                    )

                                captions[index] =
                                    merged

                                captions.removeAt(
                                    index + 1
                                )

                                selectedCaptionId =
                                    merged.id

                                message =
                                    "دو کپشن ادغام شدند"
                            }
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
    onPlayerReady: (ExoPlayer) -> Unit
) {

    val context =
        androidx.compose.ui.platform.LocalContext.current

    val player =
        remember(uri) {

            ExoPlayer.Builder(context)
                .build()
                .apply {

                    setMediaItem(
                        MediaItem.fromUri(uri)
                    )

                    prepare()

                    seekTo(
                        currentPosition
                    )
                }
        }

    DisposableEffect(player) {

        onPlayerReady(player)

        onDispose {
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->

            PlayerView(ctx).apply {

                this.player = player
                useController = true
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(
                    RoundedCornerShape(12.dp)
                )
    )
}

@Composable
private fun Timeline(
    captions: List<Caption>,
    currentPosition: Long,
    duration: Long,
    onPositionClick: (Long) -> Unit
) {

    val safeDuration =
        duration.coerceAtLeast(1L)

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            text = "Timeline",
            color = Color.White,
            fontSize = 14.sp
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .clip(
                        RoundedCornerShape(8.dp)
                    )
                    .background(
                        Color(0xFF252525)
                    )
        ) {

            captions.forEach { caption ->

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
                            .padding(3.dp)
                            .clip(
                                RoundedCornerShape(
                                    5.dp
                                )
                            )
                            .background(
                                Color(0xFF6D4C41)
                            )
                )
            }

            val positionFraction =
                (
                    currentPosition.toFloat() /
                        safeDuration.toFloat()
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
                            Color(0xFFFFD54F)
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
                0f..safeDuration.toFloat(),
            modifier =
                Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CaptionEditor(
    captions: List<Caption>,
    currentPosition: Long,
    selectedCaptionId: Int?,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Int) -> Unit,
    onUpdate:
        (Int, (Caption) -> Caption) -> Unit,
    onSetStart: (Int) -> Unit,
    onSetEnd: (Int) -> Unit,
    onGoToStart: (Int) -> Unit,
    onGoToEnd: (Int) -> Unit,
    onSplit: (Int) -> Unit,
    onMerge: (Int) -> Unit
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
                text = "کپشن‌ها",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Button(
                onClick = onAdd
            ) {
                Text("＋ افزودن")
            }
        }

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        captions.forEach { caption ->

            CaptionCard(
                caption = caption,
                selected =
                    caption.id ==
                        selectedCaptionId,
                onSelect = {
                    onSelect(caption.id)
                },
                onDelete = {
                    onDelete(caption.id)
                },
                onUpdate = {
                    transform ->
                    onUpdate(
                        caption.id,
                        transform
                    )
                },
                onSetStart = {
                    onSetStart(caption.id)
                },
                onSetEnd = {
                    onSetEnd(caption.id)
                },
                onGoToStart = {
                    onGoToStart(caption.id)
                },
                onGoToEnd = {
                    onGoToEnd(caption.id)
                },
                onSplit = {
                    onSplit(caption.id)
                },
                onMerge = {
                    onMerge(caption.id)
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
    onGoToEnd: () -> Unit,
    onSplit: () -> Unit,
    onMerge: () -> Unit
) {

    var text by remember(
        caption.id,
        caption.text
    ) {
        mutableStateOf(
            caption.text
        )
    }

    var startText by remember(
        caption.id,
        caption.startTime
    ) {
        mutableStateOf(
            caption.startTime.toString()
        )
    }

    var endText by remember(
        caption.id,
        caption.endTime
    ) {
        mutableStateOf(
            caption.endTime.toString()
        )
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
                        Color(0xFF303030)
                    else
                        Color(0xFF1C1C1C)
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
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
                        Color(0xFFFFD54F),
                    fontSize = 13.sp
                )

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                TextButton(
                    onClick = onSelect
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
                        Modifier.weight(1f)
                )

                TextButton(
                    onClick = onDelete
                ) {
                    Text(
                        "حذف",
                        color =
                            Color(0xFFFF8A80)
                    )
                }
            }

            SimpleTextField(
                value = text,
                onValueChange = {
                    text = it

                    onUpdate { current ->
                        current.copy(
                            text = text
                        )
                    }
                },
                label = "متن کپشن"
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                SimpleTextField(
                    value = startText,
                    onValueChange = {
                        startText = it

                        it.toLongOrNull()
                            ?.let { value ->

                                if (
                                    value >= 0 &&
                                    value <
                                        caption.endTime
                                ) {

                                    onUpdate {
                                        it.copy(
                                            startTime =
                                                value
                                        )
                                    }
                                }
                            }
                    },
                    label = "شروع ms",
                    modifier =
                        Modifier.weight(1f)
                )

                SimpleTextField(
                    value = endText,
                    onValueChange = {
                        endText = it

                        it.toLongOrNull()
                            ?.let { value ->

                                if (
                                    value >
                                        caption.startTime
                                ) {

                                    onUpdate {
                                        it.copy(
                                            endTime =
                                                value
                                        )
                                    }
                                }
                            }
                    },
                    label = "پایان ms",
                    modifier =
                        Modifier.weight(1f)
                )
            }

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                OutlinedButton(
                    onClick = onSetStart,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("ثبت شروع")
                }

                OutlinedButton(
                    onClick = onSetEnd,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("ثبت پایان")
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                TextButton(
                    onClick = onGoToStart,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("برو شروع")
                }

                TextButton(
                    onClick = onGoToEnd,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("برو پایان")
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                OutlinedButton(
                    onClick = onSplit,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("Split")
                }

                OutlinedButton(
                    onClick = onMerge,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("Merge")
                }
            }

            Divider(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 8.dp
                        )
            )

            Text(
                text = "استایل",
                color = Color.White,
                fontSize = 15.sp
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
                onValueChange = { value ->
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

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

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
                        if (style.bold)
                            "✓ Bold"
                        else
                            "Bold"
                    )
                }

                TextButton(
                    onClick = {
                        onUpdate {
                            it.copy(
                                style =
                                    it.style.copy(
                                        highlightEnabled =
                                            !it.style
                                                .highlightEnabled
                                    )
                            )
                        }
                    }
                ) {
                    Text(
                        if (
                            style.highlightEnabled
                        )
                            "✓ Highlight"
                        else
                            "Highlight"
                    )
                }
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
                onValueChange = { value ->
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
                text = "رنگ متن",
                color =
                    Color.LightGray
            )

            ColorChoices(
                selected =
                    style.textColor,
                onSelected = { color ->
                    onUpdate {
                        it.copy(
                            style =
                                it.style.copy(
                                    textColor =
                                        color
                                )
                        )
                    }
                }
            )

            Text(
                text = "رنگ پس‌زمینه",
                color =
                    Color.LightGray
            )

            ColorChoices(
                selected =
                    style.backgroundColor,
                onSelected = { color ->
                    onUpdate {
                        it.copy(
                            style =
                                it.style.copy(
                                    backgroundColor =
                                        color
                                )
                        )
                    }
                }
            )

            Text(
                text = "رنگ Highlight",
                color =
                    Color.LightGray
            )

            ColorChoices(
                selected =
                    style.highlightColor,
                onSelected = { color ->
                    onUpdate {
                        it.copy(
                            style =
                                it.style.copy(
                                    highlightColor =
                                        color
                                )
                        )
                    }
                }
            )

            Text(
                text = "چیدمان",
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
                ).forEach { alignment ->

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
                            ) {
                                "✓ $alignment"
                            } else {
                                alignment
                            }
                        )
                    }
                }
            }

            Text(
                text = "Animation",
                color =
                    Color.LightGray
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                CaptionAnimation.entries
                    .forEach { animation ->

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
                                ) {
                                    "✓ ${animation.name}"
                                } else {
                                    animation.name
                                }
                            )
                        }
                    }
            }

            Text(
                text =
                    "مدت Animation: " +
                        style.animationDuration +
                        "ms",
                color =
                    Color.LightGray
            )

            Slider(
                value =
                    style.animationDuration
                        .toFloat(),
                onValueChange = { value ->
                    onUpdate {
                        it.copy(
                            style =
                                it.style.copy(
                                    animationDuration =
                                        value
                                            .roundToInt()
                                )
                        )
                    }
                },
                valueRange =
                    100f..1500f
            )

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )

            CaptionOverlay(
                caption = caption,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
            )
        }
    }
}

@Composable
private fun SimpleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {

    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(label)
        },
        modifier =
            modifier.fillMaxWidth(),
        singleLine = false
    )
}

@Composable
private fun ColorChoices(
    selected: Long,
    onSelected: (Long) -> Unit
) {

    val colors =
        listOf(
            0xFFFFFFFF,
            0xFF000000,
            0xFFFFD54F,
            0xFFFF5252,
            0xFF69F0AE,
            0xFF40C4FF,
            0xFFE040FB
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                ),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        colors.forEach { colorValue ->

            Button(
                onClick = {
                    onSelected(colorValue)
                },
                modifier =
                    Modifier.size(40.dp),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(0.dp)
            ) {

                Box(
                    modifier =
                        Modifier
                            .size(25.dp)
                            .clip(
                                RoundedCornerShape(
                                    50
                                )
                            )
                            .background(
                                Color(colorValue)
                            )
                )
            }
        }
    }
}

@Composable
private fun CaptionOverlay(
    caption: Caption,
    modifier: Modifier = Modifier
) {

    val style =
        caption.style

    val horizontalAlignment =
        when (style.alignment) {
            "LEFT" ->
                Alignment.CenterStart

            "RIGHT" ->
                Alignment.CenterEnd

            else ->
                Alignment.Center
        }

    val textAlign =
        when (style.alignment) {
            "LEFT" ->
                TextAlign.Left

            "RIGHT" ->
                TextAlign.Right

            else ->
                TextAlign.Center
        }

    Box(
        modifier =
            modifier
                .clip(
                    RoundedCornerShape(8.dp)
                )
                .background(
                    Color(0xFF181818)
                )
    ) {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(
                        Alignment.TopCenter
                    )
                    .padding(
                        top =
                            (
                                style.position *
                                    100
                                ).dp
                    )
        ) {

            Box(
                modifier =
                    Modifier.fillMaxWidth(),
                contentAlignment =
                    horizontalAlignment
            ) {

                AnimatedVisibility(
                    visible = true,
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
                        text = caption.text,
                        color =
                            Color(style.textColor),
                        fontSize =
                            style.fontSize.sp,
                        fontWeight =
                            if (style.bold)
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
                                    horizontal = 10.dp,
                                    vertical = 6.dp
                                )
                    )
                }
            }
        }
    }
}

private fun animationEnter(
    animation: CaptionAnimation,
    duration: Int
): EnterTransition {

    return when (animation) {

        CaptionAnimation.NONE ->
            fadeIn(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        )
            )

        CaptionAnimation.FADE ->
            fadeIn(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        )
            )

        CaptionAnimation.SLIDE_UP ->
            slideInVertically(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        ),
                initialOffsetY = {
                    it
                }
            ) +
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                duration
                            )
                )

        CaptionAnimation.SLIDE_DOWN ->
            slideInVertically(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        ),
                initialOffsetY = {
                    -it
                }
            ) +
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                duration
                            )
                )

        CaptionAnimation.SLIDE_LEFT ->
            slideInHorizontally(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        ),
                initialOffsetX = {
                    it
                }
            ) +
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                duration
                            )
                )

        CaptionAnimation.SLIDE_RIGHT ->
            slideInHorizontally(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        ),
                initialOffsetX = {
                    -it
                }
            ) +
                fadeIn(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                duration
                            )
                )
    }
}

private fun animationExit(
    animation: CaptionAnimation,
    duration: Int
): ExitTransition {

    return when (animation) {

        CaptionAnimation.NONE ->
            fadeOut(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        )
            )

        CaptionAnimation.FADE ->
            fadeOut(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        )
            )

        CaptionAnimation.SLIDE_UP ->
            slideOutVertically(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        ),
                targetOffsetY = {
                    -it
                }
            ) +
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                duration
                            )
                )

        CaptionAnimation.SLIDE_DOWN ->
            slideOutVertically(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        ),
                targetOffsetY = {
                    it
                }
            ) +
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                duration
                            )
                )

        CaptionAnimation.SLIDE_LEFT ->
            slideOutHorizontally(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        ),
                targetOffsetX = {
                    -it
                }
            ) +
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                duration
                            )
                )

        CaptionAnimation.SLIDE_RIGHT ->
            slideOutHorizontally(
                animationSpec =
                    androidx.compose.animation.core
                        .tween(
                            duration
                        ),
                targetOffsetX = {
                    it
                }
            ) +
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                duration
                            )
                )
    }
}

private fun updateCaption(
    captions: MutableList<Caption>,
    id: Int,
    transform: (Caption) -> Caption
) {

    val index =
        captions.indexOfFirst {
            it.id == id
        }

    if (index >= 0) {

        captions[index] =
            transform(
                captions[index]
            )
    }
}

private fun splitCaptionText(
    text: String,
    ratio: Float
): Pair<String, String>? {

    val clean =
        text.trim()

    if (clean.isBlank()) {
        return null
    }

    val words =
        clean.split(
            Regex("\\s+")
        )

    if (words.size >= 2) {

        val index =
            (
                words.size * ratio
            ).roundToInt()
                .coerceIn(
                    1,
                    words.size - 1
                )

        val first =
            words
                .take(index)
                .joinToString(" ")

        val second =
            words
                .drop(index)
                .joinToString(" ")

        return first to second
    }

    if (clean.length >= 2) {

        val index =
            (
                clean.length * ratio
            ).roundToInt()
                .coerceIn(
                    1,
                    clean.length - 1
                )

        return (
            clean.substring(
                0,
                index
            )
        ) to (
            clean.substring(index)
        )
    }

    return null
}

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
        normalized.split(
            Regex("\n\\s*\n")
        )

    val result =
        mutableListOf<ParsedSrtCaption>()

    for (block in blocks) {

        val lines =
            block.lines()
                .map {
                    it.trimEnd()
                }

        if (lines.isEmpty()) {
            continue
        }

        val timeLineIndex =
            lines.indexOfFirst {
                it.contains("-->")
            }

        if (timeLineIndex < 0) {
            continue
        }

        val timeParts =
            lines[timeLineIndex]
                .split("-->")

        if (timeParts.size < 2) {
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
                    .split(" ")
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
                .joinToString("\n")
                .trim()

        if (text.isBlank()) {
            continue
        }

        result.add(
            ParsedSrtCaption(
                text = text,
                startTime = start,
                endTime = end
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

    val regex =
        Regex(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,\\.](\\d{3})"
        )

    val match =
        regex.find(value)
            ?: return null

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

    return (
        hours * 3_600_000L +
            minutes * 60_000L +
            seconds * 1_000L +
            millis
        )
}

private fun formatSrtTime(
    milliseconds: Long
): String {

    val safe =
        milliseconds.coerceAtLeast(0L)

    val hours =
        safe / 3_600_000L

    val minutes =
        (
            safe % 3_600_000L
        ) / 60_000L

    val seconds =
        (
            safe % 60_000L
        ) / 1_000L

    val millis =
        safe % 1_000L

    return String.format(
        "%02d:%02d:%02d,%03d",
        hours,
        minutes,
        seconds,
        millis
    )
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

    return buildString {

        sorted.forEachIndexed {
                index,
                caption ->

            append(index + 1)
            append("\n")

            append(
                formatSrtTime(
                    caption.startTime
                )
            )

            append(" --> ")

            append(
                formatSrtTime(
                    caption.endTime
                )
            )

            append("\n")

            append(
                caption.text.trim()
            )

            append("\n\n")
        }
    }
}

private fun buildProjectJson(
    captions: List<Caption>,
    videoUri: Uri?,
    selectedCaptionId: Int?
): String {

    val root =
        JSONObject()

    root.put(
        "format",
        "CaptionsFA"
    )

    root.put(
        "version",
        1
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

    captions
        .sortedWith(
            compareBy<Caption> {
                it.startTime
            }.thenBy {
                it.endTime
            }
        )
        .forEach { caption ->

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

            array.put(item)
        }

    root.put(
        "captions",
        array
    )

    return root.toString(2)
}

private fun parseProjectJson(
    content: String
): LoadedProject {

    val root =
        JSONObject(content)

    if (
        root.optString(
            "format",
            "CaptionsFA"
        ) != "CaptionsFA"
    ) {
        throw IllegalArgumentException(
            "Invalid project"
        )
    }

    val savedVideoUri =
        if (
            root.isNull("videoUri")
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

    val selectedId =
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
            ).takeIf {
                it > 0
            }
        }

    val array =
        root.optJSONArray(
            "captions"
        ) ?: JSONArray()

    val result =
        mutableListOf<Caption>()

    val usedIds =
        mutableSetOf<Int>()

    var generatedId = 1

    for (
        index in
        0 until array.length()
    ) {

        val item =
            array.optJSONObject(
                index
            ) ?: continue

        val originalId =
            item.optInt(
                "id",
                generatedId
            )

        val id =
            if (
                originalId > 0 &&
                !usedIds.contains(
                    originalId
                )
            ) {
                originalId
            } else {

                while (
                    usedIds.contains(
                        generatedId
                    )
                ) {
                    generatedId++
                }

                generatedId
            }

        usedIds.add(id)

        generatedId =
            maxOf(
                generatedId,
                id + 1
            )

        val text =
            item.optString(
                "text",
                ""
            )

        val startTime =
            item.optLong(
                "startTime",
                0L
            )

        val endTime =
            item.optLong(
                "endTime",
                0L
            )

        if (
            text.isBlank() ||
            startTime < 0L ||
            endTime <= startTime
        ) {
            continue
        }

        val styleObject =
            item.optJSONObject(
                "style"
            )

        val animation =
            runCatching {

                CaptionAnimation.valueOf(
                    styleObject?.optString(
                        "animation",
                        CaptionAnimation.NONE.name
                    ) ?: CaptionAnimation.NONE.name
                )

            }.getOrDefault(
                CaptionAnimation.NONE
            )

        val style =
            CaptionStyle(

                fontSize =
                    (
                        styleObject?.optDouble(
                            "fontSize",
                            28.0
                        ) ?: 28.0
                    ).toFloat()
                        .coerceIn(
                            8f,
                            100f
                        ),

                textColor =
                    styleObject?.optLong(
                        "textColor",
                        0xFFFFFFFF
                    ) ?: 0xFFFFFFFF,

                backgroundColor =
                    styleObject?.optLong(
                        "backgroundColor",
                        0x99000000
                    ) ?: 0x99000000,

                bold =
                    styleObject?.optBoolean(
                        "bold",
                        false
                    ) ?: false,

                alignment =
                    styleObject?.optString(
                        "alignment",
                        "CENTER"
                    ) ?: "CENTER",

                position =
                    (
                        styleObject?.optDouble(
                            "position",
                            0.82
                        ) ?: 0.82
                    ).toFloat()
                        .coerceIn(
                            0.05f,
                            0.95f
                        ),

                highlightEnabled =
                    styleObject?.optBoolean(
                        "highlightEnabled",
                        false
                    ) ?: false,

                highlightColor =
                    styleObject?.optLong(
                        "highlightColor",
                        0xFFFFD54F
                    ) ?: 0xFFFFD54F,

                animation =
                    animation,

                animationDuration =
                    (
                        styleObject?.optInt(
                            "animationDuration",
                            350
                        ) ?: 350
                    ).coerceIn(
                        100,
                        1500
                    )
            )

        result.add(
            Caption(
                id = id,
                text = text,
                startTime = startTime,
                endTime = endTime,
                style = style
            )
        )
    }

    return LoadedProject(
        captions = result,
        videoUri = savedVideoUri,
        selectedCaptionId = selectedId
    )
}

private fun formatTime(
    milliseconds: Long
): String {

    val totalSeconds =
        milliseconds / 1000L

    val minutes =
        totalSeconds / 60L

    val seconds =
        totalSeconds % 60L

    val millis =
        milliseconds % 1000L

    return String.format(
        "%02d:%02d.%03d",
        minutes,
        seconds,
        millis
    )
}
