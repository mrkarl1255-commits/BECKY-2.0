package com.becky.bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.becky.bridge.R
import com.becky.bridge.bluetooth.BridgeRepository
import com.becky.bridge.logging.BeckyLogger
import com.becky.bridge.logging.LogCategory
import com.becky.bridge.model.ConnectionState
import com.becky.bridge.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the BLE connection alive while the app is in the background
 * (section 10 of the spec).
 *
 * This is a minimal, non-invasive foreground service: it only shows a
 * persistent notification with the current connection status, as
 * explicitly requested. It does not perform any hidden background
 * work beyond what is required to keep the GATT connection object
 * alive at the OS level.
 */
class BridgeForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "becky_bridge_status_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.becky.bridge.action.START_FOREGROUND"
        const val ACTION_STOP = "com.becky.bridge.action.STOP_FOREGROUND"
    }

    private var job: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private lateinit var repository: BridgeRepository

    override fun onCreate() {
        super.onCreate()
        repository = BridgeRepository.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startAsForeground()
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification(ConnectionState.DISCONNECTED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            BeckyLogger.i(LogCategory.APP, "Servicio en primer plano iniciado")
        } catch (e: Exception) {
            BeckyLogger.e(LogCategory.APP, "No se pudo iniciar el servicio en primer plano", e)
        }

        job = repository.connectionState.onEach { state ->
            updateNotification(state)
        }.launchIn(serviceScope)
    }

    private fun updateNotification(state: ConnectionState) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val statusText = when (state) {
            ConnectionState.READY, ConnectionState.CONNECTED -> "Conectado"
            ConnectionState.CONNECTING, ConnectionState.DISCOVERING_SERVICES -> "Conectando..."
            ConnectionState.RECONNECTING -> "Reconectando..."
            ConnectionState.ERROR -> "Error de conexion"
            ConnectionState.DISCONNECTED -> "Desconectado"
        }

        val contentIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BECKY BRIDGE")
            .setContentText("Estado de conexion: $statusText")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Estado de BECKY BRIDGE",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado de conexion con el reloj inteligente"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
