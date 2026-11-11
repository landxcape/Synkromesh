package com.landxcape.synkromesh.domain.model

enum class HelmetAcousticProfile(
    val displayName: String,
    val description: String,
    val vadThreshold: Float,
    val opusBitrateKbps: Int,
    val isAggressiveNs: Boolean
) {
    FULL_FACE_QUIET(
        displayName = "Full-Face (Quiet)",
        description = "Aerodynamic full-face helmet with chin curtain. Balanced VAD and standard 24 kbps fullband voice.",
        vadThreshold = 0.03f,
        opusBitrateKbps = 24,
        isAggressiveNs = false
    ),
    MODULAR_URBAN(
        displayName = "Modular / Urban",
        description = "Standard modular or dual-sport helmet. Elevated noise suppression for city & traffic ambiance.",
        vadThreshold = 0.05f,
        opusBitrateKbps = 24,
        isAggressiveNs = true
    ),
    OPEN_FACE_CRUISER(
        displayName = "Open-Face / High Wind",
        description = "3/4 open-face or high-wind buffeting. Aggressive noise gate and 32 kbps bitrate to preserve speech harmonics.",
        vadThreshold = 0.08f,
        opusBitrateKbps = 32,
        isAggressiveNs = true
    )
}
