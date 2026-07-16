package com.org.meeple.api.admin.response

import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberView
import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/** 어드민 일정별 참가 신청 목록 응답의 한 행. */
data class AdminGatheringMemberResponse(
	val memberId: Long,
	val userId: Long,
	val nickname: String?,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val amount: Int?,
	val appliedAt: LocalDateTime,
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
			)
	}
}
