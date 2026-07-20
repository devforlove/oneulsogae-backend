package com.org.oneulsogae.core.user.command.application.port.`in`

import com.org.oneulsogae.core.user.command.application.port.`in`.result.RegisterIdentityVerificationResult

interface RegisterIdentityVerificationUseCase {
	fun register(userId: Long): RegisterIdentityVerificationResult
}
