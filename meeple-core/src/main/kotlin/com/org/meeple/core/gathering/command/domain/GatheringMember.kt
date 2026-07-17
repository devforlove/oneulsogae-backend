package com.org.meeple.core.gathering.command.domain

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.GatheringErrorCode

/**
 * 모임 일정 참가자(command 도메인 모델). 결제완료 접수 시 승인대기(PENDING)로 생성되고,
 * 운영자 승인/거절은 admin 모듈이 담당한다. 거절/취소된 행은 재접수 시 [revive]로 되살린다
 * ((schedule_id, user_id) 유니크 제약 때문에 새 행을 만들지 않는다).
 */
class GatheringMember(
	val id: Long? = null,
	val gatheringId: Long,
	val scheduleId: Long,
	val userId: Long,
	var gender: Gender,
	var status: GatheringMemberStatus,
	var earlyBirdApplied: Boolean,
) {

	/** 재접수 가능 여부를 검증한다. 승인대기·참가 상태면 중복 신청이다. */
	fun validateReRegistrable() {
		if (status == GatheringMemberStatus.PENDING || status == GatheringMemberStatus.JOINED) {
			throw BusinessException(GatheringErrorCode.GATHERING_ALREADY_JOINED)
		}
	}

	/** 거절/취소된 행을 승인대기로 되살린다. 이번 접수의 성별·얼리버드 적용 여부로 갱신한다. */
	fun revive(gender: Gender, earlyBirdApplied: Boolean) {
		validateReRegistrable()
		this.status = GatheringMemberStatus.PENDING
		this.gender = gender
		this.earlyBirdApplied = earlyBirdApplied
	}

	/** PG 승인 실패로 방금 접수를 취소한다(→ 참가취소). 차감 여분 복원은 서비스가 일정 도메인으로 처리한다. */
	fun cancel() {
		this.status = GatheringMemberStatus.CANCELED
	}

	companion object {

		/** 승인대기 상태의 신규 참가자를 생성한다. */
		fun pending(gatheringId: Long, scheduleId: Long, userId: Long, gender: Gender, earlyBirdApplied: Boolean): GatheringMember =
			GatheringMember(
				gatheringId = gatheringId,
				scheduleId = scheduleId,
				userId = userId,
				gender = gender,
				status = GatheringMemberStatus.PENDING,
				earlyBirdApplied = earlyBirdApplied,
			)
	}
}
