package com.org.meeple.core.user.command.domain

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Region
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.application.UserErrorCode
import java.security.SecureRandom

/**
 * 사용자 프로필 상세 도메인 모델. [com.org.meeple.core.user.domain.User]와 1:1로 연결되며 닉네임/프로필 이미지 및
 * 가입 과정에서 점진적으로 채워지는 프로필 정보(나이/키/성별 등)를 담는다.
 * 사용자 프로필 속성 묶음으로, user 도메인이 소유한다.
 * 영속성은 [com.org.meeple.infra.user.entity.UserDetailEntity]가 담당한다.
 */
data class UserDetail(
	val id: Long = 0,
	val userId: Long,
	val nickname: String? = null,
	val profileImageCode: String? = null,
	val age: Int? = null,
	val height: Int? = null,
	val gender: Gender? = null,
	val phoneNumber: String? = null,
	val job: String? = null,
	val activityArea: String? = null,
	val regionCode: Int? = null,
	val introduction: String? = null,
	val traits: List<String> = emptyList(),
	val interests: List<String> = emptyList(),
	val companyEmail: String? = null,
	val companyName: String? = null,
	val maritalStatus: MaritalStatus? = null,
	val smokingStatus: SmokingStatus? = null,
	val religion: Religion? = null,
	val drinkingStatus: DrinkingStatus? = null,
	val bodyType: BodyType? = null,
) {

	/** 사용자가 직접 닉네임을 설정/변경한다. */
	fun changeNickname(nickname: String): UserDetail =
		copy(nickname = nickname)

	/**
	 * 온보딩 입력값으로 편집 가능 필드와 회사 이메일을 교체한다.
	 * - regionCode는 [activityArea]로부터 서버가 산출한다.
	 * - profileImageCode는 아직 없으면 서버가 랜덤 배정하고, 이미 있으면 기존 값을 유지한다.
	 * - id/userId/companyName은 보존한다. (companyName은 회사 이메일 인증 완료 시점에만 채워진다)
	 * - 정식 가입(ACTIVE)으로 이어지는 입력이므로, 매칭 풀에 필요한 성별·활동권역을 [validateMatchProfile]로 강제한다.
	 */
	fun initProfile(
		nickname: String?,
		age: Int?,
		height: Int?,
		gender: Gender?,
		phoneNumber: String?,
		job: String?,
		activityArea: String?,
		introduction: String?,
		traits: List<String>,
		interests: List<String>,
		companyEmail: String,
		maritalStatus: MaritalStatus?,
		smokingStatus: SmokingStatus?,
		religion: Religion?,
		drinkingStatus: DrinkingStatus?,
		bodyType: BodyType?,
	): UserDetail {
		val updated: UserDetail = copy(
			nickname = nickname,
			age = age,
			height = height,
			gender = gender,
			phoneNumber = phoneNumber,
			job = job,
			activityArea = activityArea,
			regionCode = Region.resolveAreaCode(activityArea),
			introduction = introduction,
			traits = traits,
			interests = interests,
			companyEmail = companyEmail,
			maritalStatus = maritalStatus,
			smokingStatus = smokingStatus,
			religion = religion,
			drinkingStatus = drinkingStatus,
			bodyType = bodyType,
		).assignProfileImageCodeIfAbsent()
		updated.validateMatchProfile()
		return updated
	}

	/**
	 * 가입 이후 사용자가 자유롭게 바꿀 수 있는 프로필 필드만 교체한다.
	 * - regionCode는 [activityArea]로부터 서버가 산출한다.
	 * - profileImageCode는 사용자가 선택한 값으로 교체한다.
	 * - 나이/성별/키/휴대폰번호/회사이메일/회사명과 id/userId는 보존한다.
	 * - 편집으로 활동권역이 비워지지 않도록 [validateMatchProfile]로 성별·활동권역을 강제한다. (성별은 보존되므로 사실상 권역을 지킨다)
	 */
	fun editProfile(
		nickname: String?,
		profileImageCode: String?,
		job: String?,
		activityArea: String?,
		introduction: String?,
		traits: List<String>,
		interests: List<String>,
		maritalStatus: MaritalStatus?,
		smokingStatus: SmokingStatus?,
		religion: Religion?,
		drinkingStatus: DrinkingStatus?,
		bodyType: BodyType?,
	): UserDetail {
		val updated: UserDetail = copy(
			nickname = nickname,
			profileImageCode = profileImageCode,
			job = job,
			activityArea = activityArea,
			regionCode = Region.resolveAreaCode(activityArea),
			introduction = introduction,
			traits = traits,
			interests = interests,
			maritalStatus = maritalStatus,
			smokingStatus = smokingStatus,
			religion = religion,
			drinkingStatus = drinkingStatus,
			bodyType = bodyType,
		)
		updated.validateMatchProfile()
		return updated
	}

	/**
	 * 매칭 풀 적재에 필요한 성별·활동권역이 채워졌는지 검증한다. (정식 가입 경로의 프로필 입력·편집 시 호출)
	 * 활동지역이 지원 지역(시/도)과 매칭되지 않으면 regionCode가 null이 되므로 여기서 함께 걸러진다.
	 * 이 검증을 통과한 프로필만 ACTIVE로 이어지므로, ACTIVE 사용자는 성별·권역이 항상 채워져 있음을 보장한다.
	 * (이 불변식 덕분에 매칭 풀 조회는 성별·권역 null 필터 없이 도메인 모델로 직접 투영할 수 있다)
	 */
	private fun validateMatchProfile() {
		if (gender == null) {
			throw BusinessException(UserErrorCode.GENDER_REQUIRED)
		}
		if (regionCode == null) {
			throw BusinessException(UserErrorCode.REGION_NOT_RESOLVED)
		}
	}

	/**
	 * 아직 프로필 이미지 코드가 배정되지 않았다면 새 코드를 배정한다. (이미 있으면 기존 코드를 유지)
	 * 유저가 직접 등록하지 않고, 서버가 배정한 코드에 따라 프로필이 정해진다.
	 */
	fun assignProfileImageCodeIfAbsent(): UserDetail =
		if (profileImageCode == null) copy(profileImageCode = generateProfileImageCode()) else this

	companion object {

		/** 배정 가능한 프로필 이미지 코드 개수. (0 ~ 이 값 - 1) */
		private const val PROFILE_IMAGE_CODE_COUNT: Int = 30

		private val secureRandom: SecureRandom = SecureRandom()

		/** [com.org.meeple.core.user.domain.User]에 연결되는 신규 프로필 상세를 생성한다. */
		fun create(userId: Long, nickname: String? = null, profileImageCode: String? = null): UserDetail =
			UserDetail(
				userId = userId,
				nickname = nickname,
				profileImageCode = profileImageCode,
			)

		/** 0 ~ [PROFILE_IMAGE_CODE_COUNT] - 1 중 하나를 프로필 이미지 코드로 생성한다. */
		private fun generateProfileImageCode(): String =
			secureRandom.nextInt(PROFILE_IMAGE_CODE_COUNT).toString()
	}
}
