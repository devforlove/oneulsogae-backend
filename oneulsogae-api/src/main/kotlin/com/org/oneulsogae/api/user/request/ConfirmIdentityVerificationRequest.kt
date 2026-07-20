package com.org.oneulsogae.api.user.request

import com.org.oneulsogae.core.user.command.application.port.`in`.command.ConfirmIdentityVerificationCommand
import jakarta.validation.constraints.NotBlank

data class ConfirmIdentityVerificationRequest(
	@field:NotBlank(message = "regCertKey는 필수입니다.")
	val regCertKey: String,

	@field:NotBlank(message = "ordrIdxx는 필수입니다.")
	val ordrIdxx: String,
) {
	fun toCommand(): ConfirmIdentityVerificationCommand =
		ConfirmIdentityVerificationCommand(regCertKey = regCertKey, ordrIdxx = ordrIdxx)
}
