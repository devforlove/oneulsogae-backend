package com.org.meeple.core.solomatch.query.dao

import com.org.meeple.common.user.Gender
import com.org.meeple.core.solomatch.query.dto.ExtraIntroCandidate
import java.time.LocalDateTime

/**
 * 추가 소개 자격 후보 조회 dao. 자격 = 반대 성별 · 최근 로그인 · 재소개 이력 없음 · 매칭 가능(match_user 존재).
 * 목록은 노출 시 마스킹되므로 스코어링·정렬 없이 자격 후보 id를 셔플해 노출한다.
 * 전체 수는 자격 후보 id 수([findEligibleCandidateIds])로 세고, 표시 프로필은 선택된 소수만([findDisplayProfiles]) 적재한다.
 */
interface GetExtraIntroCandidateDao {

	/** 요청자([requesterId])의 자격 후보 userId 전체. (재소개 이력 있는 후보 제외) */
	fun findEligibleCandidateIds(requesterId: Long, partnerGender: Gender, loginAfter: LocalDateTime): List<Long>

	/** 주어진 userId들의 표시 프로필. */
	fun findDisplayProfiles(userIds: List<Long>): List<ExtraIntroCandidate>
}
