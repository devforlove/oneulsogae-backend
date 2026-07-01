package com.org.meeple.core.solomatch.query.dto

import com.org.meeple.matching.MatchScoringProfile
import com.org.meeple.matching.ScoringCandidate
import java.time.LocalDateTime

/** 추가 소개 자격 후보의 경량 스코어링 행. (전체 수 계산 + 정렬용, 표시 프로필은 별도 적재) */
data class ExtraIntroScoringRow(
	override val userId: Long,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
	val profile: MatchScoringProfile?,
) : ScoringCandidate
