package com.org.meeple.core.match.command.domain

import com.org.meeple.common.match.MatchedTeamStatus
import java.time.LocalDateTime

/**
 * 한 팀 매칭([TeamMatch])에 참가한 팀([MatchedTeam]) 목록의 일급 컬렉션.
 * 참가 팀 식별과, 재소개 방지에 쓰는 멤버 키(정렬된 team-id 결합) 산출을 한곳에 응집한다.
 */
data class MatchedTeams(
	val values: List<MatchedTeam>,
) {

	/** 참가 팀 수. */
	val size: Int
		get() = values.size

	/** 참가 팀 id 목록. */
	fun teamIds(): List<Long> =
		values.map { matchedTeam: MatchedTeam -> matchedTeam.teamId }

	/** 두 팀 조합을 식별하는 정규화 키. (순서와 무관하게 같은 조합이면 같은 키) */
	fun memberKey(): String =
		teamIds().sorted().joinToString("-")

	/** 모든 참가 팀에 소속 팀 매칭 id([teamMatchId])를 채운 새 컬렉션. (헤더 저장으로 id를 얻은 뒤 영속화 직전에 호출) */
	fun withTeamMatchId(teamMatchId: Long): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> matchedTeam.copy(teamMatchId = teamMatchId) })

	/** 모든 참가 팀을 비활성(DEACTIVE)으로 전이한 새 컬렉션. (팀 해체로 미성사 매칭을 종료할 때) */
	fun deactivateAll(): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> matchedTeam.deactivate() })

	/** [teamId]가 이 팀 매칭의 참가 팀인지 여부. */
	fun isParticipant(teamId: Long): Boolean =
		values.any { matchedTeam: MatchedTeam -> matchedTeam.teamId == teamId }

	/** [teamId] 팀을 찾는다. 없으면 null. */
	fun find(teamId: Long): MatchedTeam? =
		values.firstOrNull { matchedTeam: MatchedTeam -> matchedTeam.teamId == teamId }

	/** [teamId] 팀을 신청(APPLY) 처리하고 지불자([applicantUserId])를 기록한 새 컬렉션을 반환한다. (나머지는 그대로) */
	fun apply(teamId: Long, applicantUserId: Long): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> if (matchedTeam.teamId == teamId) matchedTeam.apply(applicantUserId) else matchedTeam })

	/** 신청(APPLY/ACTIVE)한 팀들. (미성사 만료 환불 대상 산정에 쓴다) */
	fun applied(): List<MatchedTeam> =
		values.filter { matchedTeam: MatchedTeam -> matchedTeam.hasApplied }

	/** [teamId] 팀만 비활성(DEACTIVE) 전이한 새 컬렉션을 반환한다. (나머지는 그대로, 소프트 삭제는 안 함) */
	fun deactivate(teamId: Long): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> if (matchedTeam.teamId == teamId) matchedTeam.deactivate() else matchedTeam })

	/** 모든 참가 팀을 [now]에 소프트 삭제(제거)한 새 컬렉션을 반환한다. (마지막 종료로 매칭 헤더까지 제거될 때) */
	fun delete(now: LocalDateTime): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> matchedTeam.delete(now) })

	/** [teamId]를 제외한 상대 팀이 모두 비활성(DEACTIVE)인지 여부. (이 팀이 나가면 알릴 상대가 없는 마지막 종료) */
	fun isLastActiveTeam(teamId: Long): Boolean =
		values.filter { matchedTeam: MatchedTeam -> matchedTeam.teamId != teamId }
			.all { matchedTeam: MatchedTeam -> matchedTeam.status == MatchedTeamStatus.DEACTIVE }

	/** 모든 참가 팀이 신청했는지 여부. (참가 팀이 있고 전원 APPLY/ACTIVE) */
	fun allApplied(): Boolean =
		values.isNotEmpty() && values.all { matchedTeam: MatchedTeam -> matchedTeam.hasApplied }

	/** 한 팀이라도 신청했는지 여부. */
	fun anyApplied(): Boolean =
		values.any { matchedTeam: MatchedTeam -> matchedTeam.hasApplied }

	/** 모든 참가 팀을 활성(ACTIVE)으로 승격한 새 컬렉션을 반환한다. (양 팀 신청으로 성사 시) */
	fun activateAll(): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> matchedTeam.activate() })

	/** 모든 참가 팀이 비활성(DEACTIVE)인지 여부. */
	fun allDeactivated(): Boolean =
		values.all { matchedTeam: MatchedTeam -> matchedTeam.status == MatchedTeamStatus.DEACTIVE }

	companion object {

		/** teamId들로 참가 팀 목록을 만든다. (teamMatchId는 저장 시 채워진다) */
		fun of(teamIds: List<Long>): MatchedTeams =
			MatchedTeams(
				teamIds.map { teamId: Long -> MatchedTeam(teamMatchId = 0, teamId = teamId) },
			)
	}
}
