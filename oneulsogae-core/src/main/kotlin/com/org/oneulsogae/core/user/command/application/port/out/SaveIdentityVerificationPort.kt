package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.IdentityVerification

interface SaveIdentityVerificationPort {
	fun save(verification: IdentityVerification): IdentityVerification
}
