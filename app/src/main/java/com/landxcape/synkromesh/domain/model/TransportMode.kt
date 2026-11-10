package com.landxcape.synkromesh.domain.model

enum class TransportMode {
    DUAL,         // Open Mesh: Nearby P2P + UDP broadcast
    HOTSPOT_UDP   // Lead/Join: UDP only over hotspot subnet, no Nearby
}
