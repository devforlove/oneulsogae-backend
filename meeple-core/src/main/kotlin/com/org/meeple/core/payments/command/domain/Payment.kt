package com.org.meeple.core.payments.command.domain

import com.org.meeple.common.user.Gender

/**
 * 결제 기록(command 도메인 모델). 무검증 접수 단계라 결제수단·PG 검증 정보 없이
 * 누가(userId)·무엇을(gathering/schedule/gender)·얼마에(amount, 서버 확정가) 접수했는지만 남긴다.
 * 참가 상태의 원장은 gathering_members.status 하나로 유지한다(결제 상태 컬럼 없음).
 */
class Payment(
	val id: Long? = null,
	val userId: Long,
	val gatheringId: Long,
	val scheduleId: Long,
	val gender: Gender,
	val amount: Int,
)
