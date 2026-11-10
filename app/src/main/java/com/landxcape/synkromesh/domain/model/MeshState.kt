package com.landxcape.synkromesh.domain.model

enum class MeshState(val statusLabel: String) {
    DISCONNECTED("OFFLINE"),
    SEARCHING("SEARCHING MESH"),
    CONNECTED("MESH ACTIVE")
}
