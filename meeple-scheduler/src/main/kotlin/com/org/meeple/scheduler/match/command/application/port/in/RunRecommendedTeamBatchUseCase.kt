package com.org.meeple.scheduler.match.command.application.port.`in`

import com.org.meeple.scheduler.match.command.domain.RecommendedTeamBatchResult

/**
 * 팀 추천 일일 배치 인포트(유스케이스).
 * 팀 미소속 솔로 유저를 순회하며 반대 성별·같은 권역의 ACTIVE 팀 1개를 추천 적재(교체)한다.
 * 개별 사용자 처리 실패가 전체 배치를 멈추지 않는다.
 */
interface RunRecommendedTeamBatchUseCase {

	fun run(): RecommendedTeamBatchResult
}
