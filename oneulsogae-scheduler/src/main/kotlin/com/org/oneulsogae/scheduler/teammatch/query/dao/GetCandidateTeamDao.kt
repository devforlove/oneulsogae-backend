package com.org.oneulsogae.scheduler.teammatch.query.dao

import com.org.oneulsogae.scheduler.teammatch.query.dto.CandidateTeam

/**
 * 추천 후보 팀 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * 결성(ACTIVE) 팀 전체를 teamId·성별·활동권역으로 반환한다. (인메모리 TeamPool 구성용)
 */
interface GetCandidateTeamDao {

    fun findCandidateTeams(): List<CandidateTeam>
}
