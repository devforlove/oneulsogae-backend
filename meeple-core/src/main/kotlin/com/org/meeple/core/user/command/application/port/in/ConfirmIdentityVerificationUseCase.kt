package com.org.meeple.core.user.command.application.port.`in`

import com.org.meeple.core.user.command.application.port.`in`.command.ConfirmIdentityVerificationCommand
import com.org.meeple.core.user.command.application.port.`in`.result.ConfirmIdentityVerificationResult

interface ConfirmIdentityVerificationUseCase {
	fun confirm(userId: Long, command: ConfirmIdentityVerificationCommand): ConfirmIdentityVerificationResult
}
