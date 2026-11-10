package com.landxcape.synkromesh.domain.model

data class AudioLevel(
    val rmsDb: Float = -96f,
    val normalizedLevel: Float = 0.0f // 0.0 (silent) to 1.0 (loud speech)
)
