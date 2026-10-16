package com.sonyphototransfer.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonyphototransfer.app.storage.DownloadedPhoto
import com.sonyphototransfer.app.storage.PhotoSaver
import com.sonyphototransfer.app.ui.AppViewModel
import com.sonyphototransfer.app.ui.components.Badge
import com.sonyphototransfer.app.ui.components.BackButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val entries by vm.downloadIndex.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val photos = remember(entries) { entries.values.sortedByDescending { it.savedAtMillis } }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(navigationIcon = { BackButton { vm.back() } }, title = { Text("Descargadas · ${photos.size}") }) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        if (settings.destinationTree == null) {
                            "Guardadas en ${PhotoSaver.RELATIVE_PATH}. También aparecen en la galería de Android."
                        } else {
                            "Guardadas en la carpeta elegida en Ajustes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (photos.isEmpty()) Text("Todavía no has descargado ninguna fotografía.", Modifier.padding(top = 16.dp))
                }
            }
            items(photos, key = { it.key }) { photo ->
                DownloadedTile(photo) {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(photo.uri, "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No hay ninguna app para abrir imágenes", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedTile(photo: DownloadedPhoto, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(photo.uri) { mutableStateOf<Bitmap?>(null) }
    var missing by remember(photo.uri) { mutableStateOf(false) }
    LaunchedEffect(photo.uri) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(photo.uri, Size(256, 256), null) }.getOrNull()
        }
        bitmap = loaded
        missing = loaded == null
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !missing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = photo.fileName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (missing) {
            Text("Archivo no encontrado", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
        }
        if (photo.isPreview) Badge("Vista previa", Modifier.align(Alignment.BottomStart).padding(4.dp))
    }
}
