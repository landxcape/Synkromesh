package com.meshride.domain.usecase

import com.meshride.domain.model.AudioLevel
import com.meshride.domain.repository.AudioEngineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAudioLevelUseCase @Inject constructor(
    private val audioEngineRepository: AudioEngineRepository
) {
    operator fun invoke(): Flow<AudioLevel> = audioEngineRepository.observeMicAudioLevel()
}
