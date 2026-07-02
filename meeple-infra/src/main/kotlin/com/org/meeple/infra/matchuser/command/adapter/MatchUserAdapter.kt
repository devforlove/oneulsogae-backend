package com.org.meeple.infra.matchuser.command.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.core.matchuser.command.application.port.out.DeleteMatchUserPort
import com.org.meeple.core.solomatch.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.matchuser.command.application.port.out.GetMatchUserPort
import com.org.meeple.core.matchuser.command.application.port.out.SaveMatchUserPort
import com.org.meeple.core.matchuser.command.domain.MatchUser
import com.org.meeple.infra.matchuser.SameCompanyIntroPredicates
import com.org.meeple.infra.matchuser.command.entity.MatchUserEntity
import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.infra.solomatch.command.entity.QSoloMatchMemberEntity
import com.org.meeple.infra.matchuser.command.mapper.applyFrom
import com.org.meeple.infra.matchuser.command.mapper.toDomain
import com.org.meeple.infra.matchuser.command.mapper.toEntity
import com.org.meeple.infra.matchuser.command.repository.MatchUserJpaRepository
import com.org.meeple.infra.region.PopulatedRegionRegistry
import com.org.meeple.infra.region.RegionProximityRegistry
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [MatchUserEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나 — 매칭 읽기 모델의 후보 조회/적재/삭제 out-port를 함께 구현)
 * 후보 선정([GetMatchCandidatePort])은 가까운 "유저 있는" 지역을 지역 단위로 끝까지 순회해 가장 가까운 후보를 찾는다. (모두 비면 null)
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
	 * 가까운 순으로 "[gender](상대 성별) 유저가 있는" 지역을 **지역 단위로 하나씩** 끝까지 순회해, 가장 가까운 지역의 후보를 먼저 잡고 첫 후보에서 멈춘다.
	 * (지역 단위 단일 조회라 매 쿼리가 인덱스 seek로 끝나고 — 서울 등 밀집 지역의 풀 전체를 한 번에 정렬하지 않는다.
	 * 찾는 성별 유저가 없는 지역은 [PopulatedRegionRegistry]로 미리 걸러 헛조회를 막는다)
	 * 그런 지역 전체에 신선 후보가 없으면 null. (스냅샷 갱신 전 새로 유저가 생긴 지역은 다음 refresh까지 후보에서 빠질 수 있다 — 추천 생략으로 처리)
	 */
	override fun findOneCandidate(
		requesterId: Long,
		gender: Gender,
		regionId: Long,
		loginAfter: LocalDateTime,
		requesterCompanyName: String?,
		requesterRefusesSameCompanyIntro: Boolean,
	): Long? {
		val populatedNearby: List<Long> = regionProximityRegistry.nearbyRegionIds(regionId)
			.filter { id: Long -> populatedRegionRegistry.contains(gender, id) }
		for (candidateRegionId: Long in populatedNearby) {
			findFreshCandidateInRegion(requesterId, gender, candidateRegionId, loginAfter, requesterCompanyName, requesterRefusesSameCompanyIntro)
				?.let { candidateId: Long -> return candidateId }
		}
		return null
	}

	/**
	 * 한 지역([candidateRegionId]) 안에서 "반대 성별·최근 로그인·재소개 이력 없음·같은 회사 소개 차단 아님" 후보 중 최근 로그인 1명을 조회한다.
	 * (gender·region_id 동등 + last_login_at 정렬이 인덱스 `idx_gender_region_id_last_login_at`에 그대로 받쳐져 filesort가 없다.
	 * 같은 회사 차단 조건은 non-sargable 필터라 인덱스 seek 뒤에 걸러진다 — 선택도가 낮아 추가 인덱스는 두지 않는다)
	 */
	private fun findFreshCandidateInRegion(
		requesterId: Long,
		gender: Gender,
		candidateRegionId: Long,
		loginAfter: LocalDateTime,
		requesterCompanyName: String?,
		requesterRefusesSameCompanyIntro: Boolean,
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
				SameCompanyIntroPredicates.notBlockedBySameCompanyIntro(matchUser, requesterCompanyName, requesterRefusesSameCompanyIntro),
			)
			.orderBy(matchUser.lastLoginAt.desc())
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

	override fun updateRefuseSameCompanyIntro(userId: Long, refuse: Boolean): Int =
		matchUserJpaRepository.updateRefuseSameCompanyIntro(userId, refuse)

	override fun findByUserId(userId: Long): MatchUser? =
		matchUserJpaRepository.findByUserId(userId)?.toDomain()

	override fun deleteByUserId(userId: Long) {
		matchUserJpaRepository.deleteByUserId(userId)
	}
}
