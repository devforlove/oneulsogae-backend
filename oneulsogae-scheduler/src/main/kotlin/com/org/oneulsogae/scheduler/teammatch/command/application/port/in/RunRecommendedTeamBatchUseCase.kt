package com.org.oneulsogae.scheduler.teammatch.command.application.port.`in`

import com.org.oneulsogae.scheduler.teammatch.command.domain.RecommendedTeamBatchResult

/**
 * 근접 팀 추천 일일 배치 인포트(유스케이스).
 * 팀 미소속 솔로 유저를 순회하며 가까운 권역의 반대 성별 ACTIVE 팀 1개를 무작위로 추천 적재(교체)한다.
 * 하루에 한 번만 추천하며(같은 날 재실행 시 이미 추천된 유저는 제외), 과거 매칭/추천 이력은 추천을 막지 않는다.
 * 개별 사용자 처리 실패가 전체 배치를 멈추지 않는다.
 */
interface RunRecommendedTeamBatchUseCase {

    fun run(): RecommendedTeamBatchResult
}
