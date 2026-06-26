package com.org.meeple.scheduler.match.query.dao

import com.org.meeple.scheduler.match.query.dto.MatchableTeam
import java.time.LocalDateTime

/**
 * 팀 매칭 후보 팀 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * 결성(ACTIVE) 팀 중 ACTIVE 구성원이 한 명이라도 [loginAfter] 이후 로그인한 팀을
 * teamId·성별·활동권역·팀 최신 로그인 시각([MatchableTeam])으로 반환한다. (인메모리 TeamMatchPool 구성용)
 */
interface GetMatchableTeamDao {

	fun findMatchableTeams(loginAfter: LocalDateTime): List<MatchableTeam>
}
