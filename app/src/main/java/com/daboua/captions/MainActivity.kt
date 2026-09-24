package com.daboua.captions

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

data class Caption(
    val text: String,
    val start: Long,
    val end: Long,
    val highlight: Boolean = false
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CaptionsApp()
        }
    }
}

@Composable
fun CaptionsApp() {

    var videoUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var selected by remember {
        mutableIntStateOf(0)
    }

    var style by remember {
        mutableIntStateOf(0)
    }

    var captions by remember {

        mutableStateOf(
            listOf(
                Caption(
                    "اینجا متن زیرنویس قرار می‌گیرد",
                    0,
                    3000
                ),
                Caption(
                    "برای شروع یک ویدئو انتخاب کنید",
                    3000,
                    6000
                )
            )
        )
    }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) {
            videoUri = it
        }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0E0E))
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = "Captions FA",
                    fontSize = 22.sp
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        picker.launch("video/*")
                    }
                ) {
                    Text("انتخاب ویدئو")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(10.dp)
            ) {

                if (videoUri != null) {

                    VideoPlayer(videoUri!!)

                } else {

                    Text(
                        text = "ویدئو انتخاب نشده",
                        modifier =
                            Modifier.align(
                                Alignment.Center
                            ),
                        color = Color.Gray
                    )
                }

                if (captions.isNotEmpty()) {

                    val current =
                        captions[selected]

                    Text(
                        text = current.text,

                        modifier =
                            Modifier
                                .align(
                                    Alignment.BottomCenter
                                )
                                .padding(18.dp)
                                .background(
                                    if (style == 1)
                                        Color.Black.copy(.75f)
                                    else
                                        Color.Transparent,

                                    RoundedCornerShape(8.dp)
                                )
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 8.dp
                                ),

                        color =
                            if (current.highlight)
                                Color.Yellow
                            else
                                Color.White,

                        fontSize =
                            if (style == 2)
                                25.sp
                            else
                                20.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {

                        val list =
                            captions.toMutableList()

                        val item =
                            list[selected]

                        list[selected] =
                            item.copy(
                                highlight =
                                    !item.highlight
                            )

                        captions = list
                    }
                ) {
                    Text("Highlight")
                }

                Button(
                    onClick = {
                        style = (style + 1) % 3
                    }
                ) {
                    Text("Style")
                }

                Button(
                    onClick = {

                        val list =
                            captions.toMutableList()

                        val item =
                            list[selected]

                        list.add(
                            selected + 1,

                            Caption(
                                "زیرنویس جدید",
                                item.end,
                                item.end + 2000
                            )
                        )

                        captions = list

                        selected++
                    }
                ) {
                    Text("+ زیرنویس")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .padding(10.dp)
            ) {

                itemsIndexed(captions) {
                        index,
                        caption ->

                    Card(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selected = index
                                },

                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (index == selected)
                                        Color(0xFF292929)
                                    else
                                        Color(0xFF191919)
                            )
                    ) {

                        Column(
                            modifier =
                                Modifier.padding(12.dp)
                        ) {

                            Text(
                                text =
                                    "${time(caption.start)} → ${time(caption.end)}",

                                color = Color.Gray,
                                fontSize = 12.sp
                            )

                            Spacer(
                                Modifier.height(5.dp)
                            )

                            OutlinedTextField(

                                value =
                                    caption.text,

                                onValueChange = { value ->

                                    val list =
                                        captions.toMutableList()

                                    list[index] =
                                        caption.copy(
                                            text = value
                                        )

                                    captions = list
                                },

                                modifier =
                                    Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri) {

    val context =
        LocalContext.current

    val player =
        remember(uri) {

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

        factory = {

            PlayerView(it).apply {

                player = player

                useController = true
            }
        },

        modifier =
            Modifier.fillMaxSize()
    )
}

fun time(ms: Long): String {

    val seconds =
        ms / 1000

    val minutes =
        seconds / 60

    val remaining =
        seconds % 60

    return "%02d:%02d".format(
        minutes,
        remaining
    )
}
