package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.model.MeshPeer
import com.landxcape.synkromesh.domain.repository.MeshNetworkRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePeersUseCase @Inject constructor(
    private val meshNetworkRepository: MeshNetworkRepository
) {
    operator fun invoke(): Flow<List<MeshPeer>> = meshNetworkRepository.observePeers()
}
