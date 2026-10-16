package com.sonyphototransfer.app

import android.app.Application
import android.content.Context
import com.sonyphototransfer.app.camera.CameraSession
import com.sonyphototransfer.app.data.SettingsRepository
import com.sonyphototransfer.app.storage.DownloadIndex
import com.sonyphototransfer.app.storage.PhotoSaver
import com.sonyphototransfer.app.thumbnails.ThumbnailLoader
import com.sonyphototransfer.app.transfer.TransferManager
import com.sonyphototransfer.app.wifi.CameraWifi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SonyPhotoTransferApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-written dependency graph; small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** Outlives screens so the Wi-Fi request and transfers survive rotation and backgrounding. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    val wifi = CameraWifi(context)
    val session = CameraSession(wifi, appScope)
    val thumbnails = ThumbnailLoader(session)
    val downloadIndex = DownloadIndex(context)
    val photoSaver = PhotoSaver(context)
    val transfers = TransferManager(context, session, settings, photoSaver, downloadIndex, appScope)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SonyPhotoTransferApp).container
