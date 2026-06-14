package com.org.meeple.infra.match.adapter

import com.org.meeple.core.match.application.port.out.SaveMatchMemberPort
import com.org.meeple.core.match.domain.MatchMembers
import com.org.meeple.infra.match.mapper.toDomain
import com.org.meeple.infra.match.mapper.toEntity
import com.org.meeple.infra.match.repository.MatchMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [com.org.meeple.infra.match.entity.MatchMemberEntity]의 Spring Data 어댑터.
 * 매칭 생성 시 참가자 저장([SaveMatchMemberPort])을 `MatchMemberJpaRepository`로 구현한다.
 */
@Component
class MatchMemberCoreAdapter(
	private val matchMemberJpaRepository: MatchMemberJpaRepository,
) : SaveMatchMemberPort {

	override fun saveAll(members: MatchMembers): MatchMembers =
		MatchMembers(
			matchMemberJpaRepository.saveAll(members.values.map { it.toEntity() }).map { it.toDomain() },
		)
}
