package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.out.DeleteMatchUserPort
import com.org.meeple.core.match.command.application.port.out.GetMatchCandidatePort
import com.org.meeple.core.match.command.application.port.out.GetMatchUserPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchUserPort
import com.org.meeple.core.match.command.domain.MatchUser
import com.org.meeple.infra.match.command.entity.MatchUserEntity
import com.org.meeple.infra.match.command.mapper.applyFrom
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.MatchUserJpaRepository
import com.org.meeple.infra.region.RegionProximityRegistry
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [MatchUserEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나 — 매칭 읽기 모델의 후보 조회/적재/삭제 out-port를 함께 구현)
 * 후보 선정([GetMatchCandidatePort])은 요청자 지역에서 가까운 지역부터 순회하며, 각 지역의 최근접 신선 후보를 match_user 단독 조회로 찾는다.
 * 적재/삭제/조회([SaveMatchUserPort]/[DeleteMatchUserPort]/[GetMatchUserPort])는 user 도메인 이벤트 동기화에 쓰인다.
 */
@Component
class MatchUserAdapter(
	private val matchUserJpaRepository: MatchUserJpaRepository,
	private val regionProximityRegistry: RegionProximityRegistry,
) : GetMatchCandidatePort, SaveMatchUserPort, GetMatchUserPort, DeleteMatchUserPort {

	/**
	 * 요청자 지역에서 가까운 지역 순으로 순회하며, 각 지역의 "반대 성별·최근 로그인·재소개 이력 없음" 후보 중
	 * 최근 로그인 1명을 찾는다. 가장 가까운 지역에서 먼저 찾으면 즉시 반환하고, 끝까지 없으면 null.
	 */
	override fun findOneCandidate(requesterId: Long, gender: Gender, regionId: Long, loginAfter: LocalDateTime): Long? {
		val limitOne: Pageable = PageRequest.of(0, 1)
		return regionProximityRegistry.nearbyRegionIds(regionId)
			.firstNotNullOfOrNull { candidateRegionId: Long ->
				matchUserJpaRepository
					.findNearestCandidateInRegion(requesterId, gender, candidateRegionId, loginAfter, limitOne)
					.firstOrNull()
			}
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
