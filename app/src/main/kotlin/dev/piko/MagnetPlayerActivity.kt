package dev.piko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import dev.piko.ui.PikoApp
import dev.piko.ui.VideoPlayerHost
import dev.piko.ui.navigation.Screen
import dev.piko.ui.screens.player.MediampVideoPlayerScreen
import dev.piko.ui.theme.appearanceFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Full-size video playback launched from a floating magnet picker.
 * No main tab/navigation is rendered and only the picked cloud file is opened.
 */
class MagnetPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileId = intent.getStringExtra(EXTRA_FILE_ID).orEmpty()
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        if (fileId.isBlank()) { finish(); return }
        val app = PikoApplication.instance
        val appearances = app.sessionManager.appearanceFlow(app.platform.supportsDynamicColor)
        val initial = runBlocking { appearances.first() }
        setContent {
            val appearance by appearances.collectAsStateWithLifecycle(initial)
            PikoApp(
                services = app.services,
                platform = app.platform,
                appearance = appearance,
                videoPlayer = VideoPlayerHost.InApp { screen, onClose ->
                    MediampVideoPlayerScreen(
                        initialFileId = screen.fileId,
                        initialFileName = screen.fileName,
                        initialLocalPath = screen.localPath,
                        initialStartMillis = screen.startMillis,
                        externalPlaylist = app.magnetPlayback.playlist,
                        onBackClick = onClose,
                    )
                },
                externalPlayback = Screen.VideoPlayer(fileId, fileName),
                onExternalPlaybackDismiss = { finish() },
            )
        }
    }

    override fun onDestroy() {
        if (isFinishing) PikoApplication.instance.magnetPlayback.closePlayer()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FILE_ID = "dev.piko.magnet.FILE_ID"
        const val EXTRA_FILE_NAME = "dev.piko.magnet.FILE_NAME"
    }
}
