package com.org.meeple.admin.gathering.command.domain

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender

/**
 * 어드민이 다루는 모임 일정 참가 신청(도메인 모델). 승인/거절 전이 가능 여부를 판정한다.
 * 승인대기(PENDING) 상태만 승인(JOINED)·거절(REJECTED)할 수 있다.
 * [gender]·[earlyBirdApplied]는 거절 시 일정 여분 복원에 쓴다.
 */
class AdminGatheringMember(
	val id: Long,
	val scheduleId: Long,
	val gender: Gender,
	val status: GatheringMemberStatus,
	val earlyBirdApplied: Boolean,
) {

	/** 승인 가능 여부를 검증한다. 승인대기 상태가 아니면 전이 불가. */
	fun validateApprovable() {
		validatePending()
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
