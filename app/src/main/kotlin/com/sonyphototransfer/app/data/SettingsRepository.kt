package com.sonyphototransfer.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What to do with a RAW file, whose original the camera will not send over Wi-Fi. */
enum class RawHandling {
    /** Skip it and say why. */
    SKIP,

    /** Save the camera's large JPEG preview, clearly named as a preview. */
    SAVE_PREVIEW,
}

/** Which formats are queued when the user selects photos. */
enum class FormatFilter { ALL, JPEG_ONLY, RAW_ONLY }

data class AppSettings(
    /** Folder picked with the system picker; null means Pictures/SonyPhotoTransfer via MediaStore. */
    val destinationTree: Uri? = null,
    val downloadFormats: FormatFilter = FormatFilter.ALL,
    val rawHandling: RawHandling = RawHandling.SKIP,
    val keepScreenOn: Boolean = true,
    val lastSsid: String = "",
    val lastPassword: String = "",
)

/**
 * Plain SharedPreferences: a handful of values, read once at start-up.
 * The camera's Wi-Fi password is stored in app-private storage only, for convenience.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        save(_settings.value)
    }

    private fun load() = AppSettings(
        destinationTree = prefs.getString(KEY_TREE, null)?.let(Uri::parse),
        downloadFormats = enumOrDefault(prefs.getString(KEY_FORMATS, null), FormatFilter.ALL),
        rawHandling = enumOrDefault(prefs.getString(KEY_RAW, null), RawHandling.SKIP),
        keepScreenOn = prefs.getBoolean(KEY_SCREEN_ON, true),
        lastSsid = prefs.getString(KEY_SSID, "").orEmpty(),
        lastPassword = prefs.getString(KEY_PASSWORD, "").orEmpty(),
    )

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString(KEY_TREE, s.destinationTree?.toString())
            .putString(KEY_FORMATS, s.downloadFormats.name)
            .putString(KEY_RAW, s.rawHandling.name)
            .putBoolean(KEY_SCREEN_ON, s.keepScreenOn)
            .putString(KEY_SSID, s.lastSsid)
            .putString(KEY_PASSWORD, s.lastPassword)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_TREE = "destination_tree"
        const val KEY_FORMATS = "download_formats"
        const val KEY_RAW = "raw_handling"
        const val KEY_SCREEN_ON = "keep_screen_on"
        const val KEY_SSID = "last_ssid"
        const val KEY_PASSWORD = "last_password"
    }
}
