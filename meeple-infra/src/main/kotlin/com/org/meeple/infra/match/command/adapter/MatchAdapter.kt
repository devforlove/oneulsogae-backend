package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.SoloMatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.command.application.port.out.DeleteMatchPort
import com.org.meeple.core.match.command.application.port.out.GetMatchPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchPort
import com.org.meeple.core.match.command.domain.Match
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.match.command.entity.SoloMatchEntity
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntities
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.MatchJpaRepository
import com.org.meeple.infra.match.command.repository.MatchMemberJpaRepository
import com.org.meeple.scheduler.match.command.application.port.out.GetExpiredMatchIdsPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchRecordPort
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
) : GetMatchPort, SaveMatchPort, SaveMatchRecordPort, DeleteMatchPort, GetExpiredMatchIdsPort {

	// 응답 대기(미성사) 상태로 만료 시각이 지난 소개의 id만 가져온다. (성사·종료는 대상 아님)
	override fun findExpiredMatchIds(now: LocalDateTime): List<Long> =
		matchJpaRepository.findExpiredMatchIds(
			now,
			listOf(MatchStatus.PROPOSED, MatchStatus.PARTIALLY_ACCEPTED),
		)

	// 헤더 + 참가자를 함께 읽어 매칭 애그리거트로 조립한다.
	override fun findById(id: Long): Match? =
		matchJpaRepository.findById(id).orElse(null)?.let { entity: SoloMatchEntity ->
			entity.toDomain(loadMembers(entity.id!!))
		}

	/**
	 * 주어진 매칭(헤더+참가자)을 그대로 영속화한다. (소프트 삭제 여부는 도메인 상태(deletedAt)가 들고, 매퍼가 적용한다)
	 * 제거(소프트 삭제)는 유스케이스가 [Match.delete]로 표현해 넘기므로, 여기선 매핑·저장(merge)만 한다.
	 */
	override fun delete(match: Match) {
		matchJpaRepository.save(match.toEntity())
		matchMemberJpaRepository.saveAll(match.members.toEntities())
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
