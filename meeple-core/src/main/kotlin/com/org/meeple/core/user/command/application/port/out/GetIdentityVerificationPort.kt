package com.org.meeple.core.user.command.application.port.out

import com.org.meeple.core.user.command.domain.IdentityVerification

interface GetIdentityVerificationPort {
	fun findLatestByUserId(userId: Long): IdentityVerification?
}
