package com.org.meeple.infra.alarm.query

import com.org.meeple.core.alarm.query.dao.GetAlarmFromTeamDao
import com.org.meeple.core.alarm.query.dto.AlarmTeamMembers
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

/**
 * 알람을 유발한 팀의 구성원 조회 dao([GetAlarmFromTeamDao]) 구현. (조회 전용)
 * 발신 팀 id 집합을 team_members에 `team_id IN (...)` 한 번으로 조회해 (teamId → userId 목록) 매핑으로 투영한다. (1+N 방지)
 * 팀이 해체되면 구성원 행이 소프트 삭제되는데, 종료된 팀 매칭 알람도 그 팀 구성원을 froms에 표시해야 하므로
 * team_members의 @SQLRestriction("deleted_at is null")을 우회하려고 네이티브 쿼리로 조회한다. (QueryDSL·JPQL로는 우회 불가)
 * 구성원 프로필은 user 도메인 소유라 [GetAlarmFromDaoImpl]이 userId로 따로 조인하므로, 여기서는 userId 매핑만 만든다.
 */
@Component
class GetAlarmFromTeamDaoImpl(
	private val entityManager: EntityManager,
) : GetAlarmFromTeamDao {

	override fun findByTeamIds(teamIds: Set<Long>): AlarmTeamMembers {
		if (teamIds.isEmpty()) return AlarmTeamMembers.empty()

		val sql: String = """
			SELECT team_id, user_id
			FROM team_members
			WHERE team_id IN (:teamIds)
		""".trimIndent()
		@Suppress("UNCHECKED_CAST")
		val rows: List<Array<Any>> = entityManager
			.createNativeQuery(sql)
			.setParameter("teamIds", teamIds)
			.resultList as List<Array<Any>>

		return AlarmTeamMembers(
			rows.groupBy({ row: Array<Any> -> (row[0] as Number).toLong() }, { row: Array<Any> -> (row[1] as Number).toLong() }),
		)
	}
}
