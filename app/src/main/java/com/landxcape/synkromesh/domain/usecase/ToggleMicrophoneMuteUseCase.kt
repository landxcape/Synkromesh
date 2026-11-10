package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.repository.IntercomRepository
import javax.inject.Inject

class ToggleMicrophoneMuteUseCase @Inject constructor(
    private val intercomRepository: IntercomRepository
) {
    suspend operator fun invoke(): Result<Boolean> {
        return intercomRepository.toggleMicrophoneMute()
    }
}
