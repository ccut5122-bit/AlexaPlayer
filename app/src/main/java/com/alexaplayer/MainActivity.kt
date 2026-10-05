package com.alexaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexaplayer.core.designsystem.theme.AlexaPlayerTheme
import com.alexaplayer.ui.AlexaPlayerRoot

class MainActivity : ComponentActivity() {

    private val container get() = (application as AlexaPlayerApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bind to the media session up front so the mini player is populated by the time
        // the first frame is drawn.
        container.playerConnection.connect()

        setContent {
            val settings by container.settings.collectAsStateWithLifecycle()
            AlexaPlayerTheme(
                themeMode = settings.themeMode,
                amoled = settings.amoled,
                dynamicColor = settings.dynamicColor,
            ) {
                AlexaPlayerRoot(container = container)
            }
        }
    }

    override fun onDestroy() {
        // Only tear the session down when the task is really going away; a rotation or a
        // configuration change must not interrupt playback.
        if (isFinishing) {
            container.playerConnection.release()
        }
        super.onDestroy()
    }
}
