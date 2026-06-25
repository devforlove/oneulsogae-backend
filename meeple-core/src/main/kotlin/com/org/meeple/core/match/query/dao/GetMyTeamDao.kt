package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.MyTeam

/**
 * 내 가장 최근 팀 조회 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * 요청자가 ACTIVE 구성원인 결성(ACTIVE) 또는 초대중(INVITING) 팀 중 가장 최근 1건의 (teamId, 내/친구 profileImageCode)를 반환한다. 없으면 null.
 */
interface GetMyTeamDao {

	fun findMyTeam(userId: Long): MyTeam?
}
