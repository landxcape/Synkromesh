package com.meshride.domain.usecase

import com.meshride.domain.repository.IntercomRepository
import javax.inject.Inject

class ToggleMicrophoneMuteUseCase @Inject constructor(
    private val intercomRepository: IntercomRepository
) {
    suspend operator fun invoke(): Result<Boolean> {
        return intercomRepository.toggleMicrophoneMute()
    }
}
