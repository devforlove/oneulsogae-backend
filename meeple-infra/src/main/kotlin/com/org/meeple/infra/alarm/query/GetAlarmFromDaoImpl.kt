package com.org.meeple.infra.alarm.query

import com.org.meeple.core.alarm.query.dao.GetAlarmFromDao
import com.org.meeple.core.alarm.query.dto.AlarmFrom
import com.org.meeple.core.alarm.query.dto.AlarmFroms
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 알람 발신 유저 프로필 조회 dao([GetAlarmFromDao])의 QueryDSL 구현. (조회 전용)
 * 발신 유저 id 집합을 user_details에 `user_id IN (...)` 한 번으로 조회해 [AlarmFroms] read model로 직접 투영한다. (1+N 방지)
 * 프로필 상세는 user 도메인 소유 테이블이지만, 표시용 프로필 조인은 infra 읽기 dao가 자기 도메인 read model로 투영한다.
 */
@Component
class GetAlarmFromDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAlarmFromDao {

	override fun findByUserIds(userIds: Set<Long>): AlarmFroms {
		if (userIds.isEmpty()) return AlarmFroms.empty()

		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return AlarmFroms(
			queryFactory
				.select(
					Projections.constructor(
						AlarmFrom::class.java,
						userDetail.userId,
						userDetail.profileImageCode,
						userDetail.gender,
					),
				)
				.from(userDetail)
				.where(userDetail.userId.`in`(userIds))
				.fetch(),
		)
	}
}
