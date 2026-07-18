package com.org.meeple.core.user.command.domain

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.MatchProfileSnapshot
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.user.UserErrorCode
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 사용자 프로필 상세 도메인 모델. [com.org.meeple.core.user.command.domain.User]와 1:1로 연결되며 닉네임/프로필 이미지 및
 * 가입 과정에서 점진적으로 채워지는 프로필 정보(나이/키/성별 등)를 담는다.
 * 사용자 프로필 속성 묶음으로, user 도메인이 소유한다.
 * 영속성은 [com.org.meeple.infra.user.command.entity.UserDetailEntity]가 담당한다.
 */
data class UserDetail(
	val id: Long = 0,
	val userId: Long,
	val nickname: String? = null,
	val profileImageCode: String? = null,
	val birthday: LocalDate? = null,
	val height: Int? = null,
	val gender: Gender? = null,
	val phoneNumber: String? = null,
	val job: String? = null,
	/** 직종. 멤버 인증 승인 시 어드민이 확정한다. */
	val jobCategory: String? = null,
	/** 직장명/직종/직급 상세. 멤버 인증 승인 시 어드민이 확정한다. */
	val jobDetail: String? = null,
	/** 활동지역 id(regions FK). 서비스가 regionId로 받아 채운다. (표시용 지역명은 응답 시 regions join으로 내려준다) */
	val regionId: Long? = null,
	val introduction: String? = null,
	val traits: List<String> = emptyList(),
	val interests: List<String> = emptyList(),
	val companyEmail: String? = null,
	val companyName: String? = null,
	/** 학교 이메일. 선택적 학교 인증을 완료한 사용자만 채워진다. */
	val universityEmail: String? = null,
	/** 학교명. 학교 이메일 인증 완료 시 도메인 매핑으로 채워진다(매핑이 없으면 null). */
	val universityName: String? = null,
	/** 보조 이메일. 마케팅·광고·매칭 알림 수신용으로 사용자가 선택적으로 설정한다. (미설정 시 null) */
	val secondaryEmail: String? = null,
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
	 * 마케팅·광고·매칭 알림 수신용 보조 이메일을 설정/변경/해제한다.
	 * null이나 공백 문자열은 해제로 간주해 null로 정규화한다. (형식 검증은 요청 경계에서 수행)
	 */
	fun changeSecondaryEmail(secondaryEmail: String?): UserDetail =
		copy(secondaryEmail = secondaryEmail?.takeIf { it.isNotBlank() })

	/**
	 * 온보딩 입력값으로 편집 가능 필드를 교체한다.
	 * - regionId는 서비스가 region 도메인에서 해석해 넘긴다. (활동지역 FK)
	 * - profileImageCode는 아직 없으면 서버가 랜덤 배정하고, 이미 있으면 기존 값을 유지한다.
	 * - id/userId/companyEmail/companyName은 보존한다. (회사 이메일/회사명은 온보딩과 분리된 회사 인증 플로우에서만 채워진다)
	 * - 정식 가입(ACTIVE)으로 이어지는 입력이므로, 매칭 풀에 필요한 성별·활동지역을 [validateMatchProfile]로 강제한다.
	 */
	fun initProfile(
		nickname: String?,
		birthday: LocalDate?,
		height: Int?,
		gender: Gender?,
		phoneNumber: String?,
		job: String?,
		regionId: Long?,
		introduction: String?,
		traits: List<String>,
		interests: List<String>,
		maritalStatus: MaritalStatus?,
		smokingStatus: SmokingStatus?,
		religion: Religion?,
		drinkingStatus: DrinkingStatus?,
		bodyType: BodyType?,
		today: LocalDate,
	): UserDetail {
		val updated: UserDetail = copy(
			nickname = nickname,
			birthday = birthday,
			height = height,
			gender = gender,
			phoneNumber = phoneNumber,
			job = job,
			regionId = regionId,
			introduction = introduction,
			traits = traits,
			interests = interests,
			maritalStatus = maritalStatus,
			smokingStatus = smokingStatus,
			religion = religion,
			drinkingStatus = drinkingStatus,
			bodyType = bodyType,
		).assignProfileImageCodeIfAbsent()
		updated.validateBirthday(today)
		updated.validateMatchProfile()
		return updated
	}

	/**
	 * 가입 이후 사용자가 자유롭게 바꿀 수 있는 프로필 필드만 교체한다.
	 * - regionId는 서비스가 region 도메인에서 해석해 넘긴다.
	 * - profileImageCode는 사용자가 선택한 값으로 교체한다.
	 * - 나이/성별/키/휴대폰번호/회사이메일/회사명과 id/userId는 보존한다.
	 * - 편집으로 활동지역이 비워지지 않도록 [validateMatchProfile]로 성별·활동지역을 강제한다. (성별은 보존되므로 사실상 지역을 지킨다)
	 */
	fun editProfile(
		nickname: String?,
		profileImageCode: String?,
		job: String?,
		regionId: Long?,
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
			regionId = regionId,
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
	 * 생년월일이 채워졌고 만 나이가 가입 허용 범위(만 19~100세)인지 검증한다. (온보딩 프로필 입력 시 호출)
	 * 미래 날짜는 만 나이가 19 미만이 되어 함께 걸러진다.
	 */
	private fun validateBirthday(today: LocalDate) {
		val birthday: LocalDate = birthday ?: throw BusinessException(UserErrorCode.BIRTHDAY_REQUIRED)
		val age: Int = birthday.ageAt(today)
		if (age < MIN_AGE || age > MAX_AGE) {
			throw BusinessException(UserErrorCode.INVALID_BIRTHDAY)
		}
	}

	/** 표시용 만 나이. 생년월일이 없으면 null. (응답 렌더링 시 기준일을 넘겨 계산한다) */
	fun age(today: LocalDate): Int? = birthday?.ageAt(today)

	/**
	 * 매칭 풀 적재에 필요한 성별·활동지역이 채워졌는지 검증한다. (정식 가입 경로의 프로필 입력·편집 시 호출)
	 * 이 검증을 통과한 프로필만 ACTIVE로 이어지므로, ACTIVE 사용자는 성별·활동지역이 항상 채워져 있음을 보장한다.
	 * (이 불변식 덕분에 매칭 풀 조회는 성별·지역 null 필터 없이 도메인 모델로 직접 투영할 수 있다)
	 */
	private fun validateMatchProfile() {
		if (gender == null) {
			throw BusinessException(UserErrorCode.GENDER_REQUIRED)
		}
		if (regionId == null) {
			throw BusinessException(UserErrorCode.REGION_NOT_RESOLVED)
		}
	}

	/**
	 * 아직 프로필 이미지 코드가 배정되지 않았다면 새 코드를 배정한다. (이미 있으면 기존 코드를 유지)
	 * 유저가 직접 등록하지 않고, 서버가 배정한 코드에 따라 프로필이 정해진다.
	 */
	fun assignProfileImageCodeIfAbsent(): UserDetail =
		if (profileImageCode == null) copy(profileImageCode = generateProfileImageCode()) else this

	/**
	 * 매칭에 필요한 기준 필드가 모두 채워졌으면 [MatchProfileSnapshot]을, 하나라도 비어 있으면 null을 반환한다.
	 * 매칭 대상 상태([status].isMatchable)가 아니거나 [lastLoginAt]이 없으면 매칭 불가로 보고 null을 반환한다.
	 * 매칭 가능 여부 판단(완성도 규칙)을 한곳에 캡슐화해, user 도메인이 match 읽기 모델 동기화용 스냅샷을 만들 때 쓴다.
	 */
	fun matchProfileSnapshotOrNull(status: UserStatus, lastLoginAt: LocalDateTime?): MatchProfileSnapshot? {
		if (!status.isMatchable()) return null
		return MatchProfileSnapshot(
			gender = gender ?: return null,
			birthday = birthday ?: return null,
			regionId = regionId ?: return null,
			maritalStatus = maritalStatus ?: return null,
			nickname = nickname ?: return null,
			profileImageCode = profileImageCode ?: return null,
			lastLoginAt = lastLoginAt ?: return null,
			// 회사명은 선택 필드라 매칭 가능 판정에 관여하지 않는다. (같은 회사 소개 차단 판정용)
			companyName = companyName,
		)
	}

	companion object {

		/** 배정 가능한 프로필 이미지 코드 개수. (0 ~ 이 값 - 1) */
		private const val PROFILE_IMAGE_CODE_COUNT: Int = 30

		/** 가입 허용 만 나이 하한/상한. */
		private const val MIN_AGE: Int = 19
		private const val MAX_AGE: Int = 100

		private val secureRandom: SecureRandom = SecureRandom()

		/** [com.org.meeple.core.user.command.domain.User]에 연결되는 신규 프로필 상세를 생성한다. */
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
