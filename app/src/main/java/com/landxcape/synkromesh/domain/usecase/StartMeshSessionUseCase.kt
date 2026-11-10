package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
import javax.inject.Inject

class StartMeshSessionUseCase @Inject constructor(
    private val meshNetworkRepository: MeshNetworkRepository
) {
    suspend operator fun invoke(callsign: String): Result<Unit> {
        return meshNetworkRepository.startMeshSession(callsign)
    }
}
