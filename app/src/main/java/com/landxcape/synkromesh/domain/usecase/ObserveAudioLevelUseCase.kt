package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.model.AudioLevel
import com.landxcape.synkromesh.domain.repository.AudioEngineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAudioLevelUseCase @Inject constructor(
    private val audioEngineRepository: AudioEngineRepository
) {
    operator fun invoke(): Flow<AudioLevel> = audioEngineRepository.observeMicAudioLevel()
}
