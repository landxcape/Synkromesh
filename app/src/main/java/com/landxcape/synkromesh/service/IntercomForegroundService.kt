package com.landxcape.synkromesh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.landxcape.synkromesh.R
import com.landxcape.synkromesh.data.datasource.audio.AudioToneFeedbackGenerator
import com.landxcape.synkromesh.data.datasource.audio.TacticalVoiceCueEngine
import com.landxcape.synkromesh.data.datasource.audio.FeedbackTone
import com.landxcape.synkromesh.domain.model.MeshState
import com.landxcape.synkromesh.domain.model.TransmissionState
import com.landxcape.synkromesh.domain.repository.AudioEngineRepository
import com.landxcape.synkromesh.domain.repository.BluetoothScoRepository
import com.landxcape.synkromesh.domain.repository.IntercomRepository
import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
import com.landxcape.synkromesh.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
    lateinit var meshNetworkRepository: MeshNetworkRepository

    @Inject
    lateinit var headsetMediaButtonManager: HeadsetMediaButtonManager

    @Inject
    lateinit var audioToneFeedbackGenerator: AudioToneFeedbackGenerator

    @Inject
    lateinit var tacticalVoiceCueEngine: TacticalVoiceCueEngine

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var peerMonitoringJob: Job? = null
    private var powerMonitorJob: Job? = null
    private var audioActivityMonitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var lastAudioActivityTimestampMs = System.currentTimeMillis()

    @Volatile
    private var isLowLatencyMode = true

    companion object {
        const val CHANNEL_ID = "synkromesh_intercom_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_INTERCOM"
        const val ACTION_STOP = "ACTION_STOP_INTERCOM"
        const val ACTION_STOP_AND_EXIT = "ACTION_STOP_AND_EXIT"

        private val _isLowLatencyModeFlow = MutableStateFlow(true)
        val isLowLatencyModeFlow: StateFlow<Boolean> = _isLowLatencyModeFlow.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        acquireWifiLock()
        acquireMulticastLock()
        createNotificationChannel()
        setupHeadsetButtonInterception()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardownServiceSession()
                return START_NOT_STICKY
            }
            ACTION_STOP_AND_EXIT -> {
                teardownServiceSession()
                val exitIntent = Intent(this, com.landxcape.synkromesh.presentation.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(com.landxcape.synkromesh.presentation.MainActivity.EXTRA_EXIT, true)
                }
                startActivity(exitIntent)
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                headsetMediaButtonManager.activateMediaSession()
                startPeerNotificationMonitoring()
                startAdaptivePowerMonitoring()
            }
        }
        return START_STICKY
    }

    private fun teardownServiceSession() {
        peerMonitoringJob?.cancel()
        peerMonitoringJob = null
        powerMonitorJob?.cancel()
        powerMonitorJob = null
        audioActivityMonitorJob?.cancel()
        audioActivityMonitorJob = null
        isLowLatencyMode = true
        _isLowLatencyModeFlow.value = true
        serviceScope.launch {
            meshNetworkRepository.stopMeshSession()
            audioEngineRepository.stopAudioEngine()
            bluetoothScoRepository.stopBluetoothSco()
            intercomRepository.stopIntercomSession()
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        }
        headsetMediaButtonManager.deactivateMediaSession()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        multicastLock?.let { if (it.isHeld) it.release() }
        tacticalVoiceCueEngine.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startPeerNotificationMonitoring() {
        peerMonitoringJob?.cancel()
        val knownPeerIds = mutableSetOf<String>()
        peerMonitoringJob = serviceScope.launch {
            meshNetworkRepository.observePeers().collect { peers ->
                val currentPeerIds = peers.map { it.id }.toSet()
                for (peer in peers) {
                    if (!knownPeerIds.contains(peer.id)) {
                        tacticalVoiceCueEngine.announcePeerJoined(peer.callsign, 1)
                        if (peer.hopCount > 1) {
                            tacticalVoiceCueEngine.announceRelayEngaged(peer.callsign, peer.hopCount)
                        }
                    }
                    if (peer.batteryPercent != null && peer.batteryPercent!! <= 20) {
                        tacticalVoiceCueEngine.announceBatteryCritical(peer.callsign, peer.batteryPercent!!)
                    }
                }
                for (oldId in knownPeerIds) {
                    if (!currentPeerIds.contains(oldId)) {
                        tacticalVoiceCueEngine.announcePeerLost("Rider")
                    }
                }
                knownPeerIds.clear()
                knownPeerIds.addAll(currentPeerIds)

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val statusText = if (peers.isEmpty()) {
                    "Mesh Searching • Helmet SCO Ready"
                } else {
                    "Mesh Active • ${peers.size} Rider(s) Connected"
                }
                notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText))
            }
        }
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
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopExitIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, IntercomForegroundService::class.java).apply {
                action = ACTION_STOP_AND_EXIT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.intercom_notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP & EXIT",
                stopExitIntent
            )
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
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Synkromesh::IntercomWakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun acquireWifiLock() {
        setWifiLockMode(lowLatency = true)
    }

    private fun notifyAudioActivity() {
        lastAudioActivityTimestampMs = System.currentTimeMillis()
        if (!isLowLatencyMode) {
            setWifiLockMode(lowLatency = true)
        }
    }

    private fun setWifiLockMode(lowLatency: Boolean) {
        if (isLowLatencyMode == lowLatency && wifiLock?.isHeld == true) return
        isLowLatencyMode = lowLatency
        _isLowLatencyModeFlow.value = lowLatency
        wifiLock?.let { if (it.isHeld) it.release() }
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifiManager?.createWifiLock(lockType, "Synkromesh::IntercomWifiLock")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun startAdaptivePowerMonitoring() {
        audioActivityMonitorJob?.cancel()
        audioActivityMonitorJob = serviceScope.launch {
            launch {
                meshNetworkRepository.observeIncomingAudioPackets().collect {
                    notifyAudioActivity()
                }
            }
            launch {
                intercomRepository.observeTransmissionState().collect { txState ->
                    if (txState == TransmissionState.TRANSMITTING) {
                        notifyAudioActivity()
                    }
                }
            }
        }

        // Active session maintains low-latency Wi-Fi lock and unthrottled UDP reception
        setWifiLockMode(lowLatency = true)
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("Synkromesh::IntercomMulticastLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        peerMonitoringJob?.cancel()
        peerMonitoringJob = null
        powerMonitorJob?.cancel()
        powerMonitorJob = null
        audioActivityMonitorJob?.cancel()
        audioActivityMonitorJob = null
        isLowLatencyMode = true
        _isLowLatencyModeFlow.value = true
        serviceScope.launch {
            meshNetworkRepository.stopMeshSession()
            audioEngineRepository.stopAudioEngine()
            bluetoothScoRepository.stopBluetoothSco()
            intercomRepository.stopIntercomSession()
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_NORMAL
        }
        headsetMediaButtonManager.deactivateMediaSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        peerMonitoringJob?.cancel()
        powerMonitorJob?.cancel()
        audioActivityMonitorJob?.cancel()
        isLowLatencyMode = true
        _isLowLatencyModeFlow.value = true
        headsetMediaButtonManager.deactivateMediaSession()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wifiLock?.let {
            if (it.isHeld) it.release()
        }
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_NORMAL
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
