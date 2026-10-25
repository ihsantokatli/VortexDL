package com.vortexdl.app.presentation.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vortexdl.app.R
import com.vortexdl.app.domain.model.DetectedMedia
import java.util.Locale

/**
 * Bottom sheet listing every media resource captured by the sniffer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectedMediaSheet(
    mediaList: List<DetectedMedia>,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onPreview: (DetectedMedia) -> Unit,
    onDownload: (DetectedMedia) -> Unit,
    onCopyLink: (DetectedMedia) -> Unit,
    onOpenExternally: (DetectedMedia) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SheetHeader(
                count = mediaList.size,
                onClearAll = onClearAll,
                onDismiss = onDismissRequest,
            )

            if (mediaList.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(
                        items = mediaList,
                        key = { _, media -> media.id },
                    ) { index, media ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        MediaRow(
                            media = media,
                            onPreview = { onPreview(media) },
                            onDownload = { onDownload(media) },
                            onCopyLink = { onCopyLink(media) },
                            onOpenExternally = { onOpenExternally(media) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetHeader(
    count: Int,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.detected_media_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (count > 0) {
                    pluralStringResource(R.plurals.detected_media_count, count, count)
                } else {
                    stringResource(R.string.detected_media_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (count > 0) {
            TextButton(onClick = onClearAll) {
                Text(text = stringResource(R.string.action_clear_all))
            }
        }

        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.detected_media_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.detected_media_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MediaRow(
    media: DetectedMedia,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    val title = media.title.trim().ifEmpty { stringResource(R.string.media_untitled) }
    val host = mediaHost(media.url) ?: stringResource(R.string.media_unknown_host)
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        media.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    val headerCount = media.requestHeaders.size

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.action_preview_for, title),
                onClick = onPreview,
            )
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FormatBadge(media)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$host · $relativeTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.detected_media_headers,
                    headerCount,
                    headerCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = onDownload,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = stringResource(R.string.action_download_for, title),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = onCopyLink,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy_link_for, title),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onOpenExternally,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenInBrowser,
                    contentDescription = stringResource(R.string.action_open_link_for, title),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun FormatBadge(media: DetectedMedia) {
    val extension = media.extension.lowercase(Locale.US)
    val label = when (extension) {
        "m3u8" -> "HLS"
        "mpd" -> "DASH"
        else -> extension.uppercase(Locale.US)
    }
    val containerColor: Color
    val contentColor: Color
    if (media.isStreamingFormat) {
        containerColor = MaterialTheme.colorScheme.tertiaryContainer
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        containerColor = MaterialTheme.colorScheme.secondaryContainer
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

private fun mediaHost(url: String): String? =
    runCatching { android.net.Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
