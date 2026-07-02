package com.org.meeple.infra.solomatch.command.adapter

import com.org.meeple.common.match.SoloMatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.solomatch.command.application.port.out.GetMatchPort
import com.org.meeple.core.solomatch.command.application.port.out.SaveMatchPort
import com.org.meeple.core.solomatch.command.domain.Match
import com.org.meeple.core.solomatch.command.domain.MatchMembers
import com.org.meeple.infra.solomatch.command.entity.SoloMatchEntity
import com.org.meeple.infra.solomatch.command.mapper.toDomain
import com.org.meeple.infra.solomatch.command.mapper.toEntities
import com.org.meeple.infra.solomatch.command.mapper.toEntity
import com.org.meeple.infra.solomatch.command.repository.MatchJpaRepository
import com.org.meeple.infra.solomatch.command.repository.MatchMemberJpaRepository
import com.org.meeple.scheduler.solomatch.command.application.port.out.SaveMatchRecordPort
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [SoloMatchEntity]의 command 영속성 어댑터. (매칭 엔티티당 command 어댑터는 이 하나 — core·scheduler 모듈의 command out-port를 함께 구현)
 * 매칭은 헤더(matches) + 참가자(solo_match_members)로 이뤄진 하나의 애그리거트이므로, 이 어댑터가 두 테이블의 영속화를 함께 책임진다.
 * core는 단건 조회([GetMatchPort])·저장([SaveMatchPort])을, scheduler는 매칭 이력 기록([SaveMatchRecordPort])을 쓴다.
 * scheduler는 core에 의존하지 않으므로(자기 포트만 보유), core의 [Match]·엔티티를 아는 infra가 둘을 한 어댑터에서 잇는다.
 * 조회 dao(core `GetMatchWithPartnerDao`, scheduler `GetMatchRecordDao`)는 query 패키지의 `*DaoImpl`이 별도로 구현한다.
 */
@Component
class MatchAdapter(
	private val matchJpaRepository: MatchJpaRepository,
	private val matchMemberJpaRepository: MatchMemberJpaRepository,
) : GetMatchPort, SaveMatchPort, SaveMatchRecordPort {

	// 헤더 + 참가자를 함께 읽어 매칭 애그리거트로 조립한다.
	override fun findById(id: Long): Match? =
		matchJpaRepository.findById(id).orElse(null)?.let { entity: SoloMatchEntity ->
			entity.toDomain(loadMembers(entity.id!!))
		}

	/**
	 * 매칭 애그리거트를 저장한다. 헤더를 저장해 id를 얻고, 그 id로 참가자 행들을 함께 저장한다.
	 * 신규면 참가자가 INSERT(member id 0)되고, 응답 반영 등 갱신이면 기존 참가자 행이 수락 여부까지 UPDATE된다.
	 */
	override fun save(match: Match): Match {
		val savedEntity: SoloMatchEntity = matchJpaRepository.save(match.toEntity())
		val matchId: Long = savedEntity.id!!
		val savedMembers: MatchMembers = MatchMembers(
			matchMemberJpaRepository
				.saveAll(match.membersWith(matchId).toEntities())
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMembers)
	}

	override fun markMemberCheckedIfUnchecked(matchId: Long, userId: Long, checkedAt: LocalDateTime): Int =
		matchMemberJpaRepository.markCheckedIfUnchecked(matchId, userId, checkedAt)

	private fun loadMembers(matchId: Long): MatchMembers =
		MatchMembers(matchMemberJpaRepository.findByMatchId(matchId).map { it.toDomain() })

	// 배치(scheduler)가 호출하는 경로이므로 일일 매칭(DAILY)으로 기록한다.
	override fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime) {
		save(
			Match.propose(
				requesterId = requesterId,
				requesterGender = requesterGender,
				partnerId = partnerId,
				matchType = SoloMatchType.DAILY,
				now = now,
			),
		)
	}
}
