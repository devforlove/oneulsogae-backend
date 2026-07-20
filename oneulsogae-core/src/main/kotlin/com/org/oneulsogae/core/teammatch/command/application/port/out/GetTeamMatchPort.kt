package com.org.oneulsogae.core.teammatch.command.application.port.out

import com.org.oneulsogae.core.teammatch.command.domain.TeamMatch

/**
 * 팀 매칭 애그리거트(헤더 + 참가 팀) 조회 포트. (command 흐름에서 변경/정리 대상 로드)
 * 구현은 infra의 [com.org.oneulsogae.infra.teammatch.command.adapter.TeamMatchAdapter]가 담당한다.
 */
interface GetTeamMatchPort {

	/** [teamId]가 참가했고 아직 종료(CLOSED)되지 않은 팀 매칭들을 (참가 팀 전원과 함께) 조회한다. 없으면 빈 목록. */
	fun findActiveByTeamId(teamId: Long): List<TeamMatch>

	/** 팀 매칭 애그리거트(헤더 + 참가 팀)를 id로 조회한다. 없으면 null. (소프트 삭제 제외, 종료(CLOSED) 매칭도 포함) */
	fun findById(teamMatchId: Long): TeamMatch?

	/**
	 * [memberKey] 조합의 팀 매칭이 이미 존재하는지 여부. (소프트삭제된 과거 소개 포함 — 재소개 방지 유니크와 같은 범위)
	 * 추천 팀 승격 전, 이미 소개된 조합이면 건너뛰어 ux_member_key 유니크 위반(5xx)을 막는 데 쓴다.
	 */
	fun existsByMemberKey(memberKey: String): Boolean
}
