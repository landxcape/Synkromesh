package com.meshride.domain.usecase

import com.meshride.domain.model.MeshPeer
import com.meshride.domain.repository.MeshNetworkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePeersUseCase @Inject constructor(
    private val meshNetworkRepository: MeshNetworkRepository
) {
    operator fun invoke(): Flow<List<MeshPeer>> = meshNetworkRepository.observePeers()
}
