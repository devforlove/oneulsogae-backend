package com.org.meeple.api.user.response

import com.org.meeple.core.user.command.application.port.`in`.result.ConfirmIdentityVerificationResult

data class ConfirmIdentityVerificationResponse(
	val name: String,
	val adult: Boolean,
) {
	companion object {
		fun of(result: ConfirmIdentityVerificationResult): ConfirmIdentityVerificationResponse =
			ConfirmIdentityVerificationResponse(name = result.name, adult = result.adult)
	}
}
