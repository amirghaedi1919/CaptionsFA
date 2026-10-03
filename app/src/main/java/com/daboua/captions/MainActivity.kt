package com.daboua.captions

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

data class Caption(
    val id: Int,
    val text: String,
    val startTime: Long,
    val endTime: Long
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

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        videoUri = uri
    }

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {

                Text(
                    text = "Captions FA",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Button(
                    onClick = {
                        videoPicker.launch("video/*")
                    },
                    modifier = Modifier.fillMaxWidth()
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
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Text(
                            text = "هنوز ویدئویی انتخاب نشده"
                        )

                    }

                } else {

                    VideoPreview(
                        uri = videoUri!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "کپشن‌ها",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {

                            captions.add(
                                Caption(
                                    id = nextCaptionId,
                                    text = "کپشن جدید",
                                    startTime = 0L,
                                    endTime = 3000L
                                )
                            )

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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Text(
                            text = "هنوز کپشنی اضافه نشده"
                        )

                    }

                } else {

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        items(
                            items = captions,
                            key = { it.id }
                        ) { caption ->

                            CaptionItem(
                                caption = caption,

                                onTextChange = { newText ->

                                    val index = captions.indexOfFirst {
                                        it.id == caption.id
                                    }

                                    if (index >= 0) {

                                        captions[index] =
                                            caption.copy(
                                                text = newText
                                            )
                                    }
                                },

                                onStartChange = { newStart ->

                                    val index = captions.indexOfFirst {
                                        it.id == caption.id
                                    }

                                    if (index >= 0) {

                                        captions[index] =
                                            caption.copy(
                                                startTime = newStart
                                            )
                                    }
                                },

                                onEndChange = { newEnd ->

                                    val index = captions.indexOfFirst {
                                        it.id == caption.id
                                    }

                                    if (index >= 0) {

                                        captions[index] =
                                            caption.copy(
                                                endTime = newEnd
                                            )
                                    }
                                },

                                onDelete = {

                                    captions.removeAll {
                                        it.id == caption.id
                                    }
                                }
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionItem(
    caption: Caption,
    onTextChange: (String) -> Unit,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onDelete: () -> Unit
) {

    var textValue by remember(caption.id, caption.text) {
        mutableStateOf(caption.text)
    }

    var startValue by remember(caption.id, caption.startTime) {
        mutableStateOf(caption.startTime.toString())
    }

    var endValue by remember(caption.id, caption.endTime) {
        mutableStateOf(caption.endTime.toString())
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            OutlinedTextField(
                value = textValue,
                onValueChange = {

                    textValue = it
                    onTextChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("متن کپشن")
                },
                singleLine = false
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                OutlinedTextField(
                    value = startValue,
                    onValueChange = { value ->

                        if (value.all { it.isDigit() }) {

                            startValue = value

                            value.toLongOrNull()?.let {
                                onStartChange(it)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
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

                        if (value.all { it.isDigit() }) {

                            endValue = value

                            value.toLongOrNull()?.let {
                                onEndChange(it)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    label = {
                        Text("پایان (ms)")
                    },
                    singleLine = true
                )
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
private fun VideoPreview(
    uri: Uri,
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
