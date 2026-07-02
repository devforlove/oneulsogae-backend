package com.org.meeple.core.solomatch.command.domain

import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 매칭(소개)에 참가한 사용자 한 명의 참가 정보 도메인 모델.
 * 참가자를 (matchId, userId) 한 쌍의 행으로 정규화해, 1:1뿐 아니라 N:N(2:2·3:3) 미팅으로 확장한다.
 * [status]가 참가자 상태(WAITING→APPLY→ACTIVE/DEACTIVE)를 담는다. 관심 신청 여부는 [hasApplied]로 본다.
 * [deletedAt]이 채워지면 소프트 삭제된(제거된) 참가자다.
 * 영속성은 [com.org.meeple.infra.solomatch.command.entity.SoloMatchMemberEntity]가 담당한다.
 */
data class MatchMember(
	val id: Long = 0,
	val matchId: Long,
	val userId: Long,
	val gender: Gender,
	val status: MatchMemberStatus = MatchMemberStatus.WAITING,
	/** 이 참가자가 매칭을 확인한 시각. 미확인이면 null. (전이 로직엔 쓰지 않고, 애그리거트 저장 시 값이 유실되지 않도록 보존만 한다) */
	val checkedAt: LocalDateTime? = null,
	val deletedAt: LocalDateTime? = null,
) {

	/** 이 참가자가 관심을 신청했는지 여부. (APPLY 또는 ACTIVE) */
	val hasApplied: Boolean
		get() = status == MatchMemberStatus.APPLY || status == MatchMemberStatus.ACTIVE

	/** 이 참가자가 환불 대상인지 여부. (신청(APPLY)했으나 아직 성사되지 않은 상태 — 성사로 ACTIVE가 된 참가자는 환불 대상이 아니다) */
	val isRefundable: Boolean
		get() = status == MatchMemberStatus.APPLY

	/** 이 참가자가 비활성(DEACTIVE) 상태인지 여부. (매칭을 나간 참가자) */
	val isDeactivated: Boolean
		get() = status == MatchMemberStatus.DEACTIVE

	/** 이 참가자가 관심을 신청한(APPLY) 새 모델을 반환한다. */
	fun apply(): MatchMember =
		copy(status = MatchMemberStatus.APPLY)

	/** 이 참가자를 활성(ACTIVE)으로 승격한 새 모델을 반환한다. (매치 성사 시) */
	fun activate(): MatchMember =
		copy(status = MatchMemberStatus.ACTIVE)

	/** 이 참가자를 비활성(DEACTIVE)으로 전이한 새 모델을 반환한다. (채팅방 나가기) */
	fun deactivate(): MatchMember =
		copy(status = MatchMemberStatus.DEACTIVE)

	/**
	 * 이 참가자를 [now]에 비활성(DEACTIVE) 전이 + 소프트 삭제(제거)한 새 모델을 반환한다.
	 * 채팅방 나가기로 매칭이 제거될 때 호출한다. 저장하면 status가 DEACTIVE가 되고 deletedAt이 채워져 조회에서 제외된다.
	 */
	fun delete(now: LocalDateTime): MatchMember =
		copy(status = MatchMemberStatus.DEACTIVE, deletedAt = now)
}
