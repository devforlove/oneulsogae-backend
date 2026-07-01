package com.org.meeple.matching

import java.time.LocalDateTime

/**
 * 종합 점수 계산에 필요한 후보의 최소 계약. (거리=지역, 최근성=마지막 로그인)
 * 배치의 [MatchableUser], 추가 소개의 후보 행이 이를 구현해 [MatchSelector]를 공유한다.
 */
interface ScoringCandidate {
	val userId: Long
	val regionId: Long
	val lastLoginAt: LocalDateTime
}
