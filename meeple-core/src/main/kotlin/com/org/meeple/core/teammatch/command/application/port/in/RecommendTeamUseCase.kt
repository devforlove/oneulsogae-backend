package com.org.meeple.core.teammatch.command.application.port.`in`

/**
 * 팀 추천 인포트(유스케이스).
 * 솔로 요청자([userId])에게 가장 가까운 반대 성별 결성(ACTIVE) 팀 1곳을 추천(recommended_teams)으로 교체 적재한다.
 * 회사 이메일 인증으로 온보딩이 완료될 때 호출돼, 첫 진입 전에 추천을 미리 적재한다. (조회 경로는 부수효과 없이 읽기만 한다)
 * 요청자가 아직 매칭 가능 상태가 아니거나(읽기 모델 미적재) 가까운 후보 팀이 없으면 이번엔 적재를 생략한다.
 */
interface RecommendTeamUseCase {

	fun recommend(userId: Long)
}
