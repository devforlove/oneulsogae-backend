package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.out.DeleteMatchUserPort
import com.org.meeple.core.match.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.match.command.application.port.out.GetMatchUserPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchUserPort
import com.org.meeple.core.match.command.domain.MatchUser
import com.org.meeple.infra.match.command.entity.MatchUserEntity
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.match.command.mapper.applyFrom
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.MatchUserJpaRepository
import com.org.meeple.infra.region.RegionProximityRegistry
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [MatchUserEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나 — 매칭 읽기 모델의 후보 조회/적재/삭제 out-port를 함께 구현)
 * 후보 선정([GetMatchCandidatePort])은 요청자 지역에서 가까운 지역 순서를 정렬 키로 실어, 근접 지역 전체에서 최근접 신선 후보 1명을 단일 쿼리로 찾는다.
 * 적재/삭제/조회([SaveMatchUserPort]/[DeleteMatchUserPort]/[GetMatchUserPort])는 user 도메인 이벤트 동기화에 쓰인다.
 */
@Component
class MatchUserAdapter(
	private val matchUserJpaRepository: MatchUserJpaRepository,
	private val regionProximityRegistry: RegionProximityRegistry,
	private val queryFactory: JPAQueryFactory,
) : GetMatchCandidatePort, SaveMatchUserPort, GetMatchUserPort, DeleteMatchUserPort {

	/**
	 * 요청자([requesterId]) 지역에서 가까운 지역 순으로, "반대 성별([gender])·최근 로그인([loginAfter] 이후)·재소개 이력 없음" 후보 중
	 * 가장 가까운(같은 지역 내에서는 최근 로그인) 1명을 반환한다. 근접 지역 어디에도 없으면 null.
	 *
	 * 근접 지역 전체를 `region_id in (...)`로 한 번에 보고, 근접 순서를 정렬 키(거리 순위)로 실어 단일 쿼리로 최근접 후보를 뽑는다.
	 * (지역마다 따로 조회하지 않아, 무후보 상황에서도 왕복이 1회로 고정된다)
	 * 이력 제외는 두 사람이 한 매칭(solo_match_members)에 함께 참가한 적이 있는지로 판단한다.
	 * (소프트 삭제된 매칭은 @SQLRestriction으로 제외 — command의 existsByPair와 동일 의미)
	 */
	override fun findOneCandidate(requesterId: Long, gender: Gender, regionId: Long, loginAfter: LocalDateTime): Long? {
		val orderedRegionIds: List<Long> = regionProximityRegistry.nearbyRegionIds(regionId)
		if (orderedRegionIds.isEmpty()) return null

		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val me: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		val other: QSoloMatchMemberEntity = QSoloMatchMemberEntity("other")

		return queryFactory
			.select(matchUser.userId)
			.from(matchUser)
			.where(
				matchUser.gender.eq(gender),
				matchUser.regionId.`in`(orderedRegionIds),
				matchUser.lastLoginAt.goe(loginAfter),
				JPAExpressions
					.selectOne()
					.from(me)
					.join(other).on(other.matchId.eq(me.matchId))
					.where(me.userId.eq(requesterId).and(other.userId.eq(matchUser.userId)))
					.notExists(),
			)
			.orderBy(regionDistanceRank(matchUser, orderedRegionIds).asc(), matchUser.lastLoginAt.desc())
			.limit(1)
			.fetchFirst()
	}

	/**
	 * 근접 거리 순위 정렬 식. [orderedRegionIds](가까운 순)의 인덱스를 순위로 매핑해, 가까운 지역일수록 작은 값을 갖게 한다.
	 * (where의 region_id in 조건으로 후보는 항상 이 목록 안에 있으므로 otherwise 가지는 도달하지 않는 방어값이다)
	 */
	private fun regionDistanceRank(matchUser: QMatchUserEntity, orderedRegionIds: List<Long>): NumberExpression<Int> {
		var cases: CaseBuilder.Cases<Int, NumberExpression<Int>> =
			CaseBuilder().`when`(matchUser.regionId.eq(orderedRegionIds[0])).then(0)
		for (rank: Int in 1 until orderedRegionIds.size) {
			cases = cases.`when`(matchUser.regionId.eq(orderedRegionIds[rank])).then(rank)
		}
		return cases.otherwise(Int.MAX_VALUE)
	}

	// user_id 기준 upsert: 기존 행이 있으면 가변 필드만 갱신(UPDATE), 없으면 새 엔티티로 INSERT.
	override fun save(matchUser: MatchUser): MatchUser {
		val entity: MatchUserEntity = matchUserJpaRepository.findByUserId(matchUser.userId)
			?.also { it.applyFrom(matchUser) }
			?: matchUser.toEntity()
		return matchUserJpaRepository.save(entity).toDomain()
	}

	override fun updateLastLoginAt(userId: Long, lastLoginAt: LocalDateTime) {
		matchUserJpaRepository.updateLastLoginAt(userId, lastLoginAt)
	}

	override fun findByUserId(userId: Long): MatchUser? =
		matchUserJpaRepository.findByUserId(userId)?.toDomain()

	override fun deleteByUserId(userId: Long) {
		matchUserJpaRepository.deleteByUserId(userId)
	}
}
