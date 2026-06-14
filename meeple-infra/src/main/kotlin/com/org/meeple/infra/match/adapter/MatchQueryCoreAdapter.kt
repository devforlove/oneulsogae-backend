package com.org.meeple.infra.match.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.application.port.out.GetMatchWithPartnerPort
import com.org.meeple.core.match.domain.Match
import com.org.meeple.core.match.domain.MatchWithPartner
import com.org.meeple.core.user.domain.UserDetail
import com.org.meeple.infra.match.entity.QMatchEntity
import com.org.meeple.infra.user.entity.QUserDetailEntity
import com.org.meeple.infra.match.mapper.toDomain
import com.org.meeple.infra.user.mapper.toDomain as toUserDetailDomain
import com.querydsl.core.Tuple
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * core 모듈이 쓰는 [MatchEntity]의 QueryDSL 어댑터.
 * 동적 컬럼·조인이 필요한 조회([GetMatchWithPartnerPort])만 전담하며, `JPAQueryFactory`만 주입한다.
 * 단건/존재 조회·저장은 [MatchCoreAdapter]가 별도로 둔다.
 */
@Component
class MatchQueryCoreAdapter(
	private val queryFactory: JPAQueryFactory,
) : GetMatchWithPartnerPort {

	/**
	 * 매칭 + 상대 프로필을 조인 조회한다. (만료된 소개는 now 기준으로 제외)
	 * 사용자는 성별([gender])에 따라 항상 한쪽 컬럼에만 있으므로(남=male_user_id, 여=female_user_id),
	 * "내 컬럼/상대 컬럼"을 동적으로 골라 단일 컬럼 조건으로 조회한다. (CASE/OR 없이 단일 컬럼 조건 → 인덱스 활용)
	 * 연관 매핑이 없는 [QUserDetailEntity]를 명시적 join ... on(상대 userId)으로 묶어 한 번에 가져온다. (1+N 방지)
	 */
	override fun findAllWithPartnerByUserId(userId: Long, gender: Gender, now: LocalDateTime): List<MatchWithPartner> {
		val match: QMatchEntity = QMatchEntity.matchEntity
		val partnerDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		// 성별에 따라 조회 사용자 컬럼/상대 컬럼을 동적으로 고른다.
		val isMale: Boolean = gender == Gender.MALE
		val myUserIdColumn: NumberPath<Long> = if (isMale) match.maleUserId else match.femaleUserId
		val partnerUserIdColumn: NumberPath<Long> = if (isMale) match.femaleUserId else match.maleUserId

		val rows: List<Tuple> = queryFactory
			.select(match, partnerDetail)
			.from(match)
			.join(partnerDetail).on(partnerDetail.userId.eq(partnerUserIdColumn))
			.where(
				myUserIdColumn.eq(userId),
				match.expiresAt.goe(now),
			)
			.fetch()

		return rows.map { row: Tuple ->
			val matchDomain: Match = row.get(match)!!.toDomain()
			val partner: UserDetail = row.get(partnerDetail)!!.toUserDetailDomain()
			MatchWithPartner(userId, matchDomain, partner)
		}
	}
}
