package com.org.meeple.infra.match.query

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.match.query.dto.MatchedUserIds
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * scheduler [GetMatchRecordDao]의 QueryDSL 구현. (조회 전용 — 기록은 command [com.org.meeple.infra.match.command.adapter.MatchAdapter]의 SaveMatchRecordPort가 담당)
 * 매칭 이력 조회를 QueryDSL로 구현한다. scheduler는 core에 의존하지 않으므로(자기 dao만 보유), core 도메인을 아는 infra가 잇는다.
 */
@Component
class GetMatchRecordDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMatchRecordDao {

	// 참가자 조합 키(정렬된 userId)로 소개 이력 존재 여부만 확인한다. (ux_member_key)
	override fun existsByPair(userIdA: Long, userIdB: Long): Boolean {
		val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		return queryFactory
			.selectOne()
			.from(match)
			.where(match.memberKey.eq(MatchMembers.memberKeyOf(listOf(userIdA, userIdB))))
			.fetchFirst() != null
	}

	// 성사(MATCHED) 매칭에 속한 사용자 ID 전체를 Set으로 정리해 일급 컬렉션으로 감싼다.
	// 참가자 행에서 출발해 매칭 헤더와 명시 조인하고, 상태만 where로 거른다. (중복 정리는 Set이 맡는다)
	override fun findMatchedUserIds(): MatchedUserIds {
		val member: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		val match: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		val userIds: List<Long> = queryFactory
			.select(member.userId)
			.from(member)
			.join(match).on(match.id.eq(member.matchId))
			.where(match.status.eq(MatchStatus.MATCHED))
			.fetch()
		return MatchedUserIds(userIds.toSet())
	}
}
