package com.org.oneulsogae.infra.mission.query

import com.org.oneulsogae.core.mission.query.dao.GetMissionCompletionDao
import com.org.oneulsogae.infra.mission.command.entity.QMissionCompletionEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/** [GetMissionCompletionDao]의 QueryDSL 구현. 사용자의 완료 미션 id 집합을 조회한다. */
@Component
class GetMissionCompletionDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMissionCompletionDao {

	override fun findCompletedMissionIds(userId: Long): Set<Long> {
		val completion: QMissionCompletionEntity = QMissionCompletionEntity.missionCompletionEntity
		return queryFactory
			.select(completion.missionId)
			.from(completion)
			.where(completion.userId.eq(userId))
			.fetch()
			.toSet()
	}
}
