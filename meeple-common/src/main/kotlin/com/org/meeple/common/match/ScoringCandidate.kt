package com.org.meeple.common.match

import java.time.LocalDateTime

/**
 * 종합 점수 계산에 필요한 후보의 최소 계약. (거리=지역, 최근성=마지막 로그인)
 * 배치의 [MatchableUser], 추가 소개의 후보 행이 이를 구현해 [MatchSelector]를 공유한다.
 * 회사명·같은 회사 소개 거부 플래그는 [SameCompanyIntroPolicy] 차단 판정에 쓴다.
 */
interface ScoringCandidate {
	val userId: Long
	val regionId: Long
	val lastLoginAt: LocalDateTime

	/** 후보의 회사명. 미상이면 null(같은 회사 차단 없음). */
	val companyName: String?

	/** 후보의 같은 회사 소개 거부 여부. */
	val refuseSameCompanyIntro: Boolean
}
