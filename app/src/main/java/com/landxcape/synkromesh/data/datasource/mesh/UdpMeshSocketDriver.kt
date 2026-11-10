package com.landxcape.synkromesh.data.datasource.mesh

import com.landxcape.synkromesh.domain.model.AudioPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UdpMeshSocketDriver @Inject constructor() {

    companion object {
        const val UDP_PORT = 50005
        const val MAX_DATAGRAM_SIZE = 1024
        private const val MAGIC_HEADER: Short = 0x4D52 // 'MR'
    }

    private val socketScope = CoroutineScope(Dispatchers.IO)
    private var socketJob: Job? = null
    private var datagramSocket: DatagramSocket? = null

    private val _incomingAudioFlow = MutableSharedFlow<AudioPacket>(extraBufferCapacity = 32)
    val incomingAudioFlow: SharedFlow<AudioPacket> = _incomingAudioFlow.asSharedFlow()

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
                    if (packet.length > 18) { // Minimum header length
                        parsePacket(receiveBuffer, packet.length)?.let { audioPacket ->
                            _incomingAudioFlow.tryEmit(audioPacket)
                        }
                    }
                } catch (e: Exception) {
                    if (!_isSocketActive.value) break
                    kotlinx.coroutines.delay(200)
                }
            }
        }
    }

    fun sendPacket(audioPacket: AudioPacket): Result<Unit> = runCatching {
        val socket = datagramSocket ?: return@runCatching
        val senderBytes = audioPacket.senderId.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(2 + 8 + 8 + 1 + senderBytes.size + audioPacket.payload.size).apply {
            order(ByteOrder.BIG_ENDIAN)
            putShort(MAGIC_HEADER)
            putLong(audioPacket.sequenceNumber)
            putLong(audioPacket.timestampMs)
            put(senderBytes.size.toByte())
            put(senderBytes)
            put(audioPacket.payload)
        }

        val data = buffer.array()
        val datagram = DatagramPacket(data, data.size, broadcastAddress, UDP_PORT)
        socket.send(datagram)
    }

    fun stopSocket(): Result<Unit> = runCatching {
        _isSocketActive.value = false
        socketJob?.cancel()
        socketJob = null
        datagramSocket?.close()
        datagramSocket = null
    }

    private fun parsePacket(data: ByteArray, length: Int): AudioPacket? {
        if (length < 19) return null
        val buffer = ByteBuffer.wrap(data, 0, length).apply {
            order(ByteOrder.BIG_ENDIAN)
        }

        val magic = buffer.short
        if (magic != MAGIC_HEADER) return null

        val sequenceNumber = buffer.long
        val timestamp = buffer.long
        val senderLen = buffer.get().toInt() and 0xFF
        if (buffer.remaining() < senderLen) return null

        val senderBytes = ByteArray(senderLen)
        buffer.get(senderBytes)
        val senderId = String(senderBytes, Charsets.UTF_8)

        val opusLen = buffer.remaining()
        if (opusLen <= 0) return null

        val opusPayload = ByteArray(opusLen)
        buffer.get(opusPayload)

        return AudioPacket(
            sequenceNumber = sequenceNumber,
            senderId = senderId,
            timestampMs = timestamp,
            payload = opusPayload
        )
    }
}
