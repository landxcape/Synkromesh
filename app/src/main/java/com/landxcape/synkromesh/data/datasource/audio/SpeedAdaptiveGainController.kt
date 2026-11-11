package com.landxcape.synkromesh.data.datasource.audio

import com.landxcape.synkromesh.data.datasource.local.PreferencesDataSource
import com.landxcape.synkromesh.data.datasource.mesh.LocationTelemetryDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic speed-adaptive acoustic volume boost controller that automatically
 * scales helmet audio output (+0 dB to +6 dB) to counteract aerodynamic wind
 * buffeting and high-speed engine noise.
 */
@Singleton
class SpeedAdaptiveGainController @Inject constructor(
    private val locationDataSource: LocationTelemetryDataSource,
    private val preferencesDataSource: PreferencesDataSource
) {
    companion object {
        private const val ALPHA = 0.15f // EMA smoothing factor to suppress GPS jitter

        /**
         * Dynamic velocity gain curve:
         * - 0..20 km/h: 1.0x (0 dB, standstill / low speed)
         * - 20..45 km/h: 1.0x .. 1.4x (+3 dB, moderate speed)
         * - 45..70+ km/h: 1.4x .. 2.0x (+6 dB ceiling, open highway wind buffeting)
         */
        fun calculateSpeedGain(speedKmh: Float): Float {
            return when {
                speedKmh <= 20f -> 1.0f
                speedKmh <= 45f -> {
                    val fraction = (speedKmh - 20f) / (45f - 20f)
                    1.0f + (fraction * 0.4f) // Up to 1.4x (+3 dB)
                }
                speedKmh <= 70f -> {
                    val fraction = (speedKmh - 45f) / (70f - 45f)
                    1.4f + (fraction * 0.6f) // Up to 2.0x (+6 dB)
                }
                else -> 2.0f // Max ceiling: +6 dB
            }
        }

        @Deprecated("Use calculateSpeedGain", ReplaceWith("calculateSpeedGain(speedKmh)"))
        fun calculateNepaliGain(speedKmh: Float): Float = calculateSpeedGain(speedKmh)
    }

    private val controllerScope = CoroutineScope(Dispatchers.Default)
    private var trackingJob: Job? = null

    private var smoothedSpeedKmh = 0f

    private val _gainFlow = MutableStateFlow(1.0f)
    val gainFlow: StateFlow<Float> = _gainFlow.asStateFlow()

    @Volatile
    var currentGain: Float = 1.0f
        private set

    fun start() {
        if (trackingJob != null) return
        trackingJob = controllerScope.launch {
            combine(
                locationDataSource.currentLocation,
                preferencesDataSource.speedGainEnabledFlow
            ) { loc, isEnabled ->
                Pair(loc, isEnabled)
            }.collect { (loc, isEnabled) ->
                if (!isActive) return@collect
                if (!isEnabled) {
                    currentGain = 1.0f
                    _gainFlow.value = 1.0f
                    return@collect
                }

                val rawSpeed = loc?.speedKmh ?: 0f
                // Apply Exponential Moving Average (EMA)
                smoothedSpeedKmh = if (smoothedSpeedKmh == 0f) {
                    rawSpeed
                } else {
                    (ALPHA * rawSpeed) + ((1f - ALPHA) * smoothedSpeedKmh)
                }

                val calculatedGain = calculateSpeedGain(smoothedSpeedKmh)
                currentGain = calculatedGain
                _gainFlow.value = calculatedGain
            }
        }
    }

    fun stop() {
        trackingJob?.cancel()
        trackingJob = null
        smoothedSpeedKmh = 0f
        currentGain = 1.0f
        _gainFlow.value = 1.0f
    }
}
