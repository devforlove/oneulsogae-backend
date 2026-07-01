package com.org.meeple.core.solomatch.query.dao

import com.org.meeple.common.user.Gender
import com.org.meeple.core.solomatch.query.dto.ExtraIntroCandidate
import com.org.meeple.core.solomatch.query.dto.ExtraIntroScoringRow
import com.org.meeple.matching.MatchScoringProfile
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 추가 소개 자격 후보 조회 dao. 자격 = 반대 성별 · 최근 로그인 · 재소개 이력 없음 · 매칭 가능(match_user 존재).
 * 전체 수 계산·정렬은 경량 행([findScoringRows])으로, 표시 프로필은 상위 N명만([findDisplayProfiles]) 적재한다.
 */
interface GetExtraIntroCandidateDao {

	/** 요청자([requesterId])의 자격 후보 경량 스코어링 행 전체. (재소개 이력 있는 후보 제외) */
	fun findScoringRows(requesterId: Long, partnerGender: Gender, loginAfter: LocalDateTime): List<ExtraIntroScoringRow>

	/** 요청자 자신의 스코어링 프로필. (양방향 이상형 부합 계산용, [today] 기준 나이) */
	fun findRequesterProfile(requesterId: Long, today: LocalDate): MatchScoringProfile?

	/** 주어진 userId들의 표시 프로필. (호출부가 점수순 정렬을 유지) */
	fun findDisplayProfiles(userIds: List<Long>): List<ExtraIntroCandidate>
}
