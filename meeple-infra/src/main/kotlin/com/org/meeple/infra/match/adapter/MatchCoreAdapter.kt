package com.org.meeple.infra.match.adapter

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.match.application.port.out.GetMatchPort
import com.org.meeple.core.match.application.port.out.SaveMatchPort
import com.org.meeple.core.match.domain.Match
import com.org.meeple.core.match.domain.MatchMembers
import com.org.meeple.infra.match.entity.MatchEntity
import com.org.meeple.infra.match.mapper.toDomain
import com.org.meeple.infra.match.mapper.toEntity
import com.org.meeple.infra.match.repository.MatchJpaRepository
import com.org.meeple.infra.match.repository.MatchMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [MatchEntity]의 Spring Data 어댑터.
 * 매칭은 헤더(matches) + 참가자(match_members)로 이뤄진 하나의 애그리거트이므로, 이 어댑터가 두 테이블의 영속화를 함께 책임진다.
 * 단건/존재 조회([GetMatchPort])·저장([SaveMatchPort])을 구현하며, 재소개 방지(member_key)·일일 소개·성사 사용자 조회는 참가자 조인으로 처리한다.
 * QueryDSL이 필요한 상대 프로필 조인 조회는 [MatchQueryCoreAdapter]가, scheduler 모듈용 어댑터는 [MatchSchedulerAdapter]가 별도로 둔다.
 */
@Component
class MatchCoreAdapter(
	private val matchJpaRepository: MatchJpaRepository,
	private val matchMemberJpaRepository: MatchMemberJpaRepository,
) : GetMatchPort, SaveMatchPort {

	// 헤더 + 참가자를 함께 읽어 매칭 애그리거트로 조립한다.
	override fun findById(id: Long): Match? =
		matchJpaRepository.findById(id).orElse(null)?.let { entity: MatchEntity ->
			entity.toDomain(loadMembers(entity.id!!))
		}

	// 참가자 조합 키(정렬된 userId)로 소개 이력 존재 여부만 확인한다. (udx_member_key)
	override fun existsByPair(userIdA: Long, userIdB: Long): Boolean =
		matchJpaRepository.existsByMemberKey(MatchMembers.memberKeyOf(listOf(userIdA, userIdB)))

	// 성사(MATCHED) 매칭에 속한 사용자 ID 전체. (중복 정리는 호출 측 Set이 맡는다)
	override fun findMatchedUserIds(): List<Long> =
		matchMemberJpaRepository.findUserIdsByMatchStatus(MatchStatus.MATCHED)

	/**
	 * 매칭 애그리거트를 저장한다. 헤더를 저장해 id를 얻고, 그 id로 참가자 행들을 함께 저장한다.
	 * 신규면 참가자가 INSERT(member id 0)되고, 응답 반영 등 갱신이면 기존 참가자 행이 수락 여부까지 UPDATE된다.
	 */
	override fun save(match: Match): Match {
		val savedEntity: MatchEntity = matchJpaRepository.save(match.toEntity())
		val matchId: Long = savedEntity.id!!
		val savedMembers: MatchMembers = MatchMembers(
			matchMemberJpaRepository
				.saveAll(match.members.values.map { it.copy(matchId = matchId).toEntity() })
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMembers)
	}

	private fun loadMembers(matchId: Long): MatchMembers =
		MatchMembers(matchMemberJpaRepository.findByMatchId(matchId).map { it.toDomain() })
}
