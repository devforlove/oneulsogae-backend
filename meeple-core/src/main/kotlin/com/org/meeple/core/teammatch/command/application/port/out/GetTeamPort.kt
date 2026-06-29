package com.org.meeple.core.teammatch.command.application.port.out

import com.org.meeple.core.teammatch.command.domain.Team

/**
 * 팀(헤더+구성원) 조회 아웃포트. (CQRS상 저장은 [SaveTeamPort]와 분리)
 * 구현은 infra의 [com.org.meeple.infra.teammatch.command.adapter.TeamAdapter]가 담당한다.
 */
interface GetTeamPort {

	/** 팀 애그리거트(헤더+구성원)를 조회한다. 없으면 null. (소프트 삭제된 팀은 제외) */
	fun findById(teamId: Long): Team?

	/** [userId]가 활성(삭제되지 않은) 팀 구성원으로 속해 있는지 여부. ("한 팀만" 제약 판정) */
	fun existsActiveTeamMember(userId: Long): Boolean

	/**
	 * [userId]가 (삭제되지 않은) 구성원으로 속한 INVITING 팀들. 없으면 빈 목록.
	 * 받은 초대(INVITED 구성원)뿐 아니라 **내가 만든 초대(owner=ACTIVE 구성원)인 INVITING 팀**도 포함한다.
	 * (수락 시 나머지 진행 중 초대를 모두 비활성화하는 데 쓴다)
	 */
	fun findInvitingTeamsByMember(userId: Long): List<Team>
}
