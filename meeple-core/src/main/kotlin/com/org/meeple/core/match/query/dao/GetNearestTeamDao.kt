package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.RecommendedTeam

/**
 * 유저와 가장 가까운 추천 후보 팀을 찾아 카드로 반환하는 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * 유저의 매칭 정보(match_user)에서 성별·활동지역을 읽어, 가까운 권역 순으로 반대 성별 결성(ACTIVE) 팀 1곳을 찾고 팀원 프로필까지 담은 카드로 조립한다.
 * (1:1 매칭 후보 탐색 findOneCandidate와 같은 권역 근접 방식. 카드 형태는 순수 추천과 동일하게 매칭 정보가 비어 있다)
 */
interface GetNearestTeamDao {

	/** [userId]와 가장 가까운 반대 성별 결성(ACTIVE) 팀 카드. 매칭 정보가 없거나 후보가 없으면 null. */
	fun findNearestTeam(userId: Long): RecommendedTeam?
}
