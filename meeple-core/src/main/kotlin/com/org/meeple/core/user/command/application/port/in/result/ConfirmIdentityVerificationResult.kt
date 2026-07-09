package com.org.meeple.core.user.command.application.port.`in`.result

/** CI/DI 등 민감정보는 절대 포함하지 않는다. */
data class ConfirmIdentityVerificationResult(
	val name: String,
	val adult: Boolean,
)
