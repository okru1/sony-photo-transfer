package com.sonyphototransfer.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sonyphototransfer.app.appContainer
import com.sonyphototransfer.app.camera.ConnectionState
import com.sonyphototransfer.app.data.AppSettings
import com.sonyphototransfer.camera.CameraPhoto
import com.sonyphototransfer.camera.PhotoFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

sealed interface Screen {
    data object Home : Screen
    data object Gallery : Screen
    data class Viewer(val photoKey: String) : Screen
    data object Transfers : Screen
    data object Downloads : Screen
    data object Settings : Screen
    data object Diagnostics : Screen
}

enum class ViewFilter(val label: String) { ALL("Todas"), JPEG("JPEG"), RAW("RAW") }

data class GalleryUi(
    val visible: List<CameraPhoto> = emptyList(),
    val totalCount: Int = 0,
    val selected: Set<String> = emptySet(),
    val filter: ViewFilter = ViewFilter.ALL,
    val loading: Boolean = false,
    val error: String? = null,
    val downloadedKeys: Set<String> = emptySet(),
    val countsByFormat: Map<PhotoFormat, Int> = emptyMap(),
)

/** Screen-facing state for the whole app; the real work lives in [com.sonyphototransfer.app.AppContainer]. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    val session = container.session
    val transfers = container.transfers
    val thumbnails = container.thumbnails
    val settingsRepo = container.settings
    val downloadIndex = container.downloadIndex

    /** Simple back stack; survives rotation because it lives in the ViewModel. */
    val backStack = mutableStateListOf<Screen>(Screen.Home)

    private val selection = MutableStateFlow<Set<String>>(emptySet())
    private val filter = MutableStateFlow(ViewFilter.ALL)

    val connection: StateFlow<ConnectionState> = session.connection
    val settings: StateFlow<AppSettings> = settingsRepo.settings

    val gallery: StateFlow<GalleryUi> = combine(
        session.gallery,
        selection,
        filter,
        downloadIndex.entries,
    ) { g, sel, f, downloaded ->
        val visible = when (f) {
            ViewFilter.ALL -> g.photos
            ViewFilter.JPEG -> g.photos.filter { it.format == PhotoFormat.JPEG }
            ViewFilter.RAW -> g.photos.filter { it.format == PhotoFormat.RAW }
        }
        GalleryUi(
            visible = visible,
            totalCount = g.photos.size,
            // Drop selections that disappeared after a refresh.
            selected = sel.intersect(g.photos.mapTo(HashSet()) { it.stableKey }),
            filter = f,
            loading = g.loading,
            error = g.error,
            downloadedKeys = downloaded.keys,
            countsByFormat = g.photos.groupingBy { it.format }.eachCount(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUi())

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun connect(ssid: String, password: String) {
        settingsRepo.update { it.copy(lastSsid = ssid, lastPassword = password) }
        session.connect(ssid, password)
    }

    fun connectCurrentWifi() = session.connectCurrentWifi()

    fun disconnect() {
        selection.value = emptySet()
        thumbnails.clear()
        session.disconnect()
    }

    fun toggle(photo: CameraPhoto) = selection.update {
        if (photo.stableKey in it) it - photo.stableKey else it + photo.stableKey
    }

    fun selectAllVisible() = selection.update { it + gallery.value.visible.map(CameraPhoto::stableKey) }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun setFilter(f: ViewFilter) {
        filter.value = f
    }

    fun downloadSelected() {
        val keys = gallery.value.selected
        val photos = session.gallery.value.photos.filter { it.stableKey in keys }
        transfers.enqueue(photos)
        selection.value = emptySet()
    }

    fun download(photo: CameraPhoto) = transfers.enqueue(listOf(photo))

    fun setDestination(tree: Uri?) = settingsRepo.update { it.copy(destinationTree = tree) }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsRepo.update(transform)

    fun photoByKey(key: String): CameraPhoto? = session.gallery.value.photos.firstOrNull { it.stableKey == key }
}
