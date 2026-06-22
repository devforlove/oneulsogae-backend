package com.org.meeple.core.match.command.application.port.out

import com.org.meeple.core.match.command.domain.Team

/**
 * 팀(헤더+구성원) 조회 아웃포트. (CQRS상 저장은 [SaveTeamPort]와 분리)
 * 구현은 infra의 [com.org.meeple.infra.match.command.adapter.TeamAdapter]가 담당한다.
 */
interface GetTeamPort {

	/** 팀 애그리거트(헤더+구성원)를 조회한다. 없으면 null. (소프트 삭제된 팀은 제외) */
	fun findById(teamId: Long): Team?

	/** [userId]가 활성(삭제되지 않은) 팀 구성원으로 속해 있는지 여부. ("한 팀만" 제약 판정) */
	fun existsActiveTeamMember(userId: Long): Boolean

	/** [userId]가 초대중(INVITED)으로 속한 팀들(= 받은 초대). 없으면 빈 목록. */
	fun findInvitedTeams(userId: Long): List<Team>
}
