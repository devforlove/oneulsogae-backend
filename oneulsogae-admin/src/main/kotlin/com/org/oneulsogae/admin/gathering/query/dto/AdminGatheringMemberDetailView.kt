package com.org.oneulsogae.admin.gathering.query.dto

import java.time.LocalDate

/**
 * 어드민 참가 신청 상세(유저 모임 프로필) read model. gathering_profile에서 조회한다.
 * 나이는 [birthday]로부터 조회 시점에 계산한다. gathering_profile이 없으면(멤버 인증 미승인) 모든 필드가 null이다.
 */
data class AdminGatheringMemberDetailView(
	val jobCategory: String?,
	val jobDetail: String?,
	val birthday: LocalDate?,
	val height: Int?,
	val profileImageCode: String?,
)
