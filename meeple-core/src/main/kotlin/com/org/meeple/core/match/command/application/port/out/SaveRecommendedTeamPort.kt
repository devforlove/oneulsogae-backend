package com.org.meeple.core.match.command.application.port.out

import java.time.LocalDate

/**
 * 추천 팀(recommended_teams)을 교체(upsert) 저장하는 아웃포트.
 * 추천 영속성은 infra가 가지므로 core는 이 포트만 정의하고, 실제 upsert는 infra 어댑터가 구현한다.
 * (배치(scheduler)의 동명 포트와 별개로 core 경로가 쓰는 포트다 — 모듈별로 각자 정의한다)
 */
interface SaveRecommendedTeamPort {

	/** [userId]의 추천을 [teamId]로 교체(upsert)한다. 유저당 한 행만 유지한다. */
	fun replace(userId: Long, teamId: Long, recommendedDate: LocalDate)
}
