package com.org.oneulsogae.core.user.command.application.port.`in`.result

import com.org.oneulsogae.common.user.Gender

/** CI/DI 등 민감정보는 절대 포함하지 않는다. */
data class ConfirmIdentityVerificationResult(
	val name: String,
	val adult: Boolean,
	val gender: Gender,
)
