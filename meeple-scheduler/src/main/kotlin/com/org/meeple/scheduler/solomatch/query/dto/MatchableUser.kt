package com.org.meeple.scheduler.solomatch.query.dto

import com.org.meeple.common.user.Gender
import com.org.meeple.common.match.ScoringCandidate
import java.time.LocalDateTime

/**
 * 일일 배치의 대상이자 후보가 되는 활성 매칭 유저 read model.
 * 버킷 키(성별·지역)와 후보 정렬(최근 로그인)에 쓰므로 모두 non-null이다.
 */
data class MatchableUser(
	override val userId: Long,
	val gender: Gender,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
) : ScoringCandidate
