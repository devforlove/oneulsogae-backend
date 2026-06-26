package com.org.meeple.infra.match.command.adapter

import com.org.meeple.core.match.command.application.port.out.GetRecommendedTeamPort
import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import com.org.meeple.infra.match.command.repository.RecommendedTeamJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate
import com.org.meeple.core.match.command.application.port.out.SaveRecommendedTeamPort as CoreSaveRecommendedTeamPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveRecommendedTeamPort as SchedulerSaveRecommendedTeamPort

/**
 * [RecommendedTeamEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 추천 적재 out-port(배치용 [SchedulerSaveRecommendedTeamPort]·core용 [CoreSaveRecommendedTeamPort])와 core의 추천 단건 조회 out-port([GetRecommendedTeamPort])를 함께 구현한다.
 * 두 적재 포트는 시그니처가 같아 하나의 [replace] 구현으로 충족한다.
 * user_id 기준 upsert: 기존 행이 있으면 team_id·추천 일자만 갱신(UPDATE), 없으면 새 행 INSERT. (유저당 1행 = 주기마다 교체)
 */
@Component
class RecommendedTeamAdapter(
	private val recommendedTeamJpaRepository: RecommendedTeamJpaRepository,
) : SchedulerSaveRecommendedTeamPort, CoreSaveRecommendedTeamPort, GetRecommendedTeamPort {

	override fun findRecommendedTeamId(userId: Long): Long? =
		recommendedTeamJpaRepository.findByUserId(userId)?.teamId

	override fun replace(userId: Long, teamId: Long, recommendedDate: LocalDate) {
		val entity: RecommendedTeamEntity = recommendedTeamJpaRepository.findByUserId(userId)
			?.also {
				it.teamId = teamId
				it.recommendedDate = recommendedDate
			}
			?: RecommendedTeamEntity(userId = userId, teamId = teamId, recommendedDate = recommendedDate)
		recommendedTeamJpaRepository.save(entity)
	}
}
