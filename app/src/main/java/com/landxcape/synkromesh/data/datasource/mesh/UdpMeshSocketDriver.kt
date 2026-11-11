package com.landxcape.synkromesh.data.datasource.mesh

import com.landxcape.synkromesh.data.datasource.mesh.routing.PacketRelayFilter
import com.landxcape.synkromesh.data.datasource.mesh.security.PackTokenAuthenticator
import com.landxcape.synkromesh.data.datasource.mesh.security.PayloadCipher
import com.landxcape.synkromesh.data.datasource.mesh.security.ReplayWindow
import com.landxcape.synkromesh.domain.model.AudioPacket
import com.landxcape.synkromesh.domain.model.MeshPeer
import android.net.Network
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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
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
        const val DEFAULT_HOP_LIMIT: Byte = 3
        private const val MAGIC_HEADER: Short = 0x534D // 'SM' (SynkroMesh)
        private const val PACKET_TYPE_AUDIO: Byte = 0x01
        private const val PACKET_TYPE_HEARTBEAT: Byte = 0x02
    }

    private val socketScope = CoroutineScope(Dispatchers.IO)
    private var socketJob: Job? = null
    private var cleanerJob: Job? = null
    private var datagramSocket: DatagramSocket? = null

    var localDeviceId: String = ""
        set(value) {
            field = value
            invalidateSenderHeader()
        }
    var localCallsign: String = "Rider"
        set(value) {
            field = value
            invalidateSenderHeader()
        }
    var activeChannel: Int = 1
        set(value) {
            field = value
            invalidateSenderHeader()
        }
    var activePackToken: String? = null
        set(value) {
            field = value
            derivedEncryptionKey = if (!value.isNullOrBlank()) {
                PayloadCipher.deriveKey(value)
            } else null
            invalidateSenderHeader()
        }

    @Volatile
    private var derivedEncryptionKey: ByteArray? = null

    private val replayWindows = ConcurrentHashMap<String, ReplayWindow>()
    private val packetRelayFilter = PacketRelayFilter(256)
    private val lastReceivedTimestamps = ConcurrentHashMap<String, Long>()

    @Volatile
    private var cachedSenderHeaderBytes: ByteArray? = null
    private val headerLock = Any()

    private val sendBuffer = ByteBuffer.allocate(MAX_DATAGRAM_SIZE).apply {
        order(ByteOrder.BIG_ENDIAN)
    }
    private val sendLock = Any()

    @Volatile
    private var cachedBroadcastTargets: List<InetAddress> = emptyList()
    private var lastBroadcastRefreshTime = 0L

    private fun invalidateSenderHeader() {
        synchronized(headerLock) {
            val token = activePackToken
            val tokenSig = if (!token.isNullOrBlank()) {
                "|${PackTokenAuthenticator.computeTokenSignature(token, "$localDeviceId|$activeChannel")}"
            } else ""
            val tag = "$localDeviceId|$localCallsign|$activeChannel$tokenSig"
            cachedSenderHeaderBytes = tag.toByteArray(Charsets.UTF_8)
        }
    }

    private fun getSenderHeaderBytes(): ByteArray {
        return cachedSenderHeaderBytes ?: run {
            invalidateSenderHeader()
            cachedSenderHeaderBytes!!
        }
    }

    private fun getCachedBroadcastAddresses(): List<InetAddress> {
        val now = System.currentTimeMillis()
        if (cachedBroadcastTargets.isEmpty() || now - lastBroadcastRefreshTime > 15_000L) {
            cachedBroadcastTargets = getActiveBroadcastAddresses()
            lastBroadcastRefreshTime = now
        }
        return cachedBroadcastTargets
    }

    private val _incomingAudioFlow = MutableSharedFlow<AudioPacket>(extraBufferCapacity = 32)
    val incomingAudioFlow: SharedFlow<AudioPacket> = _incomingAudioFlow.asSharedFlow()

    private val _udpPeersMap = ConcurrentHashMap<String, MeshPeer>()
    private val _udpPeersFlow = MutableStateFlow<List<MeshPeer>>(emptyList())
    val udpPeersFlow: StateFlow<List<MeshPeer>> = _udpPeersFlow.asStateFlow()

    private val _isSocketActive = MutableStateFlow(false)
    val isSocketActive: StateFlow<Boolean> = _isSocketActive.asStateFlow()

    private var broadcastTarget: InetAddress = InetAddress.getByName("255.255.255.255")

    fun setBroadcastTarget(ip: String?) {
        broadcastTarget = try {
            if (!ip.isNullOrBlank()) InetAddress.getByName(ip)
            else InetAddress.getByName("255.255.255.255")
        } catch (_: Exception) {
            InetAddress.getByName("255.255.255.255")
        }
    }

    fun startSocket(network: Network? = null): Result<Unit> = runCatching {
        if (_isSocketActive.value) return@runCatching

        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(UDP_PORT))
        }

        try {
            network?.bindSocket(socket)
        } catch (_: Exception) {}

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
                        lastReceivedTimestamps.remove(entry.key)
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
        val echoes = lastReceivedTimestamps.entries.take(4)
        val echoByteSize = 1 + (echoes.size * 12)
        val telemetryBuffer = ByteBuffer.allocate(33 + echoByteSize).apply {
            order(ByteOrder.BIG_ENDIAN)
            putDouble(lat ?: 0.0)
            putDouble(lon ?: 0.0)
            putDouble(alt ?: 0.0)
            putFloat(speedKmh ?: 0f)
            putFloat(bearingDegrees ?: 0f)
            put((batteryPercent ?: 100).toByte())
            put(echoes.size.toByte())
            for (entry in echoes) {
                putInt(entry.key.hashCode())
                putLong(entry.value)
            }
        }
        sendDatagram(PACKET_TYPE_HEARTBEAT, 0L, telemetryBuffer.array())
    }

    private fun getActiveBroadcastAddresses(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    val bcast = addr.broadcast
                    if (bcast != null && bcast is Inet4Address) {
                        targets.add(bcast)
                    }
                }
            }
        } catch (_: Exception) {}
        if (targets.isEmpty()) {
            try {
                targets.add(InetAddress.getByName("255.255.255.255"))
            } catch (_: Exception) {}
        }
        return targets
    }

    private fun sendDatagram(type: Byte, seq: Long, payload: ByteArray) {
        val socket = datagramSocket ?: return
        val senderBytes = getSenderHeaderBytes()
        val now = System.currentTimeMillis()
        val headerSize = 2 + 1 + 8 + 8 + 1 + senderBytes.size
        val fullType = ((DEFAULT_HOP_LIMIT.toInt() shl 4) or (type.toInt() and 0x0F)).toByte()

        val data: ByteArray
        synchronized(sendLock) {
            sendBuffer.clear()
            sendBuffer.putShort(MAGIC_HEADER)
            sendBuffer.put(fullType)
            sendBuffer.putLong(seq)
            sendBuffer.putLong(now)
            sendBuffer.put(senderBytes.size.toByte())
            sendBuffer.put(senderBytes)

            val headerAad = sendBuffer.array().copyOf(headerSize)
            // Mask TTL nibble in AAD so downstream relays can decrement TTL without invalidating cipher tag
            headerAad[2] = (type.toInt() and 0x0F).toByte()

            val key = derivedEncryptionKey
            val finalPayload = if (key != null && payload.isNotEmpty()) {
                PayloadCipher.encrypt(key, localDeviceId, seq, headerAad, payload)
            } else {
                payload
            }

            val totalSize = headerSize + finalPayload.size
            if (totalSize > MAX_DATAGRAM_SIZE) return

            sendBuffer.put(finalPayload)
            data = sendBuffer.array().copyOf(totalSize)
        }

        if (broadcastTarget.hostAddress != "255.255.255.255") {
            try {
                val datagram = DatagramPacket(data, data.size, broadcastTarget, UDP_PORT)
                socket.send(datagram)
            } catch (_: Exception) {}
        } else {
            val targets = getCachedBroadcastAddresses()
            for (target in targets) {
                try {
                    val datagram = DatagramPacket(data, data.size, target, UDP_PORT)
                    socket.send(datagram)
                } catch (_: Exception) {}
            }
        }
    }

    private fun relayDatagram(data: ByteArray) {
        val socket = datagramSocket ?: return
        if (broadcastTarget.hostAddress != "255.255.255.255") {
            try {
                val datagram = DatagramPacket(data, data.size, broadcastTarget, UDP_PORT)
                socket.send(datagram)
            } catch (_: Exception) {}
        } else {
            val targets = getCachedBroadcastAddresses()
            for (target in targets) {
                try {
                    val datagram = DatagramPacket(data, data.size, target, UDP_PORT)
                    socket.send(datagram)
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleIncomingDatagram(data: ByteArray, length: Int, hostIp: String?) {
        val buffer = ByteBuffer.wrap(data, 0, length).apply {
            order(ByteOrder.BIG_ENDIAN)
        }

        val magic = buffer.short
        if (magic != MAGIC_HEADER) return

        val rawType = buffer.get()
        val rawHopLimit = (rawType.toInt() shr 4) and 0x0F
        val hopLimit = if (rawHopLimit == 0) 1 else rawHopLimit
        val type = (rawType.toInt() and 0x0F).toByte()
        val calculatedHopCount = (DEFAULT_HOP_LIMIT.toInt() - hopLimit) + 1

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

        // CRITICAL 3: AUTHENTICATE PACK TOKEN (Enforces strict HMAC token validation)
        val expectedToken = activePackToken
        val packetAuthData = "$senderDeviceId|$senderChannel"
        if (!PackTokenAuthenticator.isSignatureValid(expectedToken, senderTokenHash, packetAuthData)) {
            return
        }

        // CRITICAL 4: SLIDING-WINDOW REPLAY FILTER (Drops duplicate, stale, or replayed packets)
        val replayWindow = replayWindows.computeIfAbsent(senderDeviceId) { ReplayWindow() }
        if (!replayWindow.isPacketAcceptable(seq)) {
            return
        }

        // MULTI-HOP PACKET RELAYING:
        // If TTL > 1 and this is the first time we see this packet, decrement TTL and re-broadcast to mesh
        if (hopLimit > 1 && packetRelayFilter.shouldRelay(senderDeviceId, seq)) {
            val nextHopLimit = hopLimit - 1
            val nextRawType = ((nextHopLimit shl 4) or (type.toInt() and 0x0F)).toByte()
            val relayData = data.copyOf(length)
            relayData[2] = nextRawType
            relayDatagram(relayData)
        }

        // CRITICAL 5: END-TO-END PAYLOAD DECRYPTION & AAD AUTHENTICATION
        val headerEndPos = buffer.position()
        val headerAad = data.copyOfRange(0, headerEndPos)
        // Mask TTL nibble so relayed datagrams decrypt seamlessly
        headerAad[2] = (rawType.toInt() and 0x0F).toByte()

        val rawPayloadSize = buffer.remaining()
        val rawPayload = ByteArray(rawPayloadSize)
        buffer.get(rawPayload)

        val key = derivedEncryptionKey
        val payload = if (key != null && rawPayload.isNotEmpty()) {
            val decrypted = PayloadCipher.decrypt(key, senderDeviceId, seq, headerAad, rawPayload)
            if (decrypted == null) {
                // Cryptographic tag verification failed or wrong key: drop packet!
                return
            }
            decrypted
        } else {
            rawPayload
        }
        val payloadSize = payload.size
        lastReceivedTimestamps[senderDeviceId] = timestamp

        val isSpeaking = (type == PACKET_TYPE_AUDIO && payloadSize > 0)

        // Telemetry parsing if payload contains at least 33 bytes (lat, lon, alt, speed, bearing, battery)
        var peerLat: Double? = null
        var peerLon: Double? = null
        var peerAlt: Double? = null
        var peerSpeed: Float? = null
        var peerBearing: Float? = null
        var peerBattery: Int? = null
        var measuredRttMs: Int? = null

        if (type == PACKET_TYPE_HEARTBEAT && payloadSize >= 33) {
            try {
                val tBuf = ByteBuffer.wrap(payload, 0, payloadSize).order(ByteOrder.BIG_ENDIAN)
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

                if (payloadSize > 33) {
                    val echoCount = tBuf.get().toInt() and 0xFF
                    val myHash = localDeviceId.hashCode()
                    val now = System.currentTimeMillis()
                    for (i in 0 until echoCount) {
                        if (tBuf.remaining() < 12) break
                        val targetHash = tBuf.int
                        val echoTs = tBuf.long
                        if (targetHash == myHash) {
                            val rtt = (now - echoTs).toInt()
                            if (rtt in 1..5000) {
                                measuredRttMs = rtt
                            }
                        }
                    }
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

        val lossPercent = replayWindow.getPacketLossPercentage()

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
            relativeBearingDegrees = calculatedRelBearing ?: existing?.relativeBearingDegrees,
            rttMs = measuredRttMs ?: existing?.rttMs,
            packetLossPercent = lossPercent,
            hopCount = calculatedHopCount
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
        replayWindows.clear()
        packetRelayFilter.clear()
        lastReceivedTimestamps.clear()
        cachedSenderHeaderBytes = null
        derivedEncryptionKey = null
        cachedBroadcastTargets = emptyList()
    }
}
