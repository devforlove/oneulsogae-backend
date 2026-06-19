package com.org.meeple.infra.match.query

import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.scheduler.match.query.dao.GetMatchBatchTargetDao
import com.org.meeple.scheduler.match.query.dto.MatchBatchCursor
import com.org.meeple.scheduler.match.query.dto.MatchBatchTarget
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [GetMatchBatchTargetDao]의 QueryDSL 구현. 매칭 배치 대상 사용자를 (lastLoginAt, userId) 복합 키셋으로 조회한다.
 * 매칭 읽기 모델(match_user) 단독으로 조회한다. (행의 존재가 곧 정식 가입+프로필 완성이라 status·user_details 조인이 없다)
 * 매칭 판단에 필요한 프로필(gender/age/maritalStatus/regionCode)을 match_user에서 바로 [MatchBatchTarget]로 투영한다.
 * `idx_match_user_recent_login(last_login_at, user_id)` 범위 스캔이라 최근 로그인 구간만 보고 filesort도 없다.
 * 커서 유무로 키셋 조건을 나눠 range seek가 깨지지 않게 한다.
 */
@Component
class GetMatchBatchTargetDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMatchBatchTargetDao {

	override fun findTargets(loginAfter: LocalDateTime, cursor: MatchBatchCursor?, limit: Int): List<MatchBatchTarget> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity

		return queryFactory
			.select(
				Projections.constructor(
					MatchBatchTarget::class.java,
					matchUser.userId,
					matchUser.lastLoginAt,
					matchUser.gender,
					matchUser.age,
					matchUser.maritalStatus,
					matchUser.regionCode,
				),
			)
			.from(matchUser)
			.where(keysetPredicate(matchUser, loginAfter, cursor))
			.orderBy(matchUser.lastLoginAt.asc(), matchUser.userId.asc())
			.limit(limit.toLong())
			.fetch()
	}

	/**
	 * 첫 페이지는 최근 로그인 하한(loginAfter)만, 다음 페이지는 직전 행의 (lastLoginAt, userId) 이후 구간을 건다.
	 * 커서는 항상 loginAfter 이후라 키셋 조건이 최근 로그인 하한도 함께 보장하므로 다음 페이지에선 loginAfter 조건을 생략한다.
	 */
	private fun keysetPredicate(matchUser: QMatchUserEntity, loginAfter: LocalDateTime, cursor: MatchBatchCursor?): Predicate {
		val builder: BooleanBuilder = BooleanBuilder()
		if (cursor == null) {
			builder.and(matchUser.lastLoginAt.goe(loginAfter))
		} else {
			builder.and(
				matchUser.lastLoginAt.gt(cursor.lastLoginAt)
					.or(matchUser.lastLoginAt.eq(cursor.lastLoginAt).and(matchUser.userId.gt(cursor.userId))),
			)
		}
		return builder
	}
}
