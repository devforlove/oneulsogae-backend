package com.org.meeple.api.match.response

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.solomatch.query.dto.MatchWithPartner
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 매칭 응답/목록 조회 결과. 매칭 상태는 최상위에, 상대방 프로필은 [PartnerResponse]로 중첩해 담는다.
 * 노출 가능한 상대 프로필 데이터만 포함한다. (연락처/회사 이메일 등 민감 정보는 제외)
 * status/gender는 enum(name)으로 내려보내고, 나머지 프로필 enum은 사람이 읽을 수 있는 description(한글)으로 내려보낸다.
 */
data class MatchResponse(
	val matchId: Long,
	val status: MatchStatus,
	val expiresAt: LocalDateTime,
	val datingInitAmount: Int,
	val datingAcceptAmount: Int,
	val hasUserInterest: Boolean,
	val hasPartnerInterest: Boolean,
	val partner: PartnerResponse,
) {
	companion object {
		fun of(matchWithPartner: MatchWithPartner, today: LocalDate): MatchResponse =
			MatchResponse(
				matchId = matchWithPartner.matchId,
				status = matchWithPartner.status,
				expiresAt = matchWithPartner.expiresAt,
				datingInitAmount = matchWithPartner.datingInitAmount,
				datingAcceptAmount = matchWithPartner.datingAcceptAmount,
				hasUserInterest = matchWithPartner.hasUserInterest,
				hasPartnerInterest = matchWithPartner.hasPartnerInterest,
				partner = PartnerResponse.of(matchWithPartner, today),
			)

		/** 매칭 목록을 응답 목록으로 변환한다. */
		fun listOf(matches: List<MatchWithPartner>, today: LocalDate): List<MatchResponse> =
			matches.map { of(it, today) }
	}
}

/**
 * 매칭 상대방의 노출 가능한 프로필 정보. ([MatchResponse]의 `partner` 필드에 중첩)
 */
data class PartnerResponse(
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
	val lastLoginAt: LocalDateTime?,
) {
	companion object {
		fun of(matchWithPartner: MatchWithPartner, today: LocalDate): PartnerResponse =
			PartnerResponse(
				userId = matchWithPartner.partnerUserId,
				nickname = matchWithPartner.nickname,
				profileImageCode = matchWithPartner.profileImageCode,
				age = matchWithPartner.birthday?.ageAt(today),
				height = matchWithPartner.height,
				gender = matchWithPartner.gender,
				job = matchWithPartner.job,
				activityArea = matchWithPartner.activityArea,
				introduction = matchWithPartner.introduction,
				companyName = matchWithPartner.companyName,
				universityName = matchWithPartner.universityName,
				traits = matchWithPartner.traits,
				interests = matchWithPartner.interests,
				maritalStatus = matchWithPartner.maritalStatus?.description,
				smokingStatus = matchWithPartner.smokingStatus?.description,
				religion = matchWithPartner.religion?.description,
				drinkingStatus = matchWithPartner.drinkingStatus?.description,
				bodyType = matchWithPartner.bodyType?.description,
				lastLoginAt = matchWithPartner.lastLoginAt,
			)
	}
}
