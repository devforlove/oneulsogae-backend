package com.org.meeple.core.user.command.application.port.out

import com.org.meeple.core.user.command.domain.IdentityVerification

interface SaveIdentityVerificationPort {
	fun save(verification: IdentityVerification): IdentityVerification
}
