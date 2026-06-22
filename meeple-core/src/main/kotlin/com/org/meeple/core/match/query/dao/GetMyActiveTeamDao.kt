package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.MyActiveTeam

/**
 * 내 가장 최근 결성(ACTIVE) 팀 조회 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * 요청자가 ACTIVE 구성원인 ACTIVE 팀 중 가장 최근 1건의 (teamId, 내/친구 profileImageCode)를 반환한다. 없으면 null.
 */
interface GetMyActiveTeamDao {

	fun findLatestActiveTeam(userId: Long): MyActiveTeam?
}
