package com.org.meeple.api.alarm.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.alarm.query.dto.AlarmFrom

/**
 * 알람을 보낸 발신 유저의 표시용 프로필 응답. ([AlarmResponse.froms]의 한 원소)
 */
data class AlarmFromResponse(
	val userId: Long,
	val profileImageCode: String?,
	val gender: Gender?,
) {
	companion object {
		fun of(from: AlarmFrom): AlarmFromResponse =
			AlarmFromResponse(
				userId = from.userId,
				profileImageCode = from.profileImageCode,
				gender = from.gender,
			)
	}
}
