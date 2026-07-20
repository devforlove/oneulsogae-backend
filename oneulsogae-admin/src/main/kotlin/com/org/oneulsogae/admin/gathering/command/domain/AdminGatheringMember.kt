package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender

/**
 * 어드민이 다루는 모임 일정 참가 신청(도메인 모델). 승인/거절 전이 가능 여부를 판정한다.
 * 승인대기(PENDING) 상태만 승인(JOINED)·거절(REJECTED)할 수 있고, 승인은 회원 인증(gathering_profile)을 마친 유저만 가능하다.
 * [gender]·[earlyBirdApplied]는 거절 시 일정 여분 복원에 쓴다.
 */
class AdminGatheringMember(
	val id: Long,
	val userId: Long,
	val scheduleId: Long,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val earlyBirdApplied: Boolean,
) {

	/** 승인 가능 여부를 검증한다. 승인대기 상태여야 하고, 회원 인증([memberVerified])을 마친 유저여야 한다. */
	fun validateApprovable(memberVerified: Boolean) {
		validatePending()
		if (!memberVerified) {
			throw AdminException(
				AdminErrorCode.GATHERING_MEMBER_NOT_VERIFIED,
				"회원 인증되지 않은 유저는 승인할 수 없습니다: userId=$userId",
			)
		}
	}

	/** 거절 가능 여부를 검증한다. 승인대기 상태가 아니면 전이 불가. */
	fun validateRejectable() {
		validatePending()
	}

	private fun validatePending() {
		if (status != GatheringMemberStatus.PENDING) {
			throw AdminException(AdminErrorCode.GATHERING_MEMBER_INVALID_STATUS_TRANSITION, "승인대기 상태가 아닙니다: $status")
		}
	}
}
