package com.sonyphototransfer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonyphototransfer.app.camera.ConnectionState
import com.sonyphototransfer.app.ui.AppIcons
import com.sonyphototransfer.app.ui.AppViewModel
import com.sonyphototransfer.app.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val transfers by vm.transfers.state.collectAsStateWithLifecycle()
    val gallery by vm.gallery.collectAsStateWithLifecycle()
    var showConnect by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sony Photo Transfer", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(
                    state = connection,
                    photoCount = gallery.totalCount,
                    onConnect = { showConnect = true },
                    onDisconnect = vm::disconnect,
                    onDiagnostics = { vm.navigate(Screen.Diagnostics) },
                )

                val connected = connection is ConnectionState.Connected
                Button(
                    onClick = { vm.navigate(Screen.Gallery) },
                    enabled = connected,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(AppIcons.Photo, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Galería de la cámara")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { vm.navigate(Screen.Transfers) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text(if (transfers.pending > 0) "Transferencias (${transfers.pending})" else "Transferencias")
                    }
                    FilledTonalButton(onClick = { vm.navigate(Screen.Downloads) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Descargadas")
                    }
                }

                if (connection !is ConnectionState.Connected) CameraSetupHelp()
            }
        }
    }

    if (showConnect) {
        ConnectDialog(
            initialSsid = vm.settings.value.lastSsid,
            initialPassword = vm.settings.value.lastPassword,
            onConnect = { ssid, pw ->
                showConnect = false
                vm.connect(ssid, pw)
            },
            onUseCurrentWifi = {
                showConnect = false
                vm.connectCurrentWifi()
            },
            onDismiss = { showConnect = false },
        )
    }
}

@Composable
private fun StatusCard(
    state: ConnectionState,
    photoCount: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(state)
                Column(Modifier.weight(1f)) {
                    Text(
                        when (state) {
                            ConnectionState.Disconnected -> "Desconectada"
                            ConnectionState.JoiningWifi -> "Conectando a la Wi-Fi…"
                            ConnectionState.FindingCamera -> "Buscando la cámara…"
                            is ConnectionState.Connected -> "Conectada"
                            is ConnectionState.Error -> "Error de conexión"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        when (state) {
                            is ConnectionState.Connected -> "${state.camera.displayModel} · $photoCount imágenes"
                            is ConnectionState.Error -> state.message
                            ConnectionState.JoiningWifi -> "Acepta el diálogo de Android para unirte a la red de la cámara."
                            ConnectionState.FindingCamera -> "Comprobando que la cámara responde."
                            ConnectionState.Disconnected -> "Sony α7 III (ILCE-7M3) por Wi-Fi"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (state) {
                ConnectionState.Disconnected, is ConnectionState.Error ->
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Conectar cámara") }
                ConnectionState.JoiningWifi, ConnectionState.FindingCamera ->
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
                is ConnectionState.Connected ->
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
            }
            if (state is ConnectionState.Error) {
                TextButton(onClick = onDiagnostics) { Text("Ver diagnóstico") }
            }
        }
    }
}

@Composable
private fun StatusDot(state: ConnectionState) {
    when (state) {
        ConnectionState.JoiningWifi, ConnectionState.FindingCamera ->
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        is ConnectionState.Connected ->
            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
        is ConnectionState.Error ->
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
        ConnectionState.Disconnected ->
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp)) {}
    }
}

@Composable
private fun CameraSetupHelp() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                Text("Preparar la cámara", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "1. En la α7 III: MENU → Red → Func. Enviar a smartphone → Enviar a smartphone → " +
                    "«Seleccionar en smartphone» (los nombres exactos pueden variar con el idioma del menú).",
            )
            Text("2. La cámara crea su propia red Wi-Fi (DIRECT-xxxx:ILCE-7M3) y muestra su nombre y contraseña.")
            Text("3. Pulsa «Conectar cámara» e introdúcelos. Android te pedirá confirmar la conexión.")
            Text(
                "Mientras estés conectado a la cámara no tendrás internet por Wi-Fi; los datos móviles siguen funcionando.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Limitación de la cámara: por Wi-Fi solo entrega los JPEG originales. Los RAW (ARW) solo pueden verse " +
                    "como vista previa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectDialog(
    initialSsid: String,
    initialPassword: String,
    onConnect: (String, String) -> Unit,
    onUseCurrentWifi: () -> Unit,
    onDismiss: () -> Unit,
) {
    var ssid by rememberSaveable { mutableStateOf(initialSsid) }
    var password by rememberSaveable { mutableStateOf(initialPassword) }
    var showPassword by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conectar cámara") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Escribe el nombre de red (SSID) y la contraseña que muestra la cámara en la pantalla de " +
                        "«Enviar a smartphone».",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it.trim() },
                    label = { Text("Nombre de red (SSID)") },
                    placeholder = { Text("DIRECT-xxxx:ILCE-7M3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Ocultar" else "Ver") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Por qué: Android solo deja a una app unirse a una red Wi-Fi a través de su propio diálogo de " +
                        "confirmación. No se necesita permiso de ubicación. La contraseña solo se guarda en este teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onUseCurrentWifi) { Text("Ya estoy conectado a la Wi-Fi de la cámara") }
            }
        },
        confirmButton = {
            Button(onClick = { onConnect(ssid, password) }, enabled = ssid.isNotBlank()) { Text("Conectar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
