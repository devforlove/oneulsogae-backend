package com.org.meeple.core.alarm.query.dto

import com.org.meeple.common.user.Gender

/**
 * 알람을 유발한 상대 사용자([userId])의 표시용 프로필 읽기 모델(read model).
 * 알람 목록 응답에서 발신 유저를 한 번에 식별·노출하기 위한 최소 정보(프로필 이미지·성별)를 담는다.
 * 프로필 상세는 user 도메인이 소유하므로, infra 읽기 dao가 user_details를 조인해 이 모델로 투영한다.
 */
data class AlarmFrom(
	val userId: Long,
	val profileImageCode: String?,
	val gender: Gender?,
)
