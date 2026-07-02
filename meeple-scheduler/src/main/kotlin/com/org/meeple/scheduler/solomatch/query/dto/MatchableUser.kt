package com.org.meeple.scheduler.solomatch.query.dto

import com.org.meeple.common.user.Gender
import com.org.meeple.common.match.ScoringCandidate
import java.time.LocalDateTime

/**
 * 일일 배치의 대상이자 후보가 되는 활성 매칭 유저 read model.
 * 버킷 키(성별·지역)와 후보 정렬(최근 로그인)에 쓰므로 모두 non-null이다. (회사명만 미인증 시 null)
 */
data class MatchableUser(
	override val userId: Long,
	val gender: Gender,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
	/** 회사명. 같은 회사 소개 차단 판정에 쓴다. 미인증이면 null. */
	override val companyName: String?,
	/** 같은 회사 소개 거부 여부. */
	override val refuseSameCompanyIntro: Boolean,
) : ScoringCandidate
