package com.org.oneulsogae.core.teammatch.command.application.port.out

import com.org.oneulsogae.core.teammatch.command.domain.RecommendedTeamHistory

/**
 * 성사 (유저 → 상대 팀) 이력 저장 out-port. infra 어댑터가 구현한다.
 * 이미 있는 (user_id, team_id)는 건너뛴다(멱등) — 같은 상대와 재매칭해도 유니크 위반으로 롤백되지 않도록.
 */
interface SaveRecommendedTeamHistoryPort {
	fun saveAll(histories: List<RecommendedTeamHistory>)
}
