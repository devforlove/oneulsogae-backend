package com.org.oneulsogae.core.teammatch.query.dao

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.teammatch.query.dto.RecommendedTeam

/**
 * 솔로 유저에게 추천된 팀 조회 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * 추천 행이 가리키는 ACTIVE 팀들을 팀원 프로필과 함께 최신순으로 반환한다. 추천이 없거나 팀이 모두 해체됐으면 빈 리스트.
 */
interface GetRecommendedTeamDao {

	/** [viewerGender]는 조회 사용자(뷰어) 성별로, 아직 매칭이 없는 순수 추천 팀의 신청/수락 비용 표시를 남녀별로 계산하는 데 쓴다. */
	fun findByUserId(userId: Long, viewerGender: Gender?): List<RecommendedTeam>
}
