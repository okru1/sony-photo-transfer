package com.sonyphototransfer.app.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sonyphototransfer.app.MainActivity
import com.sonyphototransfer.app.R
import com.sonyphototransfer.app.appContainer
import com.sonyphototransfer.app.ui.formatBytes
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process, CPU and Wi-Fi awake while
 * [TransferManager] has work, and shows progress in a notification.
 */
class TransferService : LifecycleService() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(TransferState()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        acquireLocks()
        val manager = appContainer.transfers
        lifecycleScope.launch {
            manager.state.collect { state ->
                if (!state.isActive) {
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_CANCEL) appContainer.transfers.cancelAll()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    /** Android 15+ caps dataSync services; stop cleanly instead of being killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        appContainer.transfers.cancelAll()
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private fun acquireLocks() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SonyPhotoTransfer:transfer")
            .apply { acquire(MAX_LOCK_MS) }
        wifiLock = getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "SonyPhotoTransfer:transfer")
            .apply { acquire() }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_transfers),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_transfers_description) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: TransferState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this,
            1,
            Intent(this, TransferService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val total = state.bytesTotal
        val text = buildString {
            append("${state.done} completadas · ${state.pending} pendientes")
            if (state.failed > 0) append(" · ${state.failed} con error")
            append(" · ${formatBytes(state.bytesDone)}")
            if (total != null) append(" de ${formatBytes(total)}")
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transfer)
            .setContentTitle(getString(R.string.notification_transfer_title))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_cancel), cancel)
        if (total != null && total > 0) {
            builder.setProgress(1000, ((state.bytesDone * 1000) / total).toInt().coerceIn(0, 1000), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "transfers"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_CANCEL = "com.sonyphototransfer.app.CANCEL_TRANSFERS"
        private const val MAX_LOCK_MS = 6L * 60 * 60 * 1000
    }
}
