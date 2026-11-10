package com.landxcape.synkromesh.data.datasource.mesh

import com.landxcape.synkromesh.domain.model.AudioPacket
import com.landxcape.synkromesh.domain.model.MeshPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UdpMeshSocketDriver @Inject constructor(
    private val locationDataSource: LocationTelemetryDataSource
) {

    companion object {
        const val UDP_PORT = 50005
        const val MAX_DATAGRAM_SIZE = 1024
        private const val MAGIC_HEADER: Short = 0x534D // 'SM' (SynkroMesh)
        private const val PACKET_TYPE_AUDIO: Byte = 0x01
        private const val PACKET_TYPE_HEARTBEAT: Byte = 0x02
    }

    private val socketScope = CoroutineScope(Dispatchers.IO)
    private var socketJob: Job? = null
    private var cleanerJob: Job? = null
    private var datagramSocket: DatagramSocket? = null

    var localDeviceId: String = ""
    var localCallsign: String = "Rider"
    var activeChannel: Int = 1
    var activePackToken: String? = null

    private val _incomingAudioFlow = MutableSharedFlow<AudioPacket>(extraBufferCapacity = 32)
    val incomingAudioFlow: SharedFlow<AudioPacket> = _incomingAudioFlow.asSharedFlow()

    private val _udpPeersMap = ConcurrentHashMap<String, MeshPeer>()
    private val _udpPeersFlow = MutableStateFlow<List<MeshPeer>>(emptyList())
    val udpPeersFlow: StateFlow<List<MeshPeer>> = _udpPeersFlow.asStateFlow()

    private val _isSocketActive = MutableStateFlow(false)
    val isSocketActive: StateFlow<Boolean> = _isSocketActive.asStateFlow()

    private val broadcastAddress: InetAddress by lazy {
        InetAddress.getByName("255.255.255.255")
    }

    fun startSocket(): Result<Unit> = runCatching {
        if (_isSocketActive.value) return@runCatching

        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(UDP_PORT))
        }

        datagramSocket = socket
        _isSocketActive.value = true

        socketJob = socketScope.launch {
            val receiveBuffer = ByteArray(MAX_DATAGRAM_SIZE)
            val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)

            while (isActive && _isSocketActive.value) {
                try {
                    socket.receive(packet)
                    if (packet.length > 20) {
                        handleIncomingDatagram(receiveBuffer, packet.length, packet.address?.hostAddress)
                    }
                } catch (e: Exception) {
                    if (!_isSocketActive.value) break
                    delay(100)
                }
            }
        }

        // Periodic stale peer cleaner (purges peers silent for > 6 seconds)
        cleanerJob = socketScope.launch {
            while (isActive && _isSocketActive.value) {
                delay(2000L)
                val now = System.currentTimeMillis()
                var changed = false
                val iterator = _udpPeersMap.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value.lastSeenTimestampMs > 6000L) {
                        iterator.remove()
                        changed = true
                    }
                }
                if (changed) {
                    _udpPeersFlow.value = _udpPeersMap.values.toList()
                }
            }
        }
    }

    fun sendPacket(audioPacket: AudioPacket): Result<Unit> = runCatching {
        sendDatagram(PACKET_TYPE_AUDIO, audioPacket.sequenceNumber, audioPacket.payload)
    }

    fun sendHeartbeat(
        lat: Double? = null,
        lon: Double? = null,
        alt: Double? = null,
        speedKmh: Float? = null,
        bearingDegrees: Float? = null,
        batteryPercent: Int? = null
    ): Result<Unit> = runCatching {
        if (lat != null && lon != null) {
            val telemetryBuffer = ByteBuffer.allocate(33).apply {
                order(ByteOrder.BIG_ENDIAN)
                putDouble(lat)
                putDouble(lon)
                putDouble(alt ?: 0.0)
                putFloat(speedKmh ?: 0f)
                putFloat(bearingDegrees ?: 0f)
                put((batteryPercent ?: 100).toByte())
            }
            sendDatagram(PACKET_TYPE_HEARTBEAT, 0L, telemetryBuffer.array())
        } else {
            sendDatagram(PACKET_TYPE_HEARTBEAT, 0L, ByteArray(0))
        }
    }

    private fun sendDatagram(type: Byte, seq: Long, payload: ByteArray) {
        val socket = datagramSocket ?: return
        val tokenPart = if (!activePackToken.isNullOrBlank()) "|${activePackToken.hashCode()}" else ""
        val senderTag = "$localDeviceId|$localCallsign|$activeChannel$tokenPart"
        val senderBytes = senderTag.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(2 + 1 + 8 + 8 + 1 + senderBytes.size + payload.size).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(MAGIC_HEADER)
            put(type)
            putLong(seq)
            putLong(System.currentTimeMillis())
            put(senderBytes.size.toByte())
            put(senderBytes)
            put(payload)
        }

        val data = buffer.array()
        val datagram = DatagramPacket(data, data.size, broadcastAddress, UDP_PORT)
        socket.send(datagram)
    }

    private fun handleIncomingDatagram(data: ByteArray, length: Int, hostIp: String?) {
        val buffer = ByteBuffer.wrap(data, 0, length).apply {
            order(ByteOrder.BIG_ENDIAN)
        }

        val magic = buffer.short
        if (magic != MAGIC_HEADER) return

        val type = buffer.get()
        val seq = buffer.long
        val timestamp = buffer.long
        val tagLen = buffer.get().toInt() and 0xFF
        if (buffer.remaining() < tagLen) return

        val tagBytes = ByteArray(tagLen)
        buffer.get(tagBytes)
        val senderTag = String(tagBytes, Charsets.UTF_8)

        // Parse: deviceId|callsign|channel|tokenHash
        val parts = senderTag.split("|")
        val senderDeviceId = parts.getOrNull(0) ?: return
        val senderCallsign = parts.getOrNull(1) ?: "Rider $senderDeviceId"
        val senderChannel = parts.getOrNull(2)?.toIntOrNull() ?: 1
        val senderTokenHash = parts.getOrNull(3)

        // CRITICAL 1: DROP SELF-PACKETS (ELIMINATES SELF-ECHO)
        if (senderDeviceId == localDeviceId && localDeviceId.isNotBlank()) {
            return
        }

        // CRITICAL 2: FILTER CHANNEL (Only hear riders on the same Channel)
        if (senderChannel != activeChannel) {
            return
        }

        // CRITICAL 3: AUTHENTICATE PACK TOKEN (Rejects unauthenticated third-party packets)
        val expectedToken = activePackToken
        if (!expectedToken.isNullOrBlank()) {
            val expectedHash = expectedToken.hashCode().toString()
            if (senderTokenHash != expectedHash) {
                // Drop rogue or unauthenticated packet
                return
            }
        }

        val payloadSize = buffer.remaining()
        val payload = ByteArray(payloadSize)
        buffer.get(payload)

        val isSpeaking = (type == PACKET_TYPE_AUDIO && payloadSize > 0)

        // Telemetry parsing if payload contains at least 33 bytes (lat, lon, alt, speed, bearing, battery)
        var peerLat: Double? = null
        var peerLon: Double? = null
        var peerAlt: Double? = null
        var peerSpeed: Float? = null
        var peerBearing: Float? = null
        var peerBattery: Int? = null

        if (type == PACKET_TYPE_HEARTBEAT && payloadSize >= 33) {
            try {
                val tBuf = ByteBuffer.wrap(payload, 0, 33).order(ByteOrder.BIG_ENDIAN)
                val lat = tBuf.double
                val lon = tBuf.double
                val alt = tBuf.double
                val spd = tBuf.float
                val brg = tBuf.float
                val bat = tBuf.get().toInt() and 0xFF

                if (lat != 0.0 || lon != 0.0) {
                    peerLat = lat
                    peerLon = lon
                    peerAlt = alt
                    peerSpeed = spd
                    peerBearing = brg
                    peerBattery = bat
                }
            } catch (_: Exception) {}
        }

        val existing = _udpPeersMap[senderDeviceId]
        val myLoc = locationDataSource.currentLocation.value

        val (calculatedDistance, calculatedRelBearing) = if (myLoc != null && peerLat != null && peerLon != null) {
            val dist = LocationTelemetryDataSource.calculateHaversineDistanceMeters(
                myLoc.latitude, myLoc.longitude, peerLat, peerLon
            )
            val peerBearingDeg = LocationTelemetryDataSource.calculateBearingDegrees(
                myLoc.latitude, myLoc.longitude, peerLat, peerLon
            )
            val relBearing = LocationTelemetryDataSource.calculateRelativeBearing(
                myLoc.bearingDegrees, peerBearingDeg
            )
            Pair(dist, relBearing)
        } else {
            Pair(null, null)
        }

        // Update Peer Presence & Telemetry
        _udpPeersMap[senderDeviceId] = MeshPeer(
            id = senderDeviceId,
            callsign = senderCallsign,
            isLocal = false,
            ipAddress = hostIp,
            signalDbm = -55,
            isTransmitting = isSpeaking,
            lastSeenTimestampMs = System.currentTimeMillis(),
            latitude = peerLat ?: existing?.latitude,
            longitude = peerLon ?: existing?.longitude,
            altitudeMeters = peerAlt ?: existing?.altitudeMeters,
            speedKmh = peerSpeed ?: existing?.speedKmh,
            bearingDegrees = peerBearing ?: existing?.bearingDegrees,
            batteryPercent = peerBattery ?: existing?.batteryPercent,
            estimatedDistanceMeters = calculatedDistance ?: existing?.estimatedDistanceMeters ?: LocationTelemetryDataSource.estimateDistanceRssiMeters(-55),
            relativeBearingDegrees = calculatedRelBearing ?: existing?.relativeBearingDegrees
        )
        _udpPeersFlow.value = _udpPeersMap.values.toList()

        // If audio packet, emit for decoding & playback
        if (type == PACKET_TYPE_AUDIO && payloadSize > 0) {
            val audioPacket = AudioPacket(
                sequenceNumber = seq,
                senderId = senderDeviceId,
                timestampMs = timestamp,
                payload = payload
            )
            _incomingAudioFlow.tryEmit(audioPacket)
        }
    }

    fun stopSocket(): Result<Unit> = runCatching {
        _isSocketActive.value = false
        socketJob?.cancel()
        socketJob = null
        cleanerJob?.cancel()
        cleanerJob = null
        datagramSocket?.close()
        datagramSocket = null
        _udpPeersMap.clear()
        _udpPeersFlow.value = emptyList()
    }
}
