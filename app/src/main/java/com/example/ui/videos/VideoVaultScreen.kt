package com.example.ui.videos

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.data.VaultMediaEntity
import com.example.media.EncryptedVaultDataSource
import com.example.security.KeystoreCryptoManager
import com.example.security.VaultSessionManager
import com.example.ui.common.formatDuration
import com.example.ui.common.formatFileSize
import com.example.ui.photos.EmptyMediaState
import com.example.ui.theme.LocalVaultAccents
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.vault.MediaVaultViewModel
import java.io.File

@Composable
fun VideoVaultScreen(
    viewModel: MediaVaultViewModel,
    sessionManager: VaultSessionManager
) {
    val activeVideos by viewModel.activeVideos.collectAsStateWithLifecycle()
    val trashedVideos by viewModel.trashedVideos.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val accents = LocalVaultAccents.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showTrash by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var playingVideo by remember { mutableStateOf<VaultMediaEntity?>(null) }
    var confirmPermanentDelete by remember { mutableStateOf(false) }

    val displayedItems = if (showTrash) trashedVideos else activeVideos

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importVideos(uris)
        }
    }

    LaunchedEffect(feedback.statusMessage) {
        feedback.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(showTrash) {
        selectedIds = emptySet()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!showTrash && selectedIds.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        sessionManager.allowTemporaryExternalActivity()
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    containerColor = accents.neonSecondary,
                    contentColor = TextPrimary,
                    icon = { Icon(Icons.Default.VideoCall, contentDescription = null) },
                    text = { Text("Import Videos") },
                    modifier = Modifier.testTag("video_vault_import_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedIds.isEmpty()) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !showTrash,
                            onClick = { showTrash = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            modifier = Modifier.testTag("video_tab_gallery")
                        ) {
                            Text("Videos (${activeVideos.size})")
                        }
                        SegmentedButton(
                            selected = showTrash,
                            onClick = { showTrash = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            modifier = Modifier.testTag("video_tab_trash")
                        ) {
                            Text("Trash (${trashedVideos.size})")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianCard)
                            .border(1.dp, accents.neonSecondary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = TextPrimary)
                            }
                            Text(
                                text = "${selectedIds.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                        Row {
                            if (showTrash) {
                                IconButton(
                                    onClick = {
                                        viewModel.restoreFromTrash(selectedIds.toList())
                                        selectedIds = emptySet()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestoreFromTrash,
                                        contentDescription = "Restore selected",
                                        tint = accents.statusGood
                                    )
                                }
                                IconButton(onClick = { confirmPermanentDelete = true }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = "Wipe forever",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        viewModel.moveToTrash(selectedIds.toList())
                                        selectedIds = emptySet()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Move to trash",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (feedback.isImporting) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = accents.neonSecondary
                )
            }

            if (displayedItems.isEmpty()) {
                EmptyMediaState(
                    title = if (showTrash) "Video Trash is Empty" else "No Encrypted Videos Yet",
                    description = if (showTrash) {
                        "Deleted videos can be restored or permanently wiped from encrypted storage."
                    } else {
                        "Tap Import Videos to encrypt videos into local private storage and play them in-app without temporary plaintext files."
                    }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedItems, key = { it.id }) { video ->
                        val isSelected = selectedIds.contains(video.id)
                        EncryptedVideoGridTile(
                            item = video,
                            isSelected = isSelected,
                            loadThumbnail = { viewModel.loadThumbnail(video) },
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (isSelected) selectedIds - video.id else selectedIds + video.id
                                } else {
                                    playingVideo = video
                                }
                            },
                            onLongClick = {
                                selectedIds = if (isSelected) selectedIds - video.id else selectedIds + video.id
                            }
                        )
                    }
                }
            }
        }
    }

    playingVideo?.let { video ->
        EncryptedVideoPlayerDialog(
            video = video,
            encryptedFile = viewModel.getEncryptedFile(video),
            cryptoManager = viewModel.cryptoManager,
            isTrashed = showTrash,
            onDismiss = { playingVideo = null },
            onExportToDevice = { viewModel.exportToDeviceGallery(video) },
            onTrashOrRestore = {
                if (showTrash) {
                    viewModel.restoreFromTrash(listOf(video.id))
                } else {
                    viewModel.moveToTrash(listOf(video.id))
                }
                playingVideo = null
            },
            onPermanentDelete = {
                viewModel.deletePermanently(listOf(video))
                playingVideo = null
            }
        )
    }

    if (confirmPermanentDelete) {
        val toDelete = displayedItems.filter { selectedIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { confirmPermanentDelete = false },
            containerColor = ObsidianCard,
            title = { Text("Permanently Wipe Videos?") },
            text = {
                Text(
                    "Erase ${toDelete.size} encrypted video(s) permanently? This cannot be undone.",
                    color = TextSecondary
                )
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmPermanentDelete = false }) { Text("Cancel") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePermanently(toDelete)
                        selectedIds = emptySet()
                        confirmPermanentDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Wipe Forever")
                }
            }
        )
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EncryptedVideoGridTile(
    item: VaultMediaEntity,
    isSelected: Boolean,
    loadThumbnail: suspend () -> Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val accents = LocalVaultAccents.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = item.id) {
        value = loadThumbnail()
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCard)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) accents.neonSecondary else accents.cardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("video_grid_item_${item.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(ObsidianBlack),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = item.originalName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(34.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ObsidianBlack.copy(alpha = 0.65f))
                    .border(1.dp, accents.neonSecondary.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play video",
                    tint = TextPrimary
                )
            }

            if (item.durationMs > 0L) {
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianBlack.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = accents.neonSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(ObsidianBlack, CircleShape)
                )
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = item.originalName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = formatFileSize(item.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun EncryptedVideoPlayerDialog(
    video: VaultMediaEntity,
    encryptedFile: File,
    cryptoManager: KeystoreCryptoManager,
    isTrashed: Boolean,
    onDismiss: () -> Unit,
    onExportToDevice: () -> Unit,
    onTrashOrRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val accents = LocalVaultAccents.current

    val exoPlayer = remember(video.id) {
        val dataSourceFactory = EncryptedVaultDataSource.Factory(cryptoManager) { encryptedFile }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(encryptedFile)))
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ObsidianBlack
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close player",
                            tint = TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.originalName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "Streaming directly from AES-256-GCM storage",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.neonPrimary
                        )
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(onClick = onExportToDevice) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Restore to Device")
                    }
                    if (isTrashed) {
                        Button(
                            onClick = onTrashOrRestore,
                            colors = ButtonDefaults.buttonColors(containerColor = accents.statusGood, contentColor = ObsidianBlack)
                        ) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Restore")
                        }
                        IconButton(onClick = onPermanentDelete) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Wipe forever", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(
                            onClick = onTrashOrRestore,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
