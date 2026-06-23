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
 * 후보 선정([GetMatchCandidatePort])은 가까운 [NEAREST_REGION_FANOUT]개 지역에서 먼저 단일 쿼리로 찾고, 없으면 나머지 지역으로 폴백한다.
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
	 * 가까운 [NEAREST_REGION_FANOUT]개 지역으로 먼저 한 번 조회하고(거의 대부분 여기서 후보가 잡힌다), 비면 나머지 지역으로 한 번 더 폴백한다.
	 * 근접 순서는 정렬 키(거리 순위 CASE)로 싣는다. CASE 정렬은 인덱스로 못 받아 filesort가 생기므로, region_id IN 대상을 K개로 제한해
	 * 정렬 대상 행 수를 묶어 둔다. (전체 지역을 한 번에 IN으로 넣으면 반대 성별 활성 풀 전체를 filesort하게 된다)
	 * 왕복은 보통 1회, 가까운 K개에 후보가 전무한 드문 경우만 2회다.
	 */
	override fun findOneCandidate(requesterId: Long, gender: Gender, regionId: Long, loginAfter: LocalDateTime): Long? {
		val orderedRegionIds: List<Long> = regionProximityRegistry.nearbyRegionIds(regionId)
		if (orderedRegionIds.isEmpty()) return null

		// 1차: 가장 가까운 K개 지역. 2차(폴백): 나머지 지역. 각 목록은 이미 가까운 순이라 거리 순위 CASE가 그대로 유효하다.
		val nearest: List<Long> = orderedRegionIds.take(NEAREST_REGION_FANOUT)
		findNearestFreshCandidate(requesterId, gender, nearest, loginAfter)?.let { candidateId: Long -> return candidateId }

		val rest: List<Long> = orderedRegionIds.drop(NEAREST_REGION_FANOUT)
		if (rest.isEmpty()) return null
		return findNearestFreshCandidate(requesterId, gender, rest, loginAfter)
	}

	/**
	 * [regionIds](가까운 순) 안에서 "반대 성별·최근 로그인·재소개 이력 없음" 후보를 거리 순위(같은 지역 내 최근 로그인)로 1명 조회한다.
	 * 이력 제외는 두 사람이 한 매칭(solo_match_members)에 함께 참가한 적이 있는지로 판단한다.
	 * (소프트 삭제된 매칭은 @SQLRestriction으로 제외 — command의 existsByPair와 동일 의미)
	 */
	private fun findNearestFreshCandidate(
		requesterId: Long,
		gender: Gender,
		regionIds: List<Long>,
		loginAfter: LocalDateTime,
	): Long? {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val me: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		val other: QSoloMatchMemberEntity = QSoloMatchMemberEntity("other")

		return queryFactory
			.select(matchUser.userId)
			.from(matchUser)
			.where(
				matchUser.gender.eq(gender),
				matchUser.regionId.`in`(regionIds),
				matchUser.lastLoginAt.goe(loginAfter),
				JPAExpressions
					.selectOne()
					.from(me)
					.join(other).on(other.matchId.eq(me.matchId))
					.where(me.userId.eq(requesterId).and(other.userId.eq(matchUser.userId)))
					.notExists(),
			)
			.orderBy(regionDistanceRank(matchUser, regionIds).asc(), matchUser.lastLoginAt.desc())
			.limit(1)
			.fetchFirst()
	}

	/**
	 * 근접 거리 순위 정렬 식. [regionIds](가까운 순)의 인덱스를 순위로 매핑해, 가까운 지역일수록 작은 값을 갖게 한다.
	 * (where의 region_id in 조건으로 후보는 항상 이 목록 안에 있으므로 otherwise 가지는 도달하지 않는 방어값이다)
	 */
	private fun regionDistanceRank(matchUser: QMatchUserEntity, regionIds: List<Long>): NumberExpression<Int> {
		var cases: CaseBuilder.Cases<Int, NumberExpression<Int>> =
			CaseBuilder().`when`(matchUser.regionId.eq(regionIds[0])).then(0)
		for (rank: Int in 1 until regionIds.size) {
			cases = cases.`when`(matchUser.regionId.eq(regionIds[rank])).then(rank)
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

	companion object {
		/**
		 * 1차 후보 조회에 포함할 "가장 가까운 지역" 개수. 거의 모든 요청자가 이 안에서 후보를 찾으므로 왕복은 보통 1회로 끝난다.
		 * 이 값이 곧 CASE 정렬(filesort) 대상 지역 범위라, 너무 크면 정렬 비용이, 너무 작으면 폴백(2차 쿼리) 빈도가 늘어난다.
		 */
		private const val NEAREST_REGION_FANOUT = 20
	}
}
