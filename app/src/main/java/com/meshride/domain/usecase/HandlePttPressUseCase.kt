package com.meshride.domain.usecase

import com.meshride.domain.repository.IntercomRepository
import javax.inject.Inject

class HandlePttPressUseCase @Inject constructor(
    private val intercomRepository: IntercomRepository
) {
    suspend operator fun invoke(isPressed: Boolean): Result<Unit> {
        return intercomRepository.handlePttPress(isPressed)
    }
}
