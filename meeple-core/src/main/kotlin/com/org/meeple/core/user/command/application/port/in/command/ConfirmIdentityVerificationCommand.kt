package com.org.meeple.core.user.command.application.port.`in`.command

data class ConfirmIdentityVerificationCommand(
	val regCertKey: String,
	val ordrIdxx: String,
)
