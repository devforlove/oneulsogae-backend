package com.org.meeple.core.match.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.match.application.MatchErrorCode
import com.org.meeple.core.user.domain.UserDetail
import com.org.meeple.core.user.domain.UserWithDetail

/**
 * 매칭에 필요한 필드가 모두 채워졌음을 타입으로 보장하는 프로필 프로젝션.
 * [from]에서 한 번 검증하므로, 이 타입을 받는 매칭 로직은 null 검사 없이 사용할 수 있다.
 * (저장 모델 [UserDetail]은 온보딩 중 점진적으로 채워지므로 필드가 nullable이다)
 */
data class MatchableProfile(
	val userId: Long,
	val gender: Gender,
	val nickname: String,
	val age: Int,
	val regionCode: Int,
	val maritalStatus: MaritalStatus,
) {

	/** 매칭 상대로 찾을 후보의 성별. (반대 성별) */
	fun partnerGender(): Gender =
		gender.opposite()

	companion object {

		/**
		 * 사용자([UserWithDetail])가 매칭 가능한지 검증해 프로필로 변환한다.
		 * 정식 가입(ACTIVE)이 아니면 [com.org.meeple.core.user.application.UserErrorCode.USER_NOT_ACTIVE],
		 * 매칭 필수 필드(성별·닉네임·나이·활동 권역·결혼 여부)가 비어 있으면 [MatchErrorCode.PROFILE_INCOMPLETE]를 던진다.
		 */
		fun from(userWithDetail: UserWithDetail): MatchableProfile {
			userWithDetail.user.validateRegistered()

			val detail: UserDetail = userWithDetail.detail
			return MatchableProfile(
				userId = detail.userId,
				gender = detail.gender ?: throw BusinessException(MatchErrorCode.PROFILE_INCOMPLETE),
				nickname = detail.nickname ?: throw BusinessException(MatchErrorCode.PROFILE_INCOMPLETE),
				age = detail.age ?: throw BusinessException(MatchErrorCode.PROFILE_INCOMPLETE),
				regionCode = detail.regionCode ?: throw BusinessException(MatchErrorCode.PROFILE_INCOMPLETE),
				maritalStatus = detail.maritalStatus ?: throw BusinessException(MatchErrorCode.PROFILE_INCOMPLETE),
			)
		}
	}
}
