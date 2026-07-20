package com.org.oneulsogae.core.teammatch.command.application

import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.teammatch.command.application.port.`in`.RecommendTeamUseCase
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetNearestTeamPort
import com.org.oneulsogae.core.teammatch.command.application.port.out.SaveRecommendedTeamPort
import org.springframework.stereotype.Service

/**
 * [RecommendTeamUseCase] 구현.
 * 요청자([userId])와 가장 가까운 반대 성별 결성(ACTIVE) 팀 1곳을 찾아 추천(recommended_teams)으로 교체 적재한다.
 * (예전엔 미팅탭 조회가 추천이 비었을 때 그 자리에서 적재했으나, CQS를 위해 회사 인증 완료 시점에 미리 적재하도록 옮겼다)
 *
 * 요청자가 아직 매칭 가능 상태가 아니거나(읽기 모델 미적재) 가까운 후보 팀이 없으면 적재 없이 끝낸다. (이번엔 추천 생략)
 */
@Service
class RecommendTeamService(
	private val getNearestTeamPort: GetNearestTeamPort,
	private val saveRecommendedTeamPort: SaveRecommendedTeamPort,
	private val timeGenerator: TimeGenerator,
) : RecommendTeamUseCase {

	override fun recommend(userId: Long) {
		// 가까운 후보 팀이 없으면(또는 매칭 읽기 모델 미적재면) 이번엔 추천을 적재하지 않는다.
		val nearestTeamId: Long = getNearestTeamPort.findNearestTeamId(userId) ?: return
		saveRecommendedTeamPort.replace(userId, nearestTeamId, timeGenerator.today())
	}
}
