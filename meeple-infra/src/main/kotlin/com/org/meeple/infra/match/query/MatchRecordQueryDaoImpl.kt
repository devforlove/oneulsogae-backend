package com.org.meeple.infra.match.query

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.match.command.repository.MatchJpaRepository
import com.org.meeple.infra.match.command.repository.MatchMemberJpaRepository
import com.org.meeple.scheduler.match.query.dao.MatchRecordQueryDao
import com.org.meeple.scheduler.match.query.dto.MatchedUserIds
import org.springframework.stereotype.Component

/**
 * scheduler [MatchRecordQueryDao]의 구현. (조회 전용 — 기록은 command [com.org.meeple.infra.match.command.adapter.MatchAdapter]의 SaveMatchRecordPort가 담당)
 * 매칭 이력 조회를 match 엔티티 리포지토리에 위임해 구현한다. (entity·repository는 command 아래에 있고, query→command 참조는 허용)
 * scheduler는 core에 의존하지 않으므로(자기 dao만 보유), core 도메인을 아는 infra가 잇는다.
 */
@Component
class MatchRecordQueryDaoImpl(
	private val matchJpaRepository: MatchJpaRepository,
	private val matchMemberJpaRepository: MatchMemberJpaRepository,
) : MatchRecordQueryDao {

	// 참가자 조합 키(정렬된 userId)로 소개 이력 존재 여부만 확인한다. (udx_member_key)
	override fun existsByPair(userIdA: Long, userIdB: Long): Boolean =
		matchJpaRepository.existsByMemberKey(MatchMembers.memberKeyOf(listOf(userIdA, userIdB)))

	// 성사(MATCHED) 매칭에 속한 사용자 ID 전체를 Set으로 정리해 일급 컬렉션으로 감싼다.
	override fun findMatchedUserIds(): MatchedUserIds =
		MatchedUserIds(matchMemberJpaRepository.findUserIdsByMatchStatus(MatchStatus.MATCHED).toSet())
}
