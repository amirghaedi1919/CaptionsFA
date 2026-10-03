package com.daboua.captions

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Captions FA",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
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
                    modifier = Modifier.height(16.dp)
                )

                if (videoUri == null) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Text(
                            text = "هنوز ویدئویی انتخاب نشده"
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = "برای شروع یک ویدئو انتخاب کنید",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                } else {

                    VideoPreview(
                        uri = videoUri!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    )
                }
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
