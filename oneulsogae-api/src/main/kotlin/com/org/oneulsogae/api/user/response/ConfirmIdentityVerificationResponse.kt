package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.user.command.application.port.`in`.result.ConfirmIdentityVerificationResult

data class ConfirmIdentityVerificationResponse(
	val name: String,
	val adult: Boolean,
	val gender: Gender,
) {
	companion object {
		fun of(result: ConfirmIdentityVerificationResult): ConfirmIdentityVerificationResponse =
			ConfirmIdentityVerificationResponse(name = result.name, adult = result.adult, gender = result.gender)
	}
}
