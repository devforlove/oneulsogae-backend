package com.org.meeple.infra.match.query

import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.scheduler.match.query.dao.MatchBatchTargetDao
import com.org.meeple.scheduler.match.query.dto.MatchBatchCursor
import com.org.meeple.scheduler.match.query.dto.MatchBatchTarget
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [MatchBatchTargetDao]의 QueryDSL 구현. 매칭 배치 대상 사용자를 (lastLoginAt, id) 복합 키셋으로 조회한다.
 * 매칭 판단에 필요한 프로필(gender/age/maritalStatus/regionCode)을 user_details와 명시 조인해 [MatchBatchTarget]로 직접 투영한다.
 * `users(status, last_login_at, id)` 인덱스 범위 스캔이라 최근 로그인 구간만 보고 filesort도 없다.
 * 커서 유무로 키셋 조건을 나눠 range seek가 깨지지 않게 한다. (@SQLRestriction으로 두 엔티티의 soft delete된 행은 자동 제외된다)
 */
@Component
class MatchBatchTargetDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : MatchBatchTargetDao {

	override fun findTargets(loginAfter: LocalDateTime, cursor: MatchBatchCursor?, limit: Int): List<MatchBatchTarget> {
		val user: QUserEntity = QUserEntity.userEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					MatchBatchTarget::class.java,
					user.id,
					user.lastLoginAt,
					detail.gender,
					detail.age,
					detail.maritalStatus,
					detail.regionCode,
				),
			)
			.from(user)
			.join(detail).on(detail.userId.eq(user.id))
			.where(
				user.status.eq(UserStatus.ACTIVE),
				keysetPredicate(user, loginAfter, cursor),
			)
			.orderBy(user.lastLoginAt.asc(), user.id.asc())
			.limit(limit.toLong())
			.fetch()
	}

	/**
	 * 첫 페이지는 최근 로그인 하한(loginAfter)만, 다음 페이지는 직전 행의 (lastLoginAt, id) 이후 구간을 건다.
	 * 커서는 항상 loginAfter 이후라 키셋 조건이 최근 로그인 하한도 함께 보장하므로 다음 페이지에선 loginAfter 조건을 생략한다.
	 */
	private fun keysetPredicate(user: QUserEntity, loginAfter: LocalDateTime, cursor: MatchBatchCursor?): Predicate {
		val builder: BooleanBuilder = BooleanBuilder()
		if (cursor == null) {
			builder.and(user.lastLoginAt.goe(loginAfter))
		} else {
			builder.and(
				user.lastLoginAt.gt(cursor.lastLoginAt)
					.or(user.lastLoginAt.eq(cursor.lastLoginAt).and(user.id.gt(cursor.userId))),
			)
		}
		return builder
	}
}
