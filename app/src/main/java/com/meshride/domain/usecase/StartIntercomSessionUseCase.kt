package com.meshride.domain.usecase

import com.meshride.domain.repository.IntercomRepository
import javax.inject.Inject

class StartIntercomSessionUseCase @Inject constructor(
    private val intercomRepository: IntercomRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return intercomRepository.startIntercomSession()
    }
}
