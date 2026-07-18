package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDate

/**
 * 모임 일정에 종속된 참가자 한 명(read model). 상세 조회에서 일정([GatheringScheduleView])별 로스터로 노출된다.
 * 승인대기(PENDING)·참가(JOINED) 상태만 담고, 거절·취소는 제외한다(dao where 조건).
 * 프로필(직종·직장상세·생일·키)은 gathering_profile left join으로 채운다(멤버 인증 승인으로 채워진 값).
 * 표시 규칙(PENDING 프로필 비노출, 생일→나이 파생)은 응답 레이어가 적용하므로 여기선 조인 원본을 그대로 담는다.
 * [scheduleId]는 서비스가 일정별로 묶기 위한 키다(응답 노출 대상 아님).
 */
data class GatheringParticipantView(
	val scheduleId: Long,
	val userId: Long,
	val status: GatheringMemberStatus,
	val gender: Gender,
	val jobCategory: String?,
	val jobDetail: String?,
	val birthday: LocalDate?,
	val height: Int?,
)
