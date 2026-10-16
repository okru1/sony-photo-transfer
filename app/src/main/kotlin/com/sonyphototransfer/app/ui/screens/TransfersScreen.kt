package com.sonyphototransfer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonyphototransfer.app.transfer.TransferItem
import com.sonyphototransfer.app.transfer.TransferState
import com.sonyphototransfer.app.transfer.TransferStatus
import com.sonyphototransfer.app.ui.AppViewModel
import com.sonyphototransfer.app.ui.components.BackButton
import com.sonyphototransfer.app.ui.components.CameraImage
import com.sonyphototransfer.app.ui.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(vm: AppViewModel) {
    val state by vm.transfers.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(navigationIcon = { BackButton { vm.back() } }, title = { Text("Transferencias") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Summary(state, vm) }
            if (state.items.isEmpty()) {
                item {
                    Text(
                        "No hay transferencias. Selecciona fotografías en la galería de la cámara y pulsa «Descargar».",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
            items(state.items.asReversed(), key = { it.id }) { item -> TransferRow(vm, item) }
        }
    }
}

@Composable
private fun Summary(state: TransferState, vm: AppViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val total = state.bytesTotal
            Text(
                if (state.isActive) "Descargando…" else "Sin descargas en curso",
                style = MaterialTheme.typography.titleMedium,
            )
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (state.bytesDone.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${(state.bytesDone * 100 / total)} % · ${formatBytes(state.bytesDone)} de ${formatBytes(total)}")
            } else if (state.isActive) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(formatBytes(state.bytesDone))
            }
            Text(
                "${state.done} completadas · ${state.pending} pendientes · ${state.failed} con error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isActive) OutlinedButton(onClick = vm.transfers::cancelAll) { Text("Cancelar todo") }
                if (state.items.any { it.status == TransferStatus.FAILED || it.status == TransferStatus.CANCELLED }) {
                    OutlinedButton(onClick = vm.transfers::retryFailed) { Text("Reintentar fallidas") }
                }
                if (!state.isActive && state.items.isNotEmpty()) {
                    OutlinedButton(onClick = vm.transfers::clearFinished) { Text("Limpiar") }
                }
            }
        }
    }
}

@Composable
private fun TransferRow(vm: AppViewModel, item: TransferItem) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CameraImage(vm.thumbnails, item.photo.thumbnail, maxPx = 128, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.photo.fileName, style = MaterialTheme.typography.bodyLarge)
            val (label, color) = when (item.status) {
                TransferStatus.QUEUED -> "En cola" to MaterialTheme.colorScheme.onSurfaceVariant
                TransferStatus.RUNNING -> buildString {
                    append(formatBytes(item.bytesDone))
                    item.bytesTotal?.let { append(" de ${formatBytes(it)}") }
                } to MaterialTheme.colorScheme.primary
                TransferStatus.DONE -> (item.message ?: "Completada y verificada") to Color(0xFF4CAF50)
                TransferStatus.FAILED -> "Error: ${item.message}" to MaterialTheme.colorScheme.error
                TransferStatus.CANCELLED -> "Cancelada" to MaterialTheme.colorScheme.onSurfaceVariant
                TransferStatus.SKIPPED -> "Omitida: ${item.message}" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            if (item.status == TransferStatus.RUNNING) {
                val fraction = item.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        when (item.status) {
            TransferStatus.QUEUED, TransferStatus.RUNNING ->
                IconButton(onClick = { vm.transfers.cancel(item.id) }) { Icon(Icons.Filled.Close, "Cancelar") }
            TransferStatus.FAILED, TransferStatus.CANCELLED ->
                IconButton(onClick = { vm.transfers.retry(item.id) }) { Icon(Icons.Filled.Refresh, "Reintentar") }
            else -> {}
        }
    }
}
