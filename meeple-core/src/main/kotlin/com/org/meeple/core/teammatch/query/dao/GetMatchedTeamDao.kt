package com.org.meeple.core.teammatch.query.dao

import com.org.meeple.core.teammatch.query.dto.RecommendedTeam
import java.time.LocalDateTime

/**
 * 내 팀과 매칭된 상대 팀 조회 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * 결성(ACTIVE) 팀을 가진 유저의 미팅탭에서, 추천 팀 대신 내 팀과 진행 중으로 매칭된 상대 팀을 내려줄 때 쓴다.
 */
interface GetMatchedTeamDao {

	/**
	 * 내 팀([myTeamId])과 진행 중으로 매칭된 상대 팀들을 팀원 프로필과 함께 최신순으로 반환한다.
	 * 진행 중 기준: team_match가 종료(CLOSED)되지 않고 [now] 기준 미만료이며, 상대 팀 참가가 DEACTIVE가 아니고 상대 팀 자체가 ACTIVE인 경우. (없으면 빈 리스트)
	 */
	fun findInProgressByTeamId(myTeamId: Long, now: LocalDateTime): List<RecommendedTeam>
}
