package com.vortexdl.app.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vortexdl.app.R

/**
 * Floating action button with a live badge showing how many media files were
 * captured on the current browsing session.
 */
@Composable
fun DetectedMediaFab(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentDescription = pluralStringResource(R.plurals.fab_content_description, count, count)

    BadgedBox(
        modifier = modifier,
        badge = {
            if (count > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ) {
                    Text(
                        text = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString(),
                    )
                }
            }
        },
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
            )
        }
    }
}

private const val MAX_BADGE_COUNT = 99
