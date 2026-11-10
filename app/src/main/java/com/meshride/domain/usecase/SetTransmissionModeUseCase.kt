package com.meshride.domain.usecase

import com.meshride.domain.model.IntercomMode
import com.meshride.domain.repository.IntercomRepository
import javax.inject.Inject

class SetTransmissionModeUseCase @Inject constructor(
    private val intercomRepository: IntercomRepository
) {
    suspend operator fun invoke(mode: IntercomMode): Result<Unit> {
        return intercomRepository.setIntercomMode(mode)
    }
}
