package com.org.oneulsogae.core.alarm.query.dao

import com.org.oneulsogae.core.alarm.query.dto.AlarmTeamMembers

/**
 * 알람을 유발한 팀의 구성원 조회 dao(query out-port 인터페이스). (조회 전용 read model 반환)
 * fromTeamId가 있는 알람의 froms를 그 팀 구성원으로 채우기 위해, 팀 id 집합으로 구성원 userId들을 한 번에 가져온다.
 * 실제 구현(infra 읽기 dao)이 team_members를 조회해 (teamId → userId 목록) 매핑으로 투영한다.
 */
interface GetAlarmFromTeamDao {

	/** [teamIds]에 해당하는 팀들의 (삭제되지 않은) 구성원 userId를 IN 조회로 한 번에 가져온다. (없으면 빈 [AlarmTeamMembers]) */
	fun findByTeamIds(teamIds: Set<Long>): AlarmTeamMembers
}
