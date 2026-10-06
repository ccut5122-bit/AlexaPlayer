package com.alexaplayer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.di.AppContainer
import com.alexaplayer.ui.navigation.AlexaPlayerNavHost
import kotlinx.coroutines.launch

/**
 * Asks for audio access once at start up. Everything the app does works without an account, but
 * without the media permission there is simply nothing to show, so the gate explains itself
 * instead of rendering an empty library.
 */
@Composable
fun AlexaPlayerRoot(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val context = LocalContext.current
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var hasAudioAccess by remember { mutableStateOf(context.hasAudioPermission()) }
        var asked by remember { mutableStateOf(false) }

        val audioLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasAudioAccess = granted
            asked = true
            if (granted) {
                // The permission gate only appears before the first successful scan, so kick one
                // off here or the library would sit empty until the app is restarted.
                scope.launch {
                    runCatching { container.libraryRepository.rescan() }
                }
            }
        }

        val notificationLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) {}

        LaunchedEffect(Unit) {
            if (!hasAudioAccess) {
                asked = true
                audioLauncher.launch(context.audioPermission())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (hasAudioAccess) {
            AlexaPlayerNavHost(container = container, modifier = modifier)
        } else {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier
                    .fillMaxSize()
                    .padding(AlexaSpacing.xl),
            ) {
                EmptyState(
                    iconRes = R.drawable.ic_shield,
                    title = stringResource(R.string.permission_title),
                    body = stringResource(
                        if (asked) R.string.permission_denied_body else R.string.permission_body,
                    ),
                    action = {
                        if (asked && !context.canAskForAudioPermission()) {
                            PrimaryButton(
                                text = stringResource(R.string.permission_open_settings),
                                onClick = { context.openAppSettings() },
                            )
                        } else {
                            PrimaryButton(
                                text = stringResource(R.string.permission_grant),
                                onClick = { audioLauncher.launch(context.audioPermission()) },
                            )
                        }
                    },
                )
                Text(
                    text = stringResource(R.string.permission_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = AlexaSpacing.lg),
                )
            }
        }
    }
}

/**
 * `READ_MEDIA_AUDIO` only exists from Android 13. Requesting it on 12L or older resolves to
 * nothing and the request is silently dropped, which left the app stuck on this gate forever.
 */
private fun Context.audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

/**
 * After two denials the system stops showing the dialog, so the only way forward is app settings.
 */
private fun Context.canAskForAudioPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        ContextCompat.shouldShowRequestPermissionRationale(this, audioPermission())

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
