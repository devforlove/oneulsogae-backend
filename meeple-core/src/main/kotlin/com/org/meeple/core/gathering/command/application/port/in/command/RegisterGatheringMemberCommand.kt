package com.org.meeple.core.gathering.command.application.port.`in`.command

import com.org.meeple.common.user.Gender

/** 참가 접수 명령. [gender]는 호출자(payments)가 본인 프로필에서 확정해 넘긴다. */
data class RegisterGatheringMemberCommand(
	val gatheringId: Long,
	val scheduleId: Long,
	val userId: Long,
	val gender: Gender,
)
