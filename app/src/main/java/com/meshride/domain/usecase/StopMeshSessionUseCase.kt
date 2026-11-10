package com.meshride.domain.usecase

import com.meshride.domain.repository.MeshNetworkRepository
import javax.inject.Inject

class StopMeshSessionUseCase @Inject constructor(
    private val meshNetworkRepository: MeshNetworkRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return meshNetworkRepository.stopMeshSession()
    }
}
