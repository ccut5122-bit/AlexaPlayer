package com.alexaplayer.ui.screen.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.model.AudioFocusBehaviour
import com.alexaplayer.core.model.ThemeMode
import com.alexaplayer.data.prefs.Settings
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.SectionHeader

@Composable
fun SettingsScreen(
    settings: Settings,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)?,
    onThemeMode: (ThemeMode) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onGapless: (Boolean) -> Unit,
    onResumePlayback: (Boolean) -> Unit,
    onAudioFocus: (AudioFocusBehaviour) -> Unit,
    onNotification: (Boolean) -> Unit,
    onVideoQuality: (Int) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
            ),
        ) {
            item(key = "appearance_header") {
                SectionHeader(
                    title = stringResource(R.string.settings_appearance),
                    modifier = Modifier.padding(
                        start = AlexaSpacing.lg,
                        end = AlexaSpacing.lg,
                        top = AlexaSpacing.md,
                    ),
                )
            }
            item(key = "theme") {
                Column(modifier = Modifier.padding(horizontal = AlexaSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = AlexaSpacing.sm),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm)) {
                        items(ThemeMode.entries.toList()) { mode ->
                            ThemeChip(
                                label = stringResource(mode.labelRes),
                                selected = settings.themeMode == mode,
                                onClick = { onThemeMode(mode) },
                            )
                        }
                    }
                }
            }
            item(key = "amoled") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_amoled),
                    subtitle = stringResource(R.string.settings_amoled_summary),
                    checked = settings.amoled,
                    onCheckedChange = onAmoled,
                )
            }
            item(key = "dynamic") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_summary),
                    checked = settings.dynamicColor,
                    onCheckedChange = onDynamicColor,
                )
            }

            item(key = "playback_header") {
                SectionHeader(
                    title = stringResource(R.string.settings_playback),
                    modifier = Modifier.padding(
                        start = AlexaSpacing.lg,
                        end = AlexaSpacing.lg,
                        top = AlexaSpacing.xl,
                    ),
                )
            }
            item(key = "gapless") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_gapless),
                    subtitle = stringResource(R.string.settings_gapless_summary),
                    checked = settings.gaplessPlayback,
                    onCheckedChange = onGapless,
                )
            }
            item(key = "resume") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_resume),
                    subtitle = stringResource(R.string.settings_resume_summary),
                    checked = settings.resumePlayback,
                    onCheckedChange = onResumePlayback,
                )
            }
            item(key = "focus_header") {
                SectionHeader(
                    title = stringResource(R.string.settings_audio_focus),
                    modifier = Modifier.padding(
                        start = AlexaSpacing.lg,
                        end = AlexaSpacing.lg,
                        top = AlexaSpacing.xl,
                    ),
                )
            }
            item(key = "focus") {
                Column(modifier = Modifier.padding(horizontal = AlexaSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.settings_audio_focus_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AlexaSpacing.sm),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm)) {
                        items(AudioFocusBehaviour.entries.toList()) { behaviour ->
                            ThemeChip(
                                label = stringResource(behaviour.labelRes),
                                selected = settings.audioFocusBehaviour == behaviour,
                                onClick = { onAudioFocus(behaviour) },
                            )
                        }
                    }
                }
            }
            item(key = "notification") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notification),
                    subtitle = stringResource(R.string.settings_notification_summary),
                    checked = settings.mediaNotificationEnabled,
                    onCheckedChange = onNotification,
                )
            }

            item(key = "video_quality") {
                Column(modifier = Modifier.padding(horizontal = AlexaSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.settings_video_quality),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_video_quality_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = AlexaSpacing.sm),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm)) {
                        items(VIDEO_QUALITIES) { height ->
                            ThemeChip(
                                label = "$height" + "p",
                                selected = settings.videoQuality == height,
                                onClick = { onVideoQuality(height) },
                            )
                        }
                    }
                }
            }

            item(key = "about_header") {
                SectionHeader(
                    title = stringResource(R.string.settings_about),
                    modifier = Modifier.padding(
                        start = AlexaSpacing.lg,
                        end = AlexaSpacing.lg,
                        top = AlexaSpacing.xl,
                    ),
                )
            }
            item(key = "about") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AlexaSpacing.xs),
                    modifier = Modifier
                        .padding(horizontal = AlexaSpacing.lg)
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAbout)
                        .padding(vertical = AlexaSpacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(AlexaRadius.md))
            .background(background)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
