package com.org.meeple.core.payments.command.application.port.`in`.command

/** 결제완료 접수 명령. 성별은 받지 않는다 — 본인 프로필 성별을 서버가 강제한다. */
data class CompletePaymentCommand(
	val gatheringId: Long,
	val scheduleId: Long,
)
