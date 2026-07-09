package com.org.meeple.core.user.command.application.port.`in`.result

data class RegisterIdentityVerificationResult(
	val callUrl: String,
	val regCertKey: String,
	val ordrIdxx: String,
)
