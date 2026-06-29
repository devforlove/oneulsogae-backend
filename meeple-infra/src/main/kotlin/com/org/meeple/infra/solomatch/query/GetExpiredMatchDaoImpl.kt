package com.org.meeple.infra.solomatch.query

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchEntity
import com.org.meeple.infra.teammatch.command.entity.QTeamMatchEntity
import com.org.meeple.scheduler.common.command.application.port.out.GetExpiredMatchPort
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetExpiredMatchPort] 구현. 만료된(미성사) 매칭 id를 조회한다.
 * 만료 = [now] 기준 expires_at 경과 + 상태가 PROPOSED/PARTIALLY_ACCEPTED. (성사 MATCHED는 만료 시각이 +100년이라 자연 제외)
 * 엔티티 @SQLRestriction("deleted_at is null")이 자동 적용돼 이미 제거된 매칭은 조회되지 않는다.
 * (status 등치 + expires_at 범위를 받치는 (status, expires_at) 복합 인덱스를 둔다 — Task 9)
 */
@Component
class GetExpiredMatchDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetExpiredMatchPort {

	override fun findExpiredSoloMatchIds(now: LocalDateTime): List<Long> {
		val soloMatch: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		return queryFactory
			.select(soloMatch.id)
			.from(soloMatch)
			.where(
				soloMatch.status.`in`(MatchStatus.PROPOSED, MatchStatus.PARTIALLY_ACCEPTED),
				soloMatch.expiresAt.lt(now),
			)
			.fetch()
	}

	override fun findExpiredTeamMatchIds(now: LocalDateTime): List<Long> {
		val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
		return queryFactory
			.select(teamMatch.id)
			.from(teamMatch)
			.where(
				teamMatch.status.`in`(MatchStatus.PROPOSED, MatchStatus.PARTIALLY_ACCEPTED),
				teamMatch.expiresAt.lt(now),
			)
			.fetch()
	}
}
