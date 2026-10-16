package com.sonyphototransfer.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonyphototransfer.app.data.FormatFilter
import com.sonyphototransfer.app.data.RawHandling
import com.sonyphototransfer.app.storage.PhotoSaver
import com.sonyphototransfer.app.ui.AppViewModel
import com.sonyphototransfer.app.ui.Screen
import com.sonyphototransfer.app.ui.components.BackButton
import com.sonyphototransfer.app.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            settings.destinationTree?.let { old ->
                runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
            }
            vm.setDestination(uri)
        }
    }

    Scaffold(topBar = { TopAppBar(navigationIcon = { BackButton { vm.back() } }, title = { Text("Ajustes") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionTitle("Carpeta de destino")
            Choice(
                selected = settings.destinationTree == null,
                title = "Galería del teléfono",
                subtitle = "${PhotoSaver.RELATIVE_PATH} · visible en Google Fotos y otras galerías",
                onClick = { vm.setDestination(null) },
            )
            Choice(
                selected = settings.destinationTree != null,
                title = "Carpeta elegida",
                subtitle = settings.destinationTree?.let { Uri.decode(it.lastPathSegment ?: it.toString()) }
                    ?: "Selecciona una carpeta con el selector del sistema",
                onClick = { pickFolder.launch(null) },
            )

            SectionTitle("Formatos a descargar")
            Choice(settings.downloadFormats == FormatFilter.ALL, "JPEG y RAW", null) {
                vm.updateSettings { it.copy(downloadFormats = FormatFilter.ALL) }
            }
            Choice(settings.downloadFormats == FormatFilter.JPEG_ONLY, "Solo JPEG", null) {
                vm.updateSettings { it.copy(downloadFormats = FormatFilter.JPEG_ONLY) }
            }
            Choice(settings.downloadFormats == FormatFilter.RAW_ONLY, "Solo RAW", "Solo útil junto con la opción siguiente") {
                vm.updateSettings { it.copy(downloadFormats = FormatFilter.RAW_ONLY) }
            }
            Toggle(
                checked = settings.rawHandling == RawHandling.SAVE_PREVIEW,
                title = "Guardar vista previa de los RAW",
                subtitle = "La α7 III no envía el archivo ARW por Wi-Fi. Si lo activas, se guarda la vista previa JPEG " +
                    "que genera la cámara (resolución reducida), con el sufijo «_preview». No es el original.",
                onChange = { on -> vm.updateSettings { it.copy(rawHandling = if (on) RawHandling.SAVE_PREVIEW else RawHandling.SKIP) } },
            )

            SectionTitle("Transferencias")
            Toggle(
                checked = settings.keepScreenOn,
                title = "Mantener la pantalla encendida",
                subtitle = "Mientras haya descargas en curso y la app esté visible.",
                onChange = { on -> vm.updateSettings { it.copy(keepScreenOn = on) } },
            )

            SectionTitle("Permisos")
            Text(
                "• Wi-Fi y red: para unirse a la red que crea la cámara y hablar con ella. No se pide ubicación.\n" +
                    "• Notificaciones (opcional): para ver y cancelar las descargas en segundo plano.\n" +
                    "• Fotos: no se pide permiso de almacenamiento. Las imágenes se guardan con MediaStore o en la " +
                    "carpeta que elijas, y la app solo accede a lo que ella misma guarda.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionTitle("Conexión")
            OutlinedButton(onClick = { vm.navigate(Screen.Diagnostics) }, modifier = Modifier.fillMaxWidth()) {
                Text("Información de conexión y diagnóstico")
            }

            SectionTitle("Acerca de")
            Text(
                "Sony Photo Transfer es una app independiente, sin relación con Sony. Usa el servidor de imágenes que la " +
                    "cámara activa en «Enviar a smartphone» (UPnP/DLNA), un protocolo no documentado oficialmente por Sony.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Choice(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Toggle(checked: Boolean, title: String, subtitle: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
