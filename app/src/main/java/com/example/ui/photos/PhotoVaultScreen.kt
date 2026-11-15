package com.example.ui.photos

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.VaultMediaEntity
import com.example.security.VaultSessionManager
import com.example.ui.common.formatFileSize
import com.example.ui.common.formatTimestamp
import com.example.ui.theme.LocalVaultAccents
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.vault.MediaVaultViewModel

@Composable
fun PhotoVaultScreen(
    viewModel: MediaVaultViewModel,
    sessionManager: VaultSessionManager
) {
    val activePhotos by viewModel.activePhotos.collectAsStateWithLifecycle()
    val trashedPhotos by viewModel.trashedPhotos.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val accents = LocalVaultAccents.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showTrash by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var viewingPhoto by remember { mutableStateOf<VaultMediaEntity?>(null) }
    var confirmPermanentDelete by remember { mutableStateOf(false) }

    val displayedItems = if (showTrash) trashedPhotos else activePhotos

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importPhotos(uris)
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
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    containerColor = accents.neonPrimary,
                    contentColor = ObsidianBlack,
                    icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                    text = { Text("Import Photos") },
                    modifier = Modifier.testTag("photo_vault_import_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter + Selection Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedIds.isEmpty()) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !showTrash,
                            onClick = { showTrash = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            modifier = Modifier.testTag("photo_tab_gallery")
                        ) {
                            Text("Gallery (${activePhotos.size})")
                        }
                        SegmentedButton(
                            selected = showTrash,
                            onClick = { showTrash = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            modifier = Modifier.testTag("photo_tab_trash")
                        ) {
                            Text("Trash (${trashedPhotos.size})")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ObsidianCard)
                            .border(1.dp, accents.neonPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
                                    },
                                    modifier = Modifier.testTag("photo_batch_restore")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestoreFromTrash,
                                        contentDescription = "Restore selected",
                                        tint = accents.statusGood
                                    )
                                }
                                IconButton(
                                    onClick = { confirmPermanentDelete = true },
                                    modifier = Modifier.testTag("photo_batch_wipe")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = "Delete permanently",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        viewModel.moveToTrash(selectedIds.toList())
                                        selectedIds = emptySet()
                                    },
                                    modifier = Modifier.testTag("photo_batch_trash")
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
                    color = accents.neonPrimary
                )
            }

            if (displayedItems.isEmpty()) {
                EmptyMediaState(
                    title = if (showTrash) "Trash is Empty" else "No Encrypted Photos Yet",
                    description = if (showTrash) {
                        "Deleted photos stay here until you restore them or permanently wipe them."
                    } else {
                        "Tap Import Photos to encrypt private images into your device-only AES-256 vault using the Android Photo Picker."
                    }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        EncryptedPhotoGridTile(
                            item = item,
                            isSelected = isSelected,
                            loadThumbnail = { viewModel.loadThumbnail(item) },
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                } else {
                                    viewingPhoto = item
                                }
                            },
                            onLongClick = {
                                selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                            }
                        )
                    }
                }
            }
        }
    }

    viewingPhoto?.let { photo ->
        FullScreenPhotoViewer(
            photo = photo,
            isTrashed = showTrash,
            loadFullPhoto = { viewModel.loadFullPhoto(photo) },
            onDismiss = { viewingPhoto = null },
            onExportToDevice = {
                viewModel.exportToDeviceGallery(photo)
            },
            onTrashOrRestore = {
                if (showTrash) {
                    viewModel.restoreFromTrash(listOf(photo.id))
                } else {
                    viewModel.moveToTrash(listOf(photo.id))
                }
                viewingPhoto = null
            },
            onPermanentDelete = {
                viewModel.deletePermanently(listOf(photo))
                viewingPhoto = null
            }
        )
    }

    if (confirmPermanentDelete) {
        val toDelete = displayedItems.filter { selectedIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { confirmPermanentDelete = false },
            containerColor = ObsidianCard,
            title = { Text("Permanently Wipe Photos?") },
            text = {
                Text(
                    "This will permanently erase ${toDelete.size} encrypted photo(s) from your private vault. This cannot be undone.",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EncryptedPhotoGridTile(
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

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCard)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) accents.neonPrimary else accents.cardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("photo_grid_item_${item.id}"),
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
                imageVector = Icons.Default.Photo,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.45f),
                modifier = Modifier.size(30.dp)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accents.neonPrimary.copy(alpha = 0.22f))
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = accents.neonPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(ObsidianBlack, CircleShape)
            )
        }
    }
}

@Composable
private fun FullScreenPhotoViewer(
    photo: VaultMediaEntity,
    isTrashed: Boolean,
    loadFullPhoto: suspend () -> Bitmap?,
    onDismiss: () -> Unit,
    onExportToDevice: () -> Unit,
    onTrashOrRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val accents = LocalVaultAccents.current
    var showInfo by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val fullBitmap by produceState<Bitmap?>(initialValue = null, key1 = photo.id) {
        value = loadFullPhoto()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ObsidianBlack
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (fullBitmap != null) {
                    Image(
                        bitmap = fullBitmap!!.asImageBitmap(),
                        contentDescription = photo.originalName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                                    scale = newScale
                                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                } else {
                    CircularProgressIndicator(
                        color = accents.neonPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(ObsidianBlack.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = photo.originalName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Photo details",
                            tint = accents.neonPrimary
                        )
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(ObsidianBlack.copy(alpha = 0.78f))
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

                if (showInfo) {
                    AlertDialog(
                        onDismissRequest = { showInfo = false },
                        containerColor = ObsidianCard,
                        title = { Text("Encrypted Media Details") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("File: ${photo.originalName}", color = TextPrimary)
                                Text("Size: ${formatFileSize(photo.sizeBytes)}", color = TextSecondary)
                                if (photo.width > 0 && photo.height > 0) {
                                    Text("Dimensions: ${photo.width} × ${photo.height}", color = TextSecondary)
                                }
                                Text("Encryption: AES-256-GCM (Android Keystore)", color = accents.neonPrimary)
                                Text("Imported: ${formatTimestamp(photo.createdAt)}", color = TextSecondary)
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showInfo = false }) { Text("Close") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyMediaState(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_vault_state),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, LocalVaultAccents.current.cardBorder, RoundedCornerShape(22.dp))
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
    }
}
