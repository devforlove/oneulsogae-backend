package com.org.meeple.scheduler.match.command.application.port.out

import java.time.LocalDate

/**
 * 배치가 솔로 유저의 추천 팀을 적재(교체)하기 위한 아웃포트.
 * 추천 영속성은 infra가 갖고 있으므로 scheduler는 이 포트만 정의하고, 실제 upsert는 infra 어댑터가 구현한다.
 * (scheduler는 core에 의존하지 않는다)
 */
interface SaveRecommendedTeamPort {

	/** [userId]의 추천을 [teamId]로 교체(upsert)한다. 유저당 한 행만 유지한다. */
	fun replace(userId: Long, teamId: Long, recommendedDate: LocalDate)
}
