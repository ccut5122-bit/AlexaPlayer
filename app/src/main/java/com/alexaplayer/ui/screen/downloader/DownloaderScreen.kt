package com.alexaplayer.ui.screen.downloader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.component.SecondaryButton
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.data.youtube.ActiveDownload
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.SearchField
import com.alexaplayer.ui.viewmodel.DownloaderViewModel.DownloaderUiState

private val QUALITIES = listOf(720, 1080, 1440, 2160)

/**
 * Offline grabber: a YouTube box (video or audio, with a resolution) and an Instagram box
 * (reel / video / photo). Both write into Download/AlexaPlayer through the same yt-dlp
 * bridge the YouTube tab uses.
 */
@Composable
fun DownloaderScreen(
    state: DownloaderUiState,
    contentPadding: PaddingValues,
    onYoutubeUrlChange: (String) -> Unit,
    onInstagramUrlChange: (String) -> Unit,
    onQuality: (Int) -> Unit,
    onDownloadYoutube: (String) -> Unit,
    onDownloadInstagram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(title = stringResource(R.string.downloader_title))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = AlexaSpacing.sm,
                bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
            ),
        ) {
            item(key = "yt") {
                SectionHeader(
                    title = stringResource(R.string.downloader_youtube_section),
                    subtitle = stringResource(R.string.downloader_subtitle),
                )
                SearchField(
                    value = state.youtubeUrl,
                    onValueChange = onYoutubeUrlChange,
                    placeholder = stringResource(R.string.downloader_youtube_hint),
                )
                Spacer(Modifier.height(AlexaSpacing.xs))
                Text(
                    text = stringResource(R.string.downloader_quality),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AlexaSpacing.lg),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
                ) {
                    QUALITIES.forEach { q ->
                        FilterChip(
                            selected = state.quality == q,
                            onClick = { onQuality(q) },
                            label = { Text(qualityLabel(q)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AlexaSpacing.lg),
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.youtube_download_video),
                        onClick = { onDownloadYoutube("video") },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.youtube_download_audio),
                        onClick = { onDownloadYoutube("audio") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "ig") {
                SectionHeader(
                    title = stringResource(R.string.downloader_instagram_section),
                    subtitle = null,
                )
                SearchField(
                    value = state.instagramUrl,
                    onValueChange = onInstagramUrlChange,
                    placeholder = stringResource(R.string.downloader_instagram_hint),
                )
                PrimaryButton(
                    text = stringResource(R.string.downloader_get),
                    onClick = onDownloadInstagram,
                    modifier = Modifier.padding(horizontal = AlexaSpacing.lg),
                )
            }

            if (state.downloads.isNotEmpty()) {
                item(key = "dl_header") {
                    SectionHeader(
                        title = stringResource(R.string.downloader_saved),
                        subtitle = null,
                    )
                }
                items(state.downloads, key = { it.token }) { download ->
                    DownloadRow(download = download)
                }
            }

            state.message?.let { message ->
                item(key = "msg") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadRow(download: ActiveDownload) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = download.path?.substringAfterLast('/') ?: download.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    download.failed -> stringResource(R.string.downloader_failed)
                    download.done -> stringResource(R.string.youtube_download_done)
                    else -> "${download.percent}%"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (download.failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (!download.done && !download.failed) {
            Spacer(Modifier.height(AlexaSpacing.xs))
            LinearProgressIndicator(
                progress = { download.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun qualityLabel(height: Int): String = if (height >= 2160) "4K" else "${height}p"
