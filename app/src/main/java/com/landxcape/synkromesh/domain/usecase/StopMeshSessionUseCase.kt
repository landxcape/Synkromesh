package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
import javax.inject.Inject

class StopMeshSessionUseCase @Inject constructor(
    private val meshNetworkRepository: MeshNetworkRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return meshNetworkRepository.stopMeshSession()
    }
}
