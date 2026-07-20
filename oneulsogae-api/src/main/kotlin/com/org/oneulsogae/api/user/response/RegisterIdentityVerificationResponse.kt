package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.core.user.command.application.port.`in`.result.RegisterIdentityVerificationResult

data class RegisterIdentityVerificationResponse(
	val callUrl: String,
	val regCertKey: String,
	val ordrIdxx: String,
) {
	companion object {
		fun of(result: RegisterIdentityVerificationResult): RegisterIdentityVerificationResponse =
			RegisterIdentityVerificationResponse(
				callUrl = result.callUrl,
				regCertKey = result.regCertKey,
				ordrIdxx = result.ordrIdxx,
			)
	}
}
