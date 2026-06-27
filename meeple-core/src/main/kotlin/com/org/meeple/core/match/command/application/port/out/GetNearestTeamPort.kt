package com.org.meeple.core.match.command.application.port.out

/**
 * 유저와 가장 가까운 추천 후보 팀의 id를 찾는 아웃포트. (적재용 — 카드 표시가 아니라 teamId만 필요)
 * 유저의 매칭 정보(match_user)에서 성별·활동지역을 읽어, 가까운 권역 순으로 반대 성별 결성(ACTIVE) 팀 1곳을 찾는다.
 * (조회 경로의 카드 조립(GetRecommendedTeamDao)과 달리 팀원 프로필 없이 teamId만 반환한다 — command·query는 각자 구현한다)
 */
interface GetNearestTeamPort {

	/** [userId]와 가장 가까운 반대 성별 결성(ACTIVE) 팀의 id. 매칭 정보가 없거나 후보가 없으면 null. */
	fun findNearestTeamId(userId: Long): Long?
}
