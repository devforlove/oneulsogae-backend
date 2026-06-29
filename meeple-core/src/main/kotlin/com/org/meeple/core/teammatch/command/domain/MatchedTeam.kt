package com.org.meeple.core.teammatch.command.domain

import com.org.meeple.common.match.MatchedTeamStatus
import java.time.LocalDateTime

/**
 * 팀 매칭([TeamMatch])에 참가한 한 팀을 (teamMatchId, teamId) 한 쌍으로 나타내는 도메인 모델.
 * 매치별 상태([status])를 팀이 아니라 이 모델이 보관한다. (WAITING→APPLY→ACTIVE/DEACTIVE)
 */
data class MatchedTeam(
	val id: Long = 0,
	val teamMatchId: Long,
	val teamId: Long,
	val status: MatchedTeamStatus = MatchedTeamStatus.WAITING,
	/** 이 팀이 신청(APPLY)할 때 코인을 지불한 구성원 userId. 미신청이면 null. (미성사 만료 환불 대상 식별용) */
	val applicantUserId: Long? = null,
	val deletedAt: LocalDateTime? = null,
) {

	/** 이 팀이 매칭을 신청한(APPLY) 새 모델을 반환한다. 코인을 지불한 [applicantUserId]를 함께 기록한다. */
	fun apply(applicantUserId: Long): MatchedTeam =
		copy(status = MatchedTeamStatus.APPLY, applicantUserId = applicantUserId)

	/** 이 팀을 활성(ACTIVE)으로 승격한 새 모델을 반환한다. (양 팀 신청으로 팀 매칭 성사 시) */
	fun activate(): MatchedTeam =
		copy(status = MatchedTeamStatus.ACTIVE)

	/** 이 팀을 비활성(DEACTIVE)으로 전이한 새 모델을 반환한다. (한 팀이 매칭을 나갔지만 매칭은 유지될 때) */
	fun deactivate(): MatchedTeam =
		copy(status = MatchedTeamStatus.DEACTIVE)

	/** 이 팀을 [now]에 비활성(DEACTIVE) + 소프트 삭제([deletedAt])한 새 모델을 반환한다. (마지막 종료로 매칭이 제거될 때) */
	fun delete(now: LocalDateTime): MatchedTeam =
		copy(status = MatchedTeamStatus.DEACTIVE, deletedAt = now)

	/** 이 팀이 비활성(DEACTIVE) 상태인지 여부. (매칭을 나간 팀) */
	val isDeactivated: Boolean
		get() = status == MatchedTeamStatus.DEACTIVE

	/** 이 팀이 신청(또는 성사로 활성)했는지 여부. (APPLY/ACTIVE) */
	val hasApplied: Boolean
		get() = status == MatchedTeamStatus.APPLY || status == MatchedTeamStatus.ACTIVE

	/** 이 팀이 환불 대상인지 여부. (신청(APPLY)했으나 아직 성사되지 않은 상태 — 성사로 ACTIVE가 된 팀은 환불 대상이 아니다) */
	val isRefundable: Boolean
		get() = status == MatchedTeamStatus.APPLY
}
