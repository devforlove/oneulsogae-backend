package com.org.meeple.admin.memberverification.command.application.port.out

import java.time.LocalDate

/**
 * 멤버 인증 승인 시 gathering_profile 스냅샷에 담을 유저 프로필 소스. (user_details에서 가져온다)
 * 나이는 [birthday]로부터 조회 시점에 계산한다.
 */
data class VerifiedUserProfile(
	val birthday: LocalDate?,
	val height: Int?,
	val profileImageCode: String?,
)
