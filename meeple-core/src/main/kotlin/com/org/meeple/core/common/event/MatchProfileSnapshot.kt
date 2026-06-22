package com.org.meeple.core.common.event

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭에 필요한 사용자 기준 필드만 담은 스냅샷. user 도메인이 자기 데이터(User+UserDetail)로 채워 이벤트에 실어 보내고,
 * match 도메인은 이 값을 그대로 자기 읽기 모델(match_user)에 upsert한다. (이벤트가 상태를 실어 나르므로 수신측이 user로 콜백하지 않는다)
 * 모든 필드가 채워졌다는 것 자체가 "매칭 가능"을 의미한다. (스냅샷이 만들어지지 않으면 매칭 불가 → 행 삭제)
 */
data class MatchProfileSnapshot(
	val gender: Gender,
	val birthday: LocalDate,
	val regionId: Long,
	val regionCode: Int,
	val maritalStatus: MaritalStatus,
	val nickname: String,
	val profileImageCode: String,
	val lastLoginAt: LocalDateTime,
)
