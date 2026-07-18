package com.org.meeple.api.admin.response

import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberDetailView
import com.org.meeple.core.common.time.ageAt
import java.time.LocalDate

/**
 * 어드민 참가 신청 상세 응답. 유저의 모임 프로필(gathering_profile)에서 조회한다.
 * 나이는 생일로부터 조회 시점([today]) 기준으로 계산한다. 멤버 인증 미승인(gathering_profile 없음)이면 모든 필드가 null이다.
 */
data class AdminGatheringMemberProfileResponse(
	val jobCategory: String?,
	val jobDetail: String?,
	val age: Int?,
	val height: Int?,
	val profileImageCode: String?,
) {
	companion object {
		fun of(view: AdminGatheringMemberDetailView, today: LocalDate): AdminGatheringMemberProfileResponse =
			AdminGatheringMemberProfileResponse(
				jobCategory = view.jobCategory,
				jobDetail = view.jobDetail,
				age = view.birthday?.ageAt(today),
				height = view.height,
				profileImageCode = view.profileImageCode,
			)
	}
}
