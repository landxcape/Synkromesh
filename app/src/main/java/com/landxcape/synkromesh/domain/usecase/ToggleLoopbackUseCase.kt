package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.repository.AudioEngineRepository
import javax.inject.Inject

class ToggleLoopbackUseCase @Inject constructor(
    private val audioEngineRepository: AudioEngineRepository
) {
    suspend operator fun invoke(): Result<Boolean> {
        return audioEngineRepository.toggleLoopbackMode()
    }
}
