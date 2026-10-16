package com.sonyphototransfer.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonyphototransfer.app.ui.AppIcons
import com.sonyphototransfer.app.ui.AppViewModel
import com.sonyphototransfer.app.ui.Screen
import com.sonyphototransfer.app.ui.ViewFilter
import com.sonyphototransfer.app.ui.components.BackButton
import com.sonyphototransfer.app.ui.components.Badge
import com.sonyphototransfer.app.ui.components.CameraImage
import com.sonyphototransfer.app.ui.components.NoticeCard
import com.sonyphototransfer.camera.CameraPhoto
import com.sonyphototransfer.camera.PhotoFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(vm: AppViewModel) {
    val ui by vm.gallery.collectAsStateWithLifecycle()
    val selecting = ui.selected.isNotEmpty()
    val requestDownload = rememberDownloadAction { vm.downloadSelected(); vm.navigate(Screen.Transfers) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Cancelar selección") }
                    } else {
                        BackButton { vm.back() }
                    }
                },
                title = {
                    if (selecting) Text("${ui.selected.size} seleccionadas")
                    else Text("Cámara · ${ui.totalCount}")
                },
                actions = {
                    IconButton(onClick = vm::selectAllVisible, enabled = ui.visible.isNotEmpty()) {
                        Icon(AppIcons.SelectAll, "Seleccionar todas las visibles")
                    }
                    if (!selecting) {
                        IconButton(onClick = { vm.session.refreshPhotos() }, enabled = !ui.loading) {
                            Icon(Icons.Filled.Refresh, "Actualizar")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selecting) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = requestDownload,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) {
                        Icon(AppIcons.Download, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Descargar ${ui.selected.size}")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ViewFilter.entries.forEach { f ->
                    val count = when (f) {
                        ViewFilter.ALL -> ui.totalCount
                        ViewFilter.JPEG -> ui.countsByFormat[PhotoFormat.JPEG] ?: 0
                        ViewFilter.RAW -> ui.countsByFormat[PhotoFormat.RAW] ?: 0
                    }
                    FilterChip(
                        selected = ui.filter == f,
                        onClick = { vm.setFilter(f) },
                        label = { Text("${f.label} ($count)") },
                    )
                }
            }
            when {
                ui.error != null && ui.visible.isEmpty() -> EmptyState(
                    "No se pudo leer la tarjeta: ${ui.error}",
                    action = "Reintentar",
                    onAction = { vm.session.refreshPhotos() },
                )
                !ui.loading && ui.visible.isEmpty() -> EmptyState(
                    if (ui.totalCount == 0) "La cámara no ha devuelto ninguna fotografía." else "No hay fotografías con este filtro.",
                )
                else -> PhotoGrid(vm, ui.visible, ui.selected, ui.downloadedKeys, hasRaw = (ui.countsByFormat[PhotoFormat.RAW] ?: 0) > 0)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    vm: AppViewModel,
    photos: List<CameraPhoto>,
    selected: Set<String>,
    downloaded: Set<String>,
    hasRaw: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hasRaw) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                NoticeCard(
                    Icons.Filled.Info,
                    "La α7 III no envía archivos RAW (ARW) por Wi-Fi: solo sus vistas previas JPEG. " +
                        "Los JPEG sí se descargan como archivo original.",
                    Modifier.padding(bottom = 4.dp),
                )
            }
        }
        items(photos, key = { it.stableKey }) { photo ->
            val isSelected = photo.stableKey in selected
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier)
                    .combinedClickable(
                        onClick = {
                            if (selected.isNotEmpty()) vm.toggle(photo) else vm.navigate(Screen.Viewer(photo.stableKey))
                        },
                        onLongClick = { vm.toggle(photo) },
                        onClickLabel = "Abrir",
                        onLongClickLabel = "Seleccionar",
                    ),
            ) {
                CameraImage(vm.thumbnails, photo.thumbnail, maxPx = 256, modifier = Modifier.fillMaxSize())
                if (photo.format != PhotoFormat.JPEG) {
                    Badge(photo.format.label, Modifier.align(Alignment.BottomStart).padding(4.dp))
                }
                if (photo.stableKey in downloaded) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Ya descargada",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp),
                    )
                }
                if (selected.isNotEmpty()) {
                    SelectionMark(isSelected, Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
        modifier = modifier.size(24.dp).border(2.dp, Color.White, CircleShape),
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(3.dp))
    }
}

@Composable
private fun EmptyState(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/**
 * Wraps a download action so that, on Android 13+, the notification permission is
 * explained and requested once first. The download starts whatever the answer is.
 */
@Composable
fun rememberDownloadAction(onDownload: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var explain by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDownload() }
    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false; onDownload() },
            title = { Text("Notificaciones") },
            text = {
                Text(
                    "Las descargas largas continúan aunque salgas de la app. Para ver su progreso y poder " +
                        "cancelarlas desde la barra de notificaciones, permite las notificaciones. Es opcional.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explain = false
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { explain = false; onDownload() }) { Text("Ahora no") } },
        )
    }
    return {
        val prefs = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
            explain = true
        } else {
            onDownload()
        }
    }
}

private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
