package com.org.meeple.infra.alarm.query

import com.org.meeple.core.alarm.query.dao.GetAlarmFromTeamDao
import com.org.meeple.core.alarm.query.dto.AlarmTeamMembers
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 알람을 유발한 팀의 구성원 조회 dao([GetAlarmFromTeamDao])의 QueryDSL 구현. (조회 전용)
 * 발신 팀 id 집합을 team_members에 `team_id IN (...)` 한 번으로 조회해 (teamId → userId 목록) 매핑으로 투영한다. (1+N 방지)
 * (team_members의 @SQLRestriction("deleted_at is null")로 삭제되지 않은 구성원만 조회된다)
 * 구성원 프로필은 user 도메인 소유라 [GetAlarmFromDaoImpl]이 userId로 따로 조인하므로, 여기서는 userId 매핑만 만든다.
 */
@Component
class GetAlarmFromTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetAlarmFromTeamDao {

	override fun findByTeamIds(teamIds: Set<Long>): AlarmTeamMembers {
		if (teamIds.isEmpty()) return AlarmTeamMembers.empty()

		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
		val rows: List<AlarmTeamMemberRow> = queryFactory
			.select(
				Projections.constructor(
					AlarmTeamMemberRow::class.java,
					teamMember.teamId,
					teamMember.userId,
				),
			)
			.from(teamMember)
			.where(teamMember.teamId.`in`(teamIds))
			.fetch()

		return AlarmTeamMembers(
			rows.groupBy({ row: AlarmTeamMemberRow -> row.teamId }, { row: AlarmTeamMemberRow -> row.userId }),
		)
	}
}

/** team_members 한 행(teamId, userId)을 그룹핑 전 담는 내부 투영 모델. (QueryDSL 생성자 투영 대상) */
internal data class AlarmTeamMemberRow(
	val teamId: Long,
	val userId: Long,
)
