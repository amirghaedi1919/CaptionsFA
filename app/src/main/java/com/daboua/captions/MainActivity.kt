package com.daboua.captions

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CaptionsFaApp()
        }
    }
}

@Composable
private fun CaptionsFaApp() {

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

    val videoPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.GetContent()
        ) { uri ->

            videoUri = uri
            currentPosition = 0L
            duration = 0L
            selectedCaptionId = null
        }

    LaunchedEffect(playerReference) {

        while (playerReference != null) {

            val player = playerReference

            currentPosition =
                player?.currentPosition ?: 0L

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

            currentPosition >= it.startTime &&
                currentPosition <= it.endTime
        }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color =
                MaterialTheme
                    .colorScheme
                    .background
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {

                Text(
                    text = "Captions FA",
                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Button(
                    onClick = {
                        videoPicker.launch("video/*")
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text("انتخاب ویدئو")
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                if (videoUri == null) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalAlignment =
                            Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.Center
                    ) {

                        Text(
                            text =
                                "هنوز ویدئویی انتخاب نشده"
                        )
                    }

                } else {

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {

                        VideoPreview(
                            uri = videoUri!!,
                            onPlayerReady = { player ->
                                playerReference = player
                            },
                            modifier =
                                Modifier.fillMaxSize()
                        )

                        if (activeCaption != null) {

                            CaptionOverlay(
                                caption = activeCaption,
                                modifier =
                                    Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
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

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                if (duration > 0L) {

                    Timeline(
                        captions = captions,
                        duration = duration,
                        selectedCaptionId =
                            selectedCaptionId,
                        onCaptionSelected = { id ->

                            selectedCaptionId = id

                            val caption =
                                captions.firstOrNull {
                                    it.id == id
                                }

                            if (caption != null) {

                                playerReference?.seekTo(
                                    caption.startTime
                                )

                                currentPosition =
                                    caption.startTime
                            }
                        }
                    )
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.Center
                ) {

                    Button(
                        onClick = {

                            val newPosition =
                                (
                                    currentPosition - 5000L
                                ).coerceAtLeast(0L)

                            playerReference?.seekTo(
                                newPosition
                            )

                            currentPosition =
                                newPosition
                        }
                    ) {
                        Text("-5s")
                    }

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Button(
                        onClick = {

                            playerReference?.let { player ->

                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }
                        }
                    ) {

                        Text(
                            if (
                                playerReference?.isPlaying ==
                                    true
                            ) {
                                "توقف"
                            } else {
                                "پخش"
                            }
                        )
                    }

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Button(
                        onClick = {

                            val newPosition =
                                (
                                    currentPosition + 5000L
                                ).coerceAtMost(
                                    duration
                                )

                            playerReference?.seekTo(
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
                    modifier = Modifier.height(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = "کپشن‌ها",
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
                                    currentPosition + 3000L
                                ).coerceAtMost(
                                    if (duration > 0L) {
                                        duration
                                    } else {
                                        currentPosition + 3000L
                                    }
                                )

                            val newCaption =
                                Caption(
                                    id = nextCaptionId,
                                    text = "کپشن جدید",
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
                        }
                    ) {
                        Text("افزودن")
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                if (captions.isEmpty()) {

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
                            items = captions,
                            key = { it.id }
                        ) { caption ->

                            CaptionItem(
                                caption = caption,
                                selected =
                                    selectedCaptionId ==
                                        caption.id,

                                onSelect = {

                                    selectedCaptionId =
                                        caption.id

                                    playerReference?.seekTo(
                                        caption.startTime
                                    )

                                    currentPosition =
                                        caption.startTime
                                },

                                onTextChange = { newText ->

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {
                                        copy(
                                            text = newText
                                        )
                                    }
                                },

                                onStartChange = { newStart ->

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        val safeStart =
                                            newStart.coerceAtLeast(
                                                0L
                                            )

                                        val safeEnd =
                                            endTime.coerceAtLeast(
                                                safeStart + 1L
                                            )

                                        copy(
                                            startTime =
                                                safeStart,
                                            endTime =
                                                safeEnd
                                        )
                                    }
                                },

                                onEndChange = { newEnd ->

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        val safeEnd =
                                            newEnd.coerceAtLeast(
                                                startTime + 1L
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
                                            endTime.coerceAtLeast(
                                                safeStart + 1L
                                            )

                                        copy(
                                            startTime =
                                                safeStart,
                                            endTime =
                                                safeEnd
                                        )
                                    }
                                },

                                onSetEnd = {

                                    updateCaption(
                                        captions,
                                        caption.id
                                    ) {

                                        val safeEnd =
                                            currentPosition
                                                .coerceAtLeast(
                                                    startTime + 1L
                                                )

                                        copy(
                                            endTime =
                                                safeEnd
                                        )
                                    }
                                },

                                onGoToStart = {

                                    playerReference?.seekTo(
                                        caption.startTime
                                    )

                                    currentPosition =
                                        caption.startTime
                                },

                                onGoToEnd = {

                                    playerReference?.seekTo(
                                        caption.endTime
                                    )

                                    currentPosition =
                                        caption.endTime
                                },

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
                                },

                                onStyleChange = { transform ->

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

private fun updateCaption(
    captions: MutableList<Caption>,
    id: Int,
    transform: Caption.() -> Caption
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

@Composable
private fun CaptionOverlay(
    caption: Caption,
    modifier: Modifier
) {

    val style = caption.style

    val textColor =
        Color(style.textColor)

    val backgroundColor =
        Color(style.backgroundColor)

    val highlightColor =
        Color(style.highlightColor)

    val enterTransition: EnterTransition
    val exitTransition: ExitTransition

    when (style.animation) {

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
                        androidx.compose.animation.core
                            .tween(
                                durationMillis =
                                    style.animationDuration
                            )
                )

            exitTransition =
                fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
                                durationMillis =
                                    style.animationDuration
                            )
                )
        }

        CaptionAnimation.SLIDE_UP -> {

            enterTransition =
                slideInVertically(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(
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
                        androidx.compose.animation.core
                            .tween(
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
                        androidx.compose.animation.core
                            .tween(
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
                        androidx.compose.animation.core
                            .tween(
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
                        androidx.compose.animation.core
                            .tween(
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
                        androidx.compose.animation.core
                            .tween(
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
                        androidx.compose.animation.core
                            .tween(
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
                        androidx.compose.animation.core
                            .tween(
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
        modifier = modifier
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
            modifier = Modifier
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
                enter = enterTransition,
                exit = exitTransition
            ) {

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(10.dp)
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
                        text = caption.text,
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
                            if (style.bold) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        textAlign =
                            when (style.alignment) {
                                0 -> TextAlign.Start
                                2 -> TextAlign.End
                                else -> TextAlign.Center
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
    onDelete: () -> Unit,
    onStyleChange:
        ((CaptionStyle.() -> CaptionStyle) -> Unit)
) {

    var textValue by remember(
        caption.id,
        caption.text
    ) {
        mutableStateOf(caption.text)
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
                onClick = onSelect,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    if (selected) {
                        "کپشن انتخاب شده"
                    } else {
                        "انتخاب این کپشن"
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            OutlinedTextField(
                value = textValue,
                onValueChange = {

                    textValue = it
                    onTextChange(it)
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text("متن کپشن")
                }
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                OutlinedTextField(
                    value = startValue,
                    onValueChange = { value ->

                        if (
                            value.all {
                                it.isDigit()
                            }
                        ) {

                            startValue = value

                            value.toLongOrNull()
                                ?.let {
                                    onStartChange(it)
                                }
                        }
                    },
                    modifier =
                        Modifier.weight(1f),
                    label = {
                        Text("شروع (ms)")
                    },
                    singleLine = true
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                OutlinedTextField(
                    value = endValue,
                    onValueChange = { value ->

                        if (
                            value.all {
                                it.isDigit()
                            }
                        ) {

                            endValue = value

                            value.toLongOrNull()
                                ?.let {
                                    onEndChange(it)
                                }
                        }
                    },
                    modifier =
                        Modifier.weight(1f),
                    label = {
                        Text("پایان (ms)")
                    },
                    singleLine = true
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Button(
                    onClick = onSetStart,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("ثبت شروع")
                }

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Button(
                    onClick = onSetEnd,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("ثبت پایان")
                }
            }

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                TextButton(
                    onClick = onGoToStart,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("↤ شروع")
                }

                TextButton(
                    onClick = onGoToEnd,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("پایان ↦")
                }
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Button(
                onClick = {
                    showStyle = !showStyle
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    if (showStyle) {
                        "بستن تنظیمات استایل"
                    } else {
                        "تنظیمات استایل"
                    }
                )
            }

            if (showStyle) {

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        "اندازه فونت: " +
                            "${caption.style.fontSize.toInt()}"
                )

                Slider(
                    value =
                        caption.style.fontSize,
                    onValueChange = { value ->

                        onStyleChange {
                            copy(
                                fontSize = value
                            )
                        }
                    },
                    valueRange =
                        14f..64f
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "موقعیت عمودی کپشن"
                )

                Slider(
                    value =
                        caption.style.position,
                    onValueChange = { value ->

                        onStyleChange {
                            copy(
                                position = value
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
                    modifier = Modifier.height(8.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = "متن ضخیم",
                        modifier =
                            Modifier.weight(1f)
                    )

                    Switch(
                        checked =
                            caption.style.bold,
                        onCheckedChange = { checked ->

                            onStyleChange {
                                copy(
                                    bold = checked
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
                        text = "هایلایت",
                        modifier =
                            Modifier.weight(1f)
                    )

                    Switch(
                        checked =
                            caption.style.highlightEnabled,
                        onCheckedChange = { checked ->

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
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "تراز متن"
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    TextButton(
                        onClick = {

                            onStyleChange {
                                copy(
                                    alignment = 0
                                )
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("چپ")
                    }

                    TextButton(
                        onClick = {

                            onStyleChange {
                                copy(
                                    alignment = 1
                                )
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("وسط")
                    }

                    TextButton(
                        onClick = {

                            onStyleChange {
                                copy(
                                    alignment = 2
                                )
                            }
                        },
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text("راست")
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "رنگ متن"
                )

                ColorChoices(
                    selectedColor =
                        caption.style.textColor,
                    colors =
                        listOf(
                            0xFFFFFFFF,
                            0xFFFFEB3B,
                            0xFFFF9800,
                            0xFFFF5252,
                            0xFF69F0AE,
                            0xFF40C4FF
                        ),
                    onColorSelected = { color ->

                        onStyleChange {
                            copy(
                                textColor = color
                            )
                        }
                    }
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "رنگ پس‌زمینه"
                )

                ColorChoices(
                    selectedColor =
                        caption.style.backgroundColor,
                    colors =
                        listOf(
                            0x99000000,
                            0xCC000000,
                            0x99FFFFFF,
                            0x990D47A1,
                            0x99004640,
                            0x99B71C1C
                        ),
                    onColorSelected = { color ->

                        onStyleChange {
                            copy(
                                backgroundColor =
                                    color
                            )
                        }
                    }
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "رنگ هایلایت"
                )

                ColorChoices(
                    selectedColor =
                        caption.style.highlightColor,
                    colors =
                        listOf(
                            0xFFFFD54F,
                            0xFFFF5252,
                            0xFF69F0AE,
                            0xFF40C4FF,
                            0xFFFFFFFF,
                            0xFFFF9800
                        ),
                    onColorSelected = { color ->

                        onStyleChange {
                            copy(
                                highlightColor =
                                    color
                            )
                        }
                    }
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "انیمیشن کپشن",
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                AnimationChoice(
                    title = "بدون انیمیشن",
                    selected =
                        caption.style.animation ==
                            CaptionAnimation.NONE,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation.NONE
                            )
                        }
                    }
                )

                AnimationChoice(
                    title = "Fade",
                    selected =
                        caption.style.animation ==
                            CaptionAnimation.FADE,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation.FADE
                            )
                        }
                    }
                )

                AnimationChoice(
                    title = "Slide Up",
                    selected =
                        caption.style.animation ==
                            CaptionAnimation.SLIDE_UP,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation.SLIDE_UP
                            )
                        }
                    }
                )

                AnimationChoice(
                    title = "Slide Down",
                    selected =
                        caption.style.animation ==
                            CaptionAnimation.SLIDE_DOWN,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation.SLIDE_DOWN
                            )
                        }
                    }
                )

                AnimationChoice(
                    title = "Slide Left",
                    selected =
                        caption.style.animation ==
                            CaptionAnimation.SLIDE_LEFT,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation.SLIDE_LEFT
                            )
                        }
                    }
                )

                AnimationChoice(
                    title = "Slide Right",
                    selected =
                        caption.style.animation ==
                            CaptionAnimation.SLIDE_RIGHT,
                    onClick = {

                        onStyleChange {
                            copy(
                                animation =
                                    CaptionAnimation.SLIDE_RIGHT
                            )
                        }
                    }
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text =
                        "مدت انیمیشن: " +
                            "${caption.style.animationDuration} ms"
                )

                Slider(
                    value =
                        caption.style
                            .animationDuration
                            .toFloat(),
                    onValueChange = { value ->

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

                    Text("100ms")
                    Text("1500ms")
                }
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            TextButton(
                onClick = onDelete
            ) {
                Text("حذف کپشن")
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
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Text(
            if (selected) {
                "✓ $title"
            } else {
                title
            }
        )
    }

    Spacer(
        modifier = Modifier.height(4.dp)
    )
}

@Composable
private fun ColorChoices(
    selectedColor: Long,
    colors: List<Long>,
    onColorSelected: (Long) -> Unit
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        colors.forEach { colorValue ->

            val selected =
                colorValue == selectedColor

            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(42.dp)
                    .clip(
                        RoundedCornerShape(8.dp)
                    )
                    .background(
                        Color(colorValue)
                    )
                    .border(
                        width =
                            if (selected) {
                                3.dp
                            } else {
                                1.dp
                            },
                        color =
                            if (selected) {
                                MaterialTheme
                                    .colorScheme
                                    .primary
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .outline
                            },
                        shape =
                            RoundedCornerShape(8.dp)
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
    onCaptionSelected: (Int) -> Unit
) {

    val safeDuration =
        duration.coerceAtLeast(1L)

    Column(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .clip(
                    RoundedCornerShape(10.dp)
                )
                .background(
                    MaterialTheme
                        .colorScheme
                        .surfaceVariant
                )
                .padding(6.dp)
        ) {

            captions.forEach { caption ->

                val startFraction =
                    (
                        caption.startTime.toFloat() /
                            safeDuration.toFloat()
                    ).coerceIn(
                        0f,
                        1f
                    )

                val endFraction =
                    (
                        caption.endTime.toFloat() /
                            safeDuration.toFloat()
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
                    modifier = Modifier
                        .fillMaxWidth(
                            widthFraction
                        )
                        .height(58.dp)
                        .padding(
                            start =
                                (
                                    startFraction * 1000
                                ).dp
                        )
                        .clip(
                            RoundedCornerShape(8.dp)
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
                                RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            onCaptionSelected(
                                caption.id
                            )
                        }
                ) {

                    Text(
                        text = caption.text,
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
            modifier = Modifier.height(4.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                text = "00:00.000",
                style =
                    MaterialTheme
                        .typography
                        .labelSmall
            )

            Text(
                text = formatTime(duration),
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
    onPlayerReady: (ExoPlayer) -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current

    val player = remember(uri) {

        ExoPlayer
            .Builder(context)
            .build()
            .apply {

                setMediaItem(
                    MediaItem.fromUri(uri)
                )

                prepare()

                playWhenReady = false
            }
    }

    LaunchedEffect(player) {
        onPlayerReady(player)
    }

    DisposableEffect(player) {

        onDispose {
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,

        factory = { viewContext ->

            PlayerView(viewContext).apply {

                this.player = player

                useController = true
            }
        }
    )
}

private fun formatTime(
    milliseconds: Long
): String {

    val safe =
        milliseconds.coerceAtLeast(0L)

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
