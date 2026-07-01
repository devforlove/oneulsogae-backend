package com.org.meeple.core.solomatch.command.application.port.out

import com.org.meeple.common.user.Gender
import com.org.meeple.matching.MatchScoringProfile
import com.org.meeple.matching.ScoringCandidate
import java.time.LocalDate
import java.time.LocalDateTime

/** 추가 소개 명령용 후보 행. (조회 경로와 별개로 command가 자체 소유 — CQRS) */
data class ExtraIntroCandidateRow(
	override val userId: Long,
	override val regionId: Long,
	override val lastLoginAt: LocalDateTime,
	val profile: MatchScoringProfile?,
) : ScoringCandidate

/**
 * 추가 소개 후보 조회 out-port. 자격 = 반대 성별 · 최근 로그인 · 매칭 가능(match_user 존재).
 * 재소개 제외는 선택 단계에서 [existsIntroduced]로 판정한다. (배치의 existsByPair와 동일한 memberKey 기준)
 */
interface GetExtraIntroCandidatePort {
	fun findCandidates(requesterId: Long, partnerGender: Gender, loginAfter: LocalDateTime): List<ExtraIntroCandidateRow>
	fun findRequesterProfile(requesterId: Long, today: LocalDate): MatchScoringProfile?
	fun existsIntroduced(requesterId: Long, candidateId: Long): Boolean
}
