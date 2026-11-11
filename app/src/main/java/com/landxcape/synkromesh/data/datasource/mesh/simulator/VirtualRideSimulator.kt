package com.landxcape.synkromesh.data.datasource.mesh.simulator

import com.landxcape.synkromesh.domain.model.Rider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin

/**
 * Multi-node virtual motorcycle convoy simulator for single-device verification
 * of OpenStreetMap convoy plotting, tactical radar vector links, multi-hop relaying,
 * and Nepali-calibrated speed-adaptive volume boost.
 */
@Singleton
class VirtualRideSimulator @Inject constructor() {

    private val simScope = CoroutineScope(Dispatchers.Default)
    private var simJob: Job? = null

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating: StateFlow<Boolean> = _isSimulating.asStateFlow()

    private val _simulatedRiders = MutableStateFlow<List<Rider>>(emptyList())
    val simulatedRiders: StateFlow<List<Rider>> = _simulatedRiders.asStateFlow()

    private var step = 0

    fun toggleSimulation() {
        if (_isSimulating.value) {
            stopSimulation()
        } else {
            startSimulation()
        }
    }

    fun startSimulation() {
        if (_isSimulating.value) return
        _isSimulating.value = true
        simJob = simScope.launch {
            // Base coordinate (Kathmandu / Prithvi Highway corridor)
            var baseLat = 27.7172
            var baseLon = 85.3240
            var baseAlt = 1400.0

            while (isActive && _isSimulating.value) {
                step++

                // Dynamic speed oscillation: 25 km/h to 65 km/h
                val speedKmh = 25f + ((sin(step * 0.2) + 1.0) * 20.0).toFloat()

                // Advance base GPS coordinates forward along 45 degree heading
                baseLat += 0.00015 * cos(Math.toRadians(45.0))
                baseLon += 0.00015 * sin(Math.toRadians(45.0))
                baseAlt += (sin(step * 0.1) * 2.0)

                // Apex Lead (45m ahead, 1-hop direct)
                val apexLat = baseLat + 0.00040
                val apexLon = baseLon + 0.00035
                val isApexTalking = (step % 8 == 0 || step % 8 == 1)

                val apexLead = Rider(
                    id = "sim_apex_lead",
                    callsign = "Apex Lead",
                    isLocalUser = false,
                    isTransmitting = isApexTalking,
                    signalStrengthDbm = -58,
                    latencyMs = 18L,
                    estimatedDistanceMeters = 48f,
                    relativePosition = "Ahead • 12°",
                    relativeBearingDegrees = 12f,
                    altitudeMeters = baseAlt + 12.0,
                    speedKmh = speedKmh + 4f,
                    batteryPercent = 84,
                    packetLossPercent = 0.5f,
                    latitude = apexLat,
                    longitude = apexLon,
                    hopCount = 1
                )

                // Mid Relay (110m ahead, 1-hop direct bridge)
                val midLat = baseLat + 0.00095
                val midLon = baseLon + 0.00085
                val midRelay = Rider(
                    id = "sim_mid_relay",
                    callsign = "Mid Relay",
                    isLocalUser = false,
                    isTransmitting = false,
                    signalStrengthDbm = -72,
                    latencyMs = 28L,
                    estimatedDistanceMeters = 112f,
                    relativePosition = "Ahead • 8°",
                    relativeBearingDegrees = 8f,
                    altitudeMeters = baseAlt + 6.0,
                    speedKmh = speedKmh + 1f,
                    batteryPercent = 71,
                    packetLossPercent = 1.2f,
                    latitude = midLat,
                    longitude = midLon,
                    hopCount = 1
                )

                // Sweeper Tail (320m behind, 2-hop relayed via Mid Relay!)
                val sweeperLat = baseLat - 0.00280
                val sweeperLon = baseLon - 0.00240
                val sweeperTail = Rider(
                    id = "sim_sweeper_tail",
                    callsign = "Sweeper Tail",
                    isLocalUser = false,
                    isTransmitting = false,
                    signalStrengthDbm = -88,
                    latencyMs = 45L,
                    estimatedDistanceMeters = 345f,
                    relativePosition = "Behind • 185°",
                    relativeBearingDegrees = 185f,
                    altitudeMeters = baseAlt - 15.0,
                    speedKmh = speedKmh - 3f,
                    batteryPercent = 38,
                    packetLossPercent = 3.5f,
                    latitude = sweeperLat,
                    longitude = sweeperLon,
                    hopCount = 2 // 2-Hop Convoy Relayed!
                )

                _simulatedRiders.value = listOf(apexLead, midRelay, sweeperTail)
                delay(1500L)
            }
        }
    }

    fun stopSimulation() {
        _isSimulating.value = false
        simJob?.cancel()
        simJob = null
        _simulatedRiders.value = emptyList()
        step = 0
    }
}
