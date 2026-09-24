package com.harleytg.puppyclicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PuppyViewerDialog(
    asset: PuppyRosterAsset,
    state: V6GameState,
    retryToken: Int,
    onDismiss: () -> Unit,
    onUse: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryArtwork: () -> Unit,
    onRename: (() -> Unit)? = null
) {
    val id = asset.style.id
    PuppyRosterDialog(
        mode = PuppyRosterDialogMode.PREVIEW,
        asset = asset,
        unlocked = id in state.unlockedPuppies,
        current = id == state.puppyStyle,
        activeName = state.puppyName,
        retryToken = retryToken,
        onDismiss = onDismiss,
        onUse = {},
        onRename = {},
        isFavorite = id in state.favoritePuppies,
        onToggleFavorite = onToggleFavorite,
        onRetryArtwork = onRetryArtwork,
        onRequestUse = onUse,
        onRequestRename = onRename
    )
}
