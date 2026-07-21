package com.org.oneulsogae.api.match.response

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.ageAt
import com.org.oneulsogae.core.solomatch.query.dto.MatchWithPartner
import com.org.oneulsogae.core.solomatch.query.dto.MyMatches
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 내 매칭 목록 응답. 목록과 함께 요청한 사용자의 회사 인증 여부를 담는다.
 * 회사 인증을 마친 사용자만 소개 기능을 쓸 수 있어, 미인증이면 프론트엔드가 이용 제한 화면으로 분기한다.
 */
data class MatchListResponse(
	/** 요청한 사용자가 회사 인증을 마쳤는지 여부. */
	val companyVerified: Boolean,
	val matches: List<MatchResponse>,
) {
	companion object {

		fun of(myMatches: MyMatches, today: LocalDate): MatchListResponse =
			MatchListResponse(
				companyVerified = myMatches.companyVerified,
				matches = MatchResponse.listOf(myMatches.matches, today),
			)
	}
}

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
