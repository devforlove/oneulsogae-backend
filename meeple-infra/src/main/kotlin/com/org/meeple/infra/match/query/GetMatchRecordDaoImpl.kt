package com.org.meeple.infra.match.query

import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.match.command.domain.MatchMembers
import com.org.meeple.infra.match.command.entity.QSoloMatchEntity
import com.org.meeple.infra.match.command.entity.QSoloMatchMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.match.query.dto.MatchedUserIds
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * scheduler [GetMatchRecordDao]의 QueryDSL 구현. (조회 전용 — 기록은 command [com.org.meeple.infra.match.command.adapter.MatchAdapter]의 SaveMatchRecordPort가 담당)
 * 매칭 이력 조회를 QueryDSL로 구현한다. scheduler는 core에 의존하지 않으므로(자기 dao만 보유), core 도메인을 아는 infra가 잇는다.
 */
@Component
class GetMatchRecordDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val entityManager: EntityManager,
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

	// introduced_date로 그 날짜에 소개된 유저를 모은다(idx_introduced_date seek). 취소된 소개(소프트 삭제)도 "오늘 소개 1회 소진"으로 세야
	// 같은 날 재소개를 막으므로, @SQLRestriction("deleted_at is null")을 우회하기 위해 네이티브 쿼리로 조회한다. (QueryDSL·JPQL로는 우회 불가)
	override fun findUserIdsIntroducedOn(date: LocalDate): Set<Long> {
		val sql: String = """
			SELECT m.user_id
			FROM solo_matches s
			JOIN solo_match_members m ON m.match_id = s.id
			WHERE s.introduced_date = :date
		""".trimIndent()
		val userIds: List<*> = entityManager
			.createNativeQuery(sql)
			.setParameter("date", date)
			.resultList
		return userIds.map { (it as Number).toLong() }.toSet()
	}
}
