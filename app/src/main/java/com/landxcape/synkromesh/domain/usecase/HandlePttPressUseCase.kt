package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.repository.IntercomRepository
import javax.inject.Inject

class HandlePttPressUseCase @Inject constructor(
    private val intercomRepository: IntercomRepository
) {
    suspend operator fun invoke(isPressed: Boolean): Result<Unit> {
        return intercomRepository.handlePttPress(isPressed)
    }
}
