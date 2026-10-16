package com.sonyphototransfer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonyphototransfer.app.data.RawHandling
import com.sonyphototransfer.app.ui.AppIcons
import com.sonyphototransfer.app.ui.AppViewModel
import com.sonyphototransfer.app.ui.components.BackButton
import com.sonyphototransfer.app.ui.components.CameraImage
import com.sonyphototransfer.app.ui.components.InfoRow
import com.sonyphototransfer.app.ui.formatBytes
import com.sonyphototransfer.app.ui.formatCameraDate
import com.sonyphototransfer.camera.CameraPhoto
import com.sonyphototransfer.camera.PhotoFormat

@Composable
fun ViewerScreen(vm: AppViewModel, photoKey: String) {
    val ui by vm.gallery.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val photos = ui.visible
    val start = photos.indexOfFirst { it.stableKey == photoKey }.coerceAtLeast(0)
    if (photos.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { Box(Modifier.statusBarsPadding()) { BackButton { vm.back() } } }
        return
    }
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var zoomed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(vm, photos[page], onZoomChanged = { zoomed = it })
        }
        Surface(color = Color.Black.copy(alpha = 0.4f), contentColor = Color.White, modifier = Modifier.statusBarsPadding()) {
            BackButton { vm.back() }
        }
        val photo = photos.getOrNull(pager.currentPage)
        if (photo != null && !zoomed) {
            val download = rememberDownloadAction { vm.download(photo) }
            DetailsPanel(
                photo = photo,
                downloaded = photo.stableKey in ui.downloadedKeys,
                rawPreviewAllowed = settings.rawHandling == RawHandling.SAVE_PREVIEW,
                onDownload = download,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun ZoomableImage(vm: AppViewModel, photo: CameraPhoto, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember(photo.stableKey) { mutableFloatStateOf(1f) }
    var offset by remember(photo.stableKey) { mutableStateOf(Offset.Zero) }
    fun setScale(value: Float) {
        scale = value.coerceIn(1f, 5f)
        if (scale == 1f) offset = Offset.Zero
        onZoomChanged(scale > 1f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(photo.stableKey) {
                detectTapGestures(onDoubleTap = { setScale(if (scale > 1f) 1f else 2.5f) })
            }
            .pointerInput(photo.stableKey, scale > 1f) {
                // Double-tap zooms in; only then are drags claimed here, so the pager
                // can keep swiping between photos at normal size.
                if (scale > 1f) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        setScale(scale * zoom)
                        offset += pan
                    }
                }
            },
    ) {
        // Previews only: the camera's large JPEG (~1600 px). The original is never loaded into memory here.
        CameraImage(
            vm.thumbnails,
            photo.largePreview,
            maxPx = 2048,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun DetailsPanel(
    photo: CameraPhoto,
    downloaded: Boolean,
    rawPreviewAllowed: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier,
) {
    val original = photo.original
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(photo.fileName, style = MaterialTheme.typography.titleMedium)
            InfoRow("Formato", photo.format.label)
            InfoRow("Fecha", formatCameraDate(photo.date))
            InfoRow("Tamaño", original?.sizeBytes?.let(::formatBytes) ?: "No lo indica la cámara")
            original?.resolution?.let { InfoRow("Resolución", it.replace("x", " × ")) }
            if (downloaded) InfoRow("Estado", "Ya descargada")
            if (photo.format == PhotoFormat.RAW) {
                Text(
                    "La cámara no envía el archivo RAW original por Wi-Fi; lo que ves es su vista previa JPEG." +
                        if (rawPreviewAllowed) " Puedes guardar esa vista previa." else " Puedes permitir guardar la vista previa en Ajustes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val canDownload = original != null || (photo.format == PhotoFormat.RAW && rawPreviewAllowed && photo.largePreview != null)
            Spacer(Modifier.size(8.dp))
            Button(onClick = onDownload, enabled = canDownload && !downloaded, modifier = Modifier.fillMaxWidth()) {
                Icon(AppIcons.Download, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    when {
                        downloaded -> "Descargada"
                        original != null -> "Descargar original"
                        canDownload -> "Guardar vista previa JPEG"
                        else -> "Original no disponible"
                    },
                )
            }
        }
    }
}
