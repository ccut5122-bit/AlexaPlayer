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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.component.SecondaryButton
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.util.DurationFormatter
import com.alexaplayer.data.youtube.ActiveDownload
import com.alexaplayer.data.youtube.FetchedMedia
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.SearchField
import com.alexaplayer.ui.viewmodel.DownloaderViewModel.DownloaderUiState

private val QUALITIES = listOf(720, 1080, 1440, 2160)

/**
 * One link box for everything: paste a YouTube or Instagram URL, Fetch to preview it,
 * then Play it full-screen or download the video/audio. The same resolution chips apply to
 * both playback and downloads.
 */
@Composable
fun DownloaderScreen(
    state: DownloaderUiState,
    contentPadding: PaddingValues,
    onLinkChange: (String) -> Unit,
    onFetch: () -> Unit,
    onQuality: (Int) -> Unit,
    onPlay: () -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadAudio: () -> Unit,
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
            item(key = "input") {
                Text(
                    text = stringResource(R.string.downloader_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AlexaSpacing.lg),
                )
                SearchField(
                    value = state.link,
                    onValueChange = onLinkChange,
                    placeholder = stringResource(R.string.downloader_link_hint),
                    onSubmit = onFetch,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AlexaSpacing.lg),
                ) {
                    PrimaryButton(
                        text = if (state.fetching) {
                            stringResource(R.string.downloader_fetching)
                        } else {
                            stringResource(R.string.downloader_fetch)
                        },
                        onClick = onFetch,
                        enabled = !state.fetching,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.fetching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.md),
                    )
                }
            }

            state.media?.let { media ->
                item(key = "media") {
                    MediaCard(
                        media = media,
                        quality = state.quality,
                        resolvingPlay = state.resolvingPlay,
                        onQuality = onQuality,
                        onPlay = onPlay,
                        onDownloadVideo = onDownloadVideo,
                        onDownloadAudio = onDownloadAudio,
                    )
                }
            }

            if (state.downloads.isNotEmpty()) {
                item(key = "dl_title") {
                    Text(
                        text = stringResource(R.string.downloader_downloads_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
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
private fun MediaCard(
    media: FetchedMedia,
    quality: Int,
    resolvingPlay: Boolean,
    onQuality: (Int) -> Unit,
    onPlay: () -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadAudio: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
        ) {
            Artwork(
                artworkUri = media.thumbnailUrl.ifBlank { null },
                seed = media.url.hashCode().toLong(),
                contentDescription = media.title,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(AlexaRadius.md),
                modifier = Modifier.size(96.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.title.ifBlank { media.url },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (media.uploader.isNotBlank()) {
                    Text(
                        text = media.uploader,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (media.durationMs > 0L) {
                    Text(
                        text = DurationFormatter.format(media.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (resolvingPlay) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }

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
                    selected = quality == q,
                    onClick = { onQuality(q) },
                    label = { Text(qualityLabel(q)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        Text(
            text = stringResource(R.string.downloader_quality_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AlexaSpacing.lg),
        )

        Spacer(Modifier.height(AlexaSpacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AlexaSpacing.lg),
        ) {
            if (media.isVideo) {
                PrimaryButton(
                    text = stringResource(R.string.downloader_play),
                    onClick = onPlay,
                    enabled = !resolvingPlay,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(AlexaSpacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AlexaSpacing.lg),
        ) {
            PrimaryButton(
                text = stringResource(R.string.youtube_download_video),
                onClick = onDownloadVideo,
                enabled = media.isVideo,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.youtube_download_audio),
                onClick = onDownloadAudio,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DownloadRow(download: ActiveDownload) {
    Column(
        modifier = Modifier
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
