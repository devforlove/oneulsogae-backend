package com.org.meeple.api.match.response

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.domain.MatchWithPartner
import com.org.meeple.core.user.domain.UserDetail
import java.time.LocalDateTime

/**
 * 매칭 응답/목록 조회 결과. 매칭 상태와 상대방 프로필을 한 단계로(중첩 없이) 담는다.
 * 상대 [UserDetail]의 노출 가능한 데이터만 포함한다. (연락처/회사 이메일 등 민감 정보는 제외)
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
		fun of(matchWithPartner: MatchWithPartner): MatchResponse {
			val partner: UserDetail = matchWithPartner.partner
			return MatchResponse(
				matchId = matchWithPartner.match.id,
				status = matchWithPartner.match.status,
				expiresAt = matchWithPartner.match.expiresAt,
				datingInitAmount = matchWithPartner.match.datingInitAmount,
				datingAcceptAmount = matchWithPartner.match.datingAcceptAmount,
				hasUserInterest = matchWithPartner.hasUserInterest,
				hasPartnerInterest = matchWithPartner.hasPartnerInterest,
				userId = partner.userId,
				nickname = partner.nickname,
				profileImageCode = partner.profileImageCode,
				age = partner.age,
				height = partner.height,
				gender = partner.gender,
				job = partner.job,
				activityArea = partner.activityArea,
				introduction = partner.introduction,
				companyName = partner.companyName,
				traits = partner.traits,
				interests = partner.interests,
				maritalStatus = partner.maritalStatus?.description,
				smokingStatus = partner.smokingStatus?.description,
				religion = partner.religion?.description,
				drinkingStatus = partner.drinkingStatus?.description,
				bodyType = partner.bodyType?.description,
			)
		}
	}
}
