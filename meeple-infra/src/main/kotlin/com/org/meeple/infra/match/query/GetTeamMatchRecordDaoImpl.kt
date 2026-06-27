package com.org.meeple.infra.match.query

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.core.match.command.domain.MatchedTeams
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.scheduler.match.query.dao.GetTeamMatchRecordDao
import com.org.meeple.scheduler.match.query.dto.MatchedTeamIds
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * scheduler [GetTeamMatchRecordDao]의 QueryDSL 구현. (조회 전용 — 기록은 command [com.org.meeple.infra.match.command.adapter.TeamMatchAdapter]의 SaveTeamMatchRecordPort가 담당)
 * 팀 매칭 이력 조회를 QueryDSL로 구현한다. scheduler는 core에 의존하지 않으므로(자기 dao만 보유), core 도메인을 아는 infra가 잇는다.
 */
@Component
class GetTeamMatchRecordDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val entityManager: EntityManager,
) : GetTeamMatchRecordDao {

	// 두 팀 조합 키(정렬된 team-id 결합)로 소개 이력 존재 여부를 확인한다. (ux_member_key)
	// 취소·종료로 소프트 삭제된 과거 소개도 member_key를 그대로 점유하므로(ux_member_key는 deleted_at 미포함), 재소개 영구 방지를 위해
	// 삭제 행까지 봐야 한다. @SQLRestriction("deleted_at is null")을 우회하려고 네이티브 쿼리로 조회한다. (QueryDSL·JPQL로는 우회 불가)
	override fun existsByPair(teamIdA: Long, teamIdB: Long): Boolean {
		val sql: String = """
			SELECT 1
			FROM team_matches
			WHERE member_key = :memberKey
			LIMIT 1
		""".trimIndent()
		return entityManager
			.createNativeQuery(sql)
			.setParameter("memberKey", MatchedTeams.of(listOf(teamIdA, teamIdB)).memberKey())
			.resultList
			.isNotEmpty()
	}

	// 성사(MATCHED) 팀 매칭에 '활성(ACTIVE) 참가 팀으로' 속한 팀 ID 전체를 Set으로 정리해 일급 컬렉션으로 감싼다.
	// 참가 행에서 출발해 팀 매칭 헤더와 명시 조인하고, 매칭 상태(MATCHED) + 참가 팀 상태(ACTIVE)로 거른다. (해체로 DEACTIVE된 팀은 제외 → 다시 소개 대상)
	override fun findMatchedTeamIds(): MatchedTeamIds {
		val matchedTeam: QMatchedTeamEntity = QMatchedTeamEntity.matchedTeamEntity
		val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
		val teamIds: List<Long> = queryFactory
			.select(matchedTeam.teamId)
			.from(matchedTeam)
			.join(teamMatch).on(teamMatch.id.eq(matchedTeam.teamMatchId))
			.where(
				teamMatch.status.eq(MatchStatus.MATCHED),
				matchedTeam.status.eq(MatchedTeamStatus.ACTIVE),
			)
			.fetch()
		return MatchedTeamIds(teamIds.toSet())
	}

	// introduced_date로 그 날짜 소개 헤더를 seek하고, 참가 팀과 명시 조인해 teamId를 모은다. (소프트 삭제 행은 @SQLRestriction으로 제외)
	override fun findTeamIdsIntroducedOn(date: LocalDate): Set<Long> {
		val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
		val matchedTeam: QMatchedTeamEntity = QMatchedTeamEntity.matchedTeamEntity
		return queryFactory
			.select(matchedTeam.teamId)
			.from(teamMatch)
			.join(matchedTeam).on(matchedTeam.teamMatchId.eq(teamMatch.id))
			.where(teamMatch.introducedDate.eq(date))
			.fetch()
			.toSet()
	}
}
