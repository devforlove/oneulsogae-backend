package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.teammatch.command.entity.RecommendedTeamEntity
import java.time.LocalDate

/**
 * [RecommendedTeamEntity] 테스트 픽스처. 추천 포인터(userId→teamId)와 추천 일자를 합리적 기본값으로 채운다.
 */
object RecommendedTeamEntityFixture {

	fun create(
		userId: Long = 1L,
		teamId: Long = 1L,
		recommendedDate: LocalDate = LocalDate.of(2026, 1, 1),
	): RecommendedTeamEntity =
		RecommendedTeamEntity(
			userId = userId,
			teamId = teamId,
			recommendedDate = recommendedDate,
		)
}
