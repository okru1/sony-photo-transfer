package com.sonyphototransfer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonyphototransfer.app.ui.AppViewModel
import com.sonyphototransfer.app.ui.Screen
import com.sonyphototransfer.app.ui.screens.DiagnosticsScreen
import com.sonyphototransfer.app.ui.screens.DownloadsScreen
import com.sonyphototransfer.app.ui.screens.GalleryScreen
import com.sonyphototransfer.app.ui.screens.HomeScreen
import com.sonyphototransfer.app.ui.screens.SettingsScreen
import com.sonyphototransfer.app.ui.screens.TransfersScreen
import com.sonyphototransfer.app.ui.screens.ViewerScreen
import com.sonyphototransfer.app.ui.theme.SonyPhotoTransferTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SonyPhotoTransferTheme {
                KeepScreenOnDuringTransfers(viewModel)
                AppNavigation(viewModel)
            }
        }
    }
}

@Composable
private fun AppNavigation(vm: AppViewModel) {
    val screen = vm.backStack.last()
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen",
    ) { current ->
        when (current) {
            Screen.Home -> HomeScreen(vm)
            Screen.Gallery -> GalleryScreen(vm)
            is Screen.Viewer -> ViewerScreen(vm, current.photoKey)
            Screen.Transfers -> TransfersScreen(vm)
            Screen.Downloads -> DownloadsScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            Screen.Diagnostics -> DiagnosticsScreen(vm)
        }
    }
}

@Composable
private fun KeepScreenOnDuringTransfers(vm: AppViewModel) {
    val transfers by vm.transfers.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val keepOn = settings.keepScreenOn && transfers.isActive
    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
}
