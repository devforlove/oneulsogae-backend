package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.IdentityVerification

interface GetIdentityVerificationPort {
	fun findLatestByUserId(userId: Long): IdentityVerification?
}
