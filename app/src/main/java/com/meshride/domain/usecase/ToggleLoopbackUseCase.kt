package com.meshride.domain.usecase

import com.meshride.domain.repository.AudioEngineRepository
import javax.inject.Inject

class ToggleLoopbackUseCase @Inject constructor(
    private val audioEngineRepository: AudioEngineRepository
) {
    suspend operator fun invoke(): Result<Boolean> {
        return audioEngineRepository.toggleLoopbackMode()
    }
}
