package com.org.meeple.api.admin.response

import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberView
import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 어드민 참가 신청 목록 응답의 한 행.
 * [scheduleId]·[gatheringTitle]·[scheduleStartAt]은 전역 목록에서 어느 모임·일정의 신청인지 알기 위한 맥락이며,
 * 승인/거절·상세 호출에 필요한 scheduleId를 포함한다.
 */
data class AdminGatheringMemberResponse(
	val memberId: Long,
	val userId: Long,
	val nickname: String?,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val amount: Int?,
	val appliedAt: LocalDateTime,
	val scheduleId: Long,
	val gatheringTitle: String,
	val scheduleStartAt: LocalDateTime,
	/** 회원 인증(gathering_profile) 완료 여부. true면 승인 가능. */
	val memberVerified: Boolean,
) {

	companion object {

		fun of(view: AdminGatheringMemberView): AdminGatheringMemberResponse =
			AdminGatheringMemberResponse(
				memberId = view.memberId,
				userId = view.userId,
				nickname = view.nickname,
				gender = view.gender,
				status = view.status,
				amount = view.amount,
				appliedAt = view.appliedAt,
				scheduleId = view.scheduleId,
				gatheringTitle = view.gatheringTitle,
				scheduleStartAt = view.scheduleStartAt,
				memberVerified = view.memberVerified,
			)
	}
}
