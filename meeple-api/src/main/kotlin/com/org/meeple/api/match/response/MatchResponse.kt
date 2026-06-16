package com.org.meeple.api.match.response

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.MatchWithPartner
import java.time.LocalDateTime

/**
 * 매칭 응답/목록 조회 결과. 매칭 상태와 상대방 프로필을 한 단계로(중첩 없이) 담는다.
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
	val traits: List<String>,
	val interests: List<String>,
	val maritalStatus: String?,
	val smokingStatus: String?,
	val religion: String?,
	val drinkingStatus: String?,
	val bodyType: String?,
) {
	companion object {
		fun of(matchWithPartner: MatchWithPartner): MatchResponse =
			MatchResponse(
				matchId = matchWithPartner.matchId,
				status = matchWithPartner.status,
				expiresAt = matchWithPartner.expiresAt,
				datingInitAmount = matchWithPartner.datingInitAmount,
				datingAcceptAmount = matchWithPartner.datingAcceptAmount,
				hasUserInterest = matchWithPartner.hasUserInterest,
				hasPartnerInterest = matchWithPartner.hasPartnerInterest,
				userId = matchWithPartner.partnerUserId,
				nickname = matchWithPartner.nickname,
				profileImageCode = matchWithPartner.profileImageCode,
				age = matchWithPartner.age,
				height = matchWithPartner.height,
				gender = matchWithPartner.gender,
				job = matchWithPartner.job,
				activityArea = matchWithPartner.activityArea,
				introduction = matchWithPartner.introduction,
				companyName = matchWithPartner.companyName,
				traits = matchWithPartner.traits,
				interests = matchWithPartner.interests,
				maritalStatus = matchWithPartner.maritalStatus?.description,
				smokingStatus = matchWithPartner.smokingStatus?.description,
				religion = matchWithPartner.religion?.description,
				drinkingStatus = matchWithPartner.drinkingStatus?.description,
				bodyType = matchWithPartner.bodyType?.description,
			)
	}
}
