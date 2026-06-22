package com.org.meeple.infra.match.command.adapter

import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import com.org.meeple.infra.match.command.repository.RecommendedTeamJpaRepository
import com.org.meeple.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * [RecommendedTeamEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나 — scheduler의 추천 적재 out-port를 구현)
 * user_id 기준 upsert: 기존 행이 있으면 team_id·추천 일자만 갱신(UPDATE), 없으면 새 행 INSERT. (유저당 1행 = 주기마다 교체)
 */
@Component
class RecommendedTeamAdapter(
	private val recommendedTeamJpaRepository: RecommendedTeamJpaRepository,
) : SaveRecommendedTeamPort {

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
