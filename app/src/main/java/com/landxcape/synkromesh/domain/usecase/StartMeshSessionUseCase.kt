package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
import javax.inject.Inject

class StartMeshSessionUseCase @Inject constructor(
    private val meshNetworkRepository: MeshNetworkRepository
) {
    suspend operator fun invoke(callsign: String, channel: Int = 1): Result<Unit> {
        return meshNetworkRepository.startMeshSession(callsign, channel)
    }
}
