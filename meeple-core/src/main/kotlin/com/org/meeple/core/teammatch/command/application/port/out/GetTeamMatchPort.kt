package com.org.meeple.core.teammatch.command.application.port.out

import com.org.meeple.core.teammatch.command.domain.TeamMatch

/**
 * 팀 매칭 애그리거트(헤더 + 참가 팀) 조회 포트. (command 흐름에서 변경/정리 대상 로드)
 * 구현은 infra의 [com.org.meeple.infra.teammatch.command.adapter.TeamMatchAdapter]가 담당한다.
 */
interface GetTeamMatchPort {

	/** [teamId]가 참가했고 아직 종료(CLOSED)되지 않은 팀 매칭들을 (참가 팀 전원과 함께) 조회한다. 없으면 빈 목록. */
	fun findActiveByTeamId(teamId: Long): List<TeamMatch>

	/** 팀 매칭 애그리거트(헤더 + 참가 팀)를 id로 조회한다. 없으면 null. (소프트 삭제 제외, 종료(CLOSED) 매칭도 포함) */
	fun findById(teamMatchId: Long): TeamMatch?
}
