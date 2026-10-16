package com.sonyphototransfer.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonyphototransfer.app.camera.ConnectionState
import com.sonyphototransfer.app.ui.AppViewModel
import com.sonyphototransfer.app.ui.components.BackButton
import com.sonyphototransfer.app.ui.components.InfoRow
import com.sonyphototransfer.app.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val gallery by vm.gallery.collectAsStateWithLifecycle()
    val log by vm.session.log.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton { vm.back() } },
                title = { Text("Diagnóstico") },
                actions = {
                    TextButton(onClick = {
                        val text = buildString {
                            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
                            appendLine("Estado: $connection")
                            log.forEach(::appendLine)
                        }
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Diagnóstico", text))
                    }) { Text("Copiar") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Column {
                    SectionTitle("Conexión")
                    InfoRow("Estado", describe(connection))
                    (connection as? ConnectionState.Connected)?.camera?.let { cam ->
                        InfoRow("Modelo", cam.displayModel)
                        InfoRow("Fabricante", cam.manufacturer.ifBlank { "—" })
                        InfoRow("Descripción", cam.descriptionUrl)
                    }
                    InfoRow("Imágenes listadas", gallery.totalCount.toString())
                    gallery.countsByFormat.forEach { (format, count) -> InfoRow("  ${format.label}", count.toString()) }
                    InfoRow("Teléfono", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    SectionTitle("Registro")
                    if (log.isEmpty()) Text("Sin actividad todavía.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(log) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun describe(state: ConnectionState) = when (state) {
    ConnectionState.Disconnected -> "Desconectada"
    ConnectionState.JoiningWifi -> "Conectando a la Wi-Fi"
    ConnectionState.FindingCamera -> "Buscando la cámara"
    is ConnectionState.Connected -> "Conectada"
    is ConnectionState.Error -> "Error: ${state.message}"
}
