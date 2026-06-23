package com.org.meeple.infra.match.query

import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.match.query.dto.MatchedUserIds
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

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
		val soloMatch: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		return queryFactory
			.selectOne()
			.from(soloMatch)
			.where(soloMatch.memberKey.eq(MatchMembers.memberKeyOf(listOf(userIdA, userIdB))))
			.fetchFirst() != null
	}

	// 성사(MATCHED) 매칭에 '활성(ACTIVE) 참가자로' 속한 사용자 ID 전체를 Set으로 정리해 일급 컬렉션으로 감싼다.
	// 참가자 행에서 출발해 매칭 헤더와 명시 조인하고, 매칭 상태(MATCHED) + 참가자 상태(ACTIVE)로 거른다. (중복 정리는 Set이 맡는다)
	// 채팅방 나가기로 참가가 DEACTIVE된 유저는 매치 헤더가 MATCHED로 남아 있어도 제외 → 다시 소개 대상이 된다. (마지막 1명이 나가면 매치 자체가 소프트 삭제됨)
	override fun findMatchedUserIds(): MatchedUserIds {
		val soloMatchMember: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		val soloMatch: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		val userIds: List<Long> = queryFactory
			.select(soloMatchMember.userId)
			.from(soloMatchMember)
			.join(soloMatch).on(soloMatch.id.eq(soloMatchMember.matchId))
			.where(
				soloMatch.status.eq(MatchStatus.MATCHED),
				soloMatchMember.status.eq(MatchMemberStatus.ACTIVE),
			)
			.fetch()
		return MatchedUserIds(userIds.toSet())
	}

	// introduced_date로 그 날짜 소개 헤더를 seek(idx_introduced_date)하고, 참가자와 명시 조인해 userId를 모은다. (소프트 삭제 행은 @SQLRestriction으로 제외)
	override fun findUserIdsIntroducedOn(date: LocalDate): Set<Long> {
		val soloMatch: QSoloMatchEntity = QSoloMatchEntity.soloMatchEntity
		val soloMatchMember: QSoloMatchMemberEntity = QSoloMatchMemberEntity.soloMatchMemberEntity
		return queryFactory
			.select(soloMatchMember.userId)
			.from(soloMatch)
			.join(soloMatchMember).on(soloMatchMember.matchId.eq(soloMatch.id))
			.where(soloMatch.introducedDate.eq(date))
			.fetch()
			.toSet()
	}
}
