package com.org.meeple.scheduler.match.query.dao

import com.org.meeple.common.user.Gender

/**
 * 추천 후보 팀 1개 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * 결성(ACTIVE) + 팀원 성별이 [teamGender](요청자 반대 성별) + 팀원 중 한 명이라도 [regionCode]가 같은 팀 중 1개의 id를 반환한다.
 * 후보가 없으면 null.
 */
interface GetCandidateTeamDao {

	fun findOneCandidateTeamId(teamGender: Gender, regionCode: Int): Long?
}
