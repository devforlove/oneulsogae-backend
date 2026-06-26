package com.org.meeple.scheduler.match.query.dto

import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 팀 매칭 후보가 되는 ACTIVE(결성) 팀 read model.
 * 풀 버킷 키(팀 성별·활동권역)와, 같은 권역 내 정렬 기준이 되는 팀 최신 로그인 시각을 담는다.
 * (lastLoginAt = 그 팀 ACTIVE 구성원 중 가장 최근 로그인 시각 — "구성원 한 명이라도 최근 로그인" 판단/정렬에 쓴다)
 */
data class MatchableTeam(
	val teamId: Long,
	val gender: Gender,
	val regionId: Long,
	val lastLoginAt: LocalDateTime,
)
