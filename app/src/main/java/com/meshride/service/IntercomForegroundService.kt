package com.meshride.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.meshride.R
import com.meshride.data.datasource.audio.AudioToneFeedbackGenerator
import com.meshride.data.datasource.audio.FeedbackTone
import com.meshride.domain.repository.AudioEngineRepository
import com.meshride.domain.repository.BluetoothScoRepository
import com.meshride.domain.repository.IntercomRepository
import com.meshride.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class IntercomForegroundService : Service() {

    @Inject
    lateinit var intercomRepository: IntercomRepository

    @Inject
    lateinit var bluetoothScoRepository: BluetoothScoRepository

    @Inject
    lateinit var audioEngineRepository: AudioEngineRepository

    @Inject
    lateinit var headsetMediaButtonManager: HeadsetMediaButtonManager

    @Inject
    lateinit var audioToneFeedbackGenerator: AudioToneFeedbackGenerator

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "meshride_intercom_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_INTERCOM"
        const val ACTION_STOP = "ACTION_STOP_INTERCOM"
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        createNotificationChannel()
        setupHeadsetButtonInterception()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    audioEngineRepository.stopAudioEngine()
                    bluetoothScoRepository.stopBluetoothSco()
                    intercomRepository.stopIntercomSession()
                }
                headsetMediaButtonManager.deactivateMediaSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                headsetMediaButtonManager.activateMediaSession()
                serviceScope.launch {
                    intercomRepository.startIntercomSession()
                    audioEngineRepository.startAudioEngine()
                    bluetoothScoRepository.startBluetoothSco()
                }
            }
        }
        return START_STICKY
    }

    private fun setupHeadsetButtonInterception() {
        headsetMediaButtonManager.onButtonClickListener = {
            serviceScope.launch {
                intercomRepository.toggleMicrophoneMute().onSuccess { isMuted ->
                    val tone = if (isMuted) FeedbackTone.MIC_MUTED else FeedbackTone.MIC_OPEN
                    audioToneFeedbackGenerator.playFeedbackTone(tone)
                }
            }
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Intercom Active - Helmet SCO & Mesh Ready")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.intercom_notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.intercom_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.intercom_notification_channel_desc)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeshRide::IntercomWakeLock").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10 minutes sliding window
        }
    }

    override fun onDestroy() {
        headsetMediaButtonManager.deactivateMediaSession()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
