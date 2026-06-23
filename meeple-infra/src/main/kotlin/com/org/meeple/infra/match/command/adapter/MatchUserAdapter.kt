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
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

/**
 * [MatchUserEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나 — 매칭 읽기 모델의 후보 조회/적재/삭제 out-port를 함께 구현)
 * 후보 선정([GetMatchCandidatePort])은 가까운 "유저 있는" 지역을 지역 단위로 순회해 찾고, 없으면 자격 후보 중 완전 랜덤으로 폴백한다.
 * 적재/삭제/조회([SaveMatchUserPort]/[DeleteMatchUserPort]/[GetMatchUserPort])는 user 도메인 이벤트 동기화에 쓰인다.
 */
@Component
class MatchUserAdapter(
	private val matchUserJpaRepository: MatchUserJpaRepository,
	private val regionProximityRegistry: RegionProximityRegistry,
	private val populatedRegionRegistry: PopulatedRegionRegistry,
	private val queryFactory: JPAQueryFactory,
) : GetMatchCandidatePort, SaveMatchUserPort, GetMatchUserPort, DeleteMatchUserPort {

	/**
	 * 요청자([requesterId])에게 "반대 성별([gender])·최근 로그인([loginAfter] 이후)·재소개 이력 없음" 후보 1명을 반환한다.
	 *
	 * 가까운 순으로 "유저 있는" 지역을 최대 [NEAREST_REGION_FANOUT]개까지 **지역 단위로 하나씩** 조회해, 가장 가까운 지역의 후보를 먼저 잡는다.
	 * (지역 단위 단일 조회라 매 쿼리가 인덱스 seek로 끝나고 — 서울 등 밀집 지역의 풀 전체를 한 번에 정렬하지 않는다.
	 * 빈 지역은 [PopulatedRegionRegistry]로 미리 걸러 헛조회를 막는다)
	 * 가까운 지역들에 신선 후보가 없으면(또는 스냅샷이 미처 못 담은 지역 대비) **자격 후보 중 완전 랜덤으로 1명**을 매칭한다. 어떤 자격 후보도 없으면 null.
	 */
	override fun findOneCandidate(requesterId: Long, gender: Gender, regionId: Long, loginAfter: LocalDateTime): Long? {
		val nearestPopulated: List<Long> = regionProximityRegistry.nearbyRegionIds(regionId)
			.filter { id: Long -> populatedRegionRegistry.contains(id) }
			.take(NEAREST_REGION_FANOUT)
		for (candidateRegionId: Long in nearestPopulated) {
			findFreshCandidateInRegion(requesterId, gender, candidateRegionId, loginAfter)?.let { candidateId: Long -> return candidateId }
		}
		// 가까운 지역에 후보가 없으면 거리를 포기하고 자격(반대 성별·최근 로그인·이력 없음) 후보 중 무작위 1명.
		return findRandomFreshCandidate(requesterId, gender, loginAfter)
	}

	/**
	 * 한 지역([candidateRegionId]) 안에서 "반대 성별·최근 로그인·재소개 이력 없음" 후보 중 최근 로그인 1명을 조회한다.
	 * (gender·region_id 동등 + last_login_at 정렬이 인덱스 `idx_gender_region_id_last_login_at`에 그대로 받쳐져 filesort가 없다)
	 */
	private fun findFreshCandidateInRegion(
		requesterId: Long,
		gender: Gender,
		candidateRegionId: Long,
		loginAfter: LocalDateTime,
	): Long? {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity

		return queryFactory
			.select(matchUser.userId)
			.from(matchUser)
			.where(
				matchUser.gender.eq(gender),
				matchUser.regionId.eq(candidateRegionId),
				matchUser.lastLoginAt.goe(loginAfter),
				notIntroducedBefore(matchUser, requesterId),
			)
			.orderBy(matchUser.lastLoginAt.desc())
			.limit(1)
			.fetchFirst()
	}

	/**
	 * 지역과 무관하게 "반대 성별·최근 로그인·재소개 이력 없음" 자격 후보 중 무작위 1명을 조회한다. (가까운 지역에 후보가 없을 때의 폴백)
	 * 자격 후보 수를 센 뒤 `[0, count)` 무작위 오프셋으로 1명만 건너뛰어 꺼낸다. (`order by rand()`로 풀 전체를 filesort하지 않는다)
	 * 정렬은 인덱스(gender, region_id, last_login_at) 순서와 맞춰, 오프셋 스캔이 filesort 없이 진행되게 한다.
	 */
	private fun findRandomFreshCandidate(requesterId: Long, gender: Gender, loginAfter: LocalDateTime): Long? {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val eligible: Array<Predicate> = arrayOf(
			matchUser.gender.eq(gender),
			matchUser.lastLoginAt.goe(loginAfter),
			notIntroducedBefore(matchUser, requesterId),
		)

		val count: Long = queryFactory
			.select(matchUser.count())
			.from(matchUser)
			.where(*eligible)
			.fetchOne() ?: 0L
		if (count == 0L) return null

		val offset: Long = ThreadLocalRandom.current().nextLong(count)
		return queryFactory
			.select(matchUser.userId)
			.from(matchUser)
			.where(*eligible)
			.orderBy(matchUser.regionId.asc(), matchUser.lastLoginAt.asc())
			.offset(offset)
			.limit(1)
			.fetchFirst()
	}

	/**
	 * [matchUser]가 [requesterId]와 함께 소개된 적이 없음을 뜻하는 조건. (재소개 방지)
	 * 두 사람이 한 매칭(solo_match_members)에 함께 참가한 적이 있는지로 판단한다.
	 * (소프트 삭제된 매칭은 @SQLRestriction으로 제외 — command의 existsByPair와 동일 의미)
	 */
	private fun notIntroducedBefore(matchUser: QMatchUserEntity, requesterId: Long): BooleanExpression {
		val me: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		val other: QSoloMatchMemberEntity = QSoloMatchMemberEntity("other")
		return JPAExpressions
			.selectOne()
			.from(me)
			.join(other).on(other.matchId.eq(me.matchId))
			.where(me.userId.eq(requesterId).and(other.userId.eq(matchUser.userId)))
			.notExists()
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
		 * 지역 단위로 순회할 "가장 가까운 유저 있는 지역"의 최대 개수(= 최악의 지역별 왕복 상한).
		 * 거의 모든 요청자가 이 안에서 후보를 찾는다. 너무 크면 무후보 시 왕복이 늘고, 너무 작으면 랜덤 폴백 빈도가 늘어난다.
		 */
		private const val NEAREST_REGION_FANOUT = 20
	}
}
