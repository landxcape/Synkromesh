package com.landxcape.synkromesh.domain.usecase

import com.landxcape.synkromesh.domain.model.IntercomMode
import com.landxcape.synkromesh.domain.repository.IntercomRepository
import javax.inject.Inject

class SetTransmissionModeUseCase @Inject constructor(
    private val intercomRepository: IntercomRepository
) {
    suspend operator fun invoke(mode: IntercomMode): Result<Unit> {
        return intercomRepository.setIntercomMode(mode)
    }
}
