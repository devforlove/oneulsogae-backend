package com.org.oneulsogae.api.match.response

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.ageAt
import com.org.oneulsogae.core.solomatch.query.dto.ExtraIntroCandidate
import com.org.oneulsogae.core.solomatch.query.dto.ExtraIntroCandidates
import java.time.LocalDate

/**
 * 추가 소개 후보 목록 응답.
 * [totalCount]=전체 자격 후보 수, [coinCost]=추가 소개 1회에 필요한 코인 비용(서버 고정), [candidates]=점수 상위 표시 목록.
 */
data class ExtraIntroCandidatesResponse(
	val totalCount: Int,
	val coinCost: Int,
	val candidates: List<ExtraIntroCandidateResponse>,
) {
	companion object {
		fun of(result: ExtraIntroCandidates, today: LocalDate): ExtraIntroCandidatesResponse =
			ExtraIntroCandidatesResponse(
				totalCount = result.totalCount,
				coinCost = CoinUsageType.EXTRA_INTRO.coinAmount,
				candidates = result.candidates.map { c: ExtraIntroCandidate -> ExtraIntroCandidateResponse.of(c, today) },
			)
	}
}

/**
 * 추가 소개 후보 프로필. ([ExtraIntroCandidatesResponse]의 `candidates` 항목)
 * gender는 enum(name)으로, 나머지 프로필 enum은 사람이 읽을 수 있는 description(한글)으로 내려보낸다. (PartnerResponse와 동일 관례)
 */
data class ExtraIntroCandidateResponse(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val age: Int?,
	val height: Int?,
	val gender: Gender?,
	val job: String?,
	val activityArea: String?,
	val introduction: String?,
	val companyName: String?,
	val universityName: String?,
	val traits: List<String>,
	val interests: List<String>,
	val maritalStatus: String?,
	val smokingStatus: String?,
	val religion: String?,
	val drinkingStatus: String?,
	val bodyType: String?,
) {
	companion object {
		fun of(c: ExtraIntroCandidate, today: LocalDate): ExtraIntroCandidateResponse =
			ExtraIntroCandidateResponse(
				userId = c.userId,
				nickname = c.nickname,
				profileImageCode = c.profileImageCode,
				age = c.birthday?.ageAt(today),
				height = c.height,
				gender = c.gender,
				job = c.job,
				activityArea = c.activityArea,
				introduction = c.introduction,
				companyName = c.companyName,
				universityName = c.universityName,
				traits = c.traits,
				interests = c.interests,
				maritalStatus = c.maritalStatus?.description,
				smokingStatus = c.smokingStatus?.description,
				religion = c.religion?.description,
				drinkingStatus = c.drinkingStatus?.description,
				bodyType = c.bodyType?.description,
			)
	}
}
