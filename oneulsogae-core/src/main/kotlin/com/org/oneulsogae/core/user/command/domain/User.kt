package com.org.oneulsogae.core.user.command.domain

import com.org.oneulsogae.common.user.Role
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import java.time.LocalDateTime

/**
 * 사용자 도메인 모델. 인증/가입 상태 등 계정 식별에 관한 도메인 행위를 정의한다.
 * 닉네임/프로필 등 프로필 상세 정보는 [UserDetail]이 담당한다.
 * 영속성은 [com.org.oneulsogae.infra.user.command.entity.UserEntity]가 담당한다.
 */
data class User(
	val id: Long = 0,
	val provider: String,
	val providerId: String,
	val email: String? = null,
	val role: Role = Role.USER,
	val status: UserStatus = UserStatus.ONBOARDING,
	val lastLoginAt: LocalDateTime? = null,
	/** 내가 남에게 공유하는 추천 코드. 조회 시점 lazy 발급이라 발급 전엔 null. */
	val referralCode: String? = null,
	/** 나를 추천한(내가 가입 시 코드를 입력한) 추천인 id. 추천 없이 가입하면 null. */
	val referredByUserId: Long? = null,
) {

	/** 온보딩(프로필 입력)을 마치고 정식 가입을 완료한다. (-> ACTIVE) */
	fun completeSignUp(): User =
		copy(status = UserStatus.ACTIVE)

	/** 아직 추가 정보 입력 전(ONBOARDING) 상태인지 여부. */
	val isOnboarding: Boolean
		get() = status == UserStatus.ONBOARDING

	/** 본인확인(KCP)을 시작할 수 있는 상태(ONBOARDING)인지 검증한다. 이미 온보딩을 지난 사용자면 예외를 던진다. */
	fun validateCanStartIdentityVerification() {
		if (!isOnboarding) {
			throw BusinessException(UserErrorCode.IDENTITY_VERIFICATION_NOT_ONBOARDING)
		}
	}

	val isRegistered: Boolean
		get() = status.isRegistered()

	/** 정식 가입(ACTIVE) 상태가 아니면 예외를 던진다. (정식 가입을 전제로 하는 기능의 사전 검증) */
	fun validateRegistered() {
		if (!isRegistered) {
			throw BusinessException(UserErrorCode.USER_NOT_ACTIVE)
		}
	}

	/** 마지막 로그인 시점을 기록한다. */
	fun recordLogin(at: LocalDateTime): User =
		copy(lastLoginAt = at)

	/** 추천 코드를 발급(부여)한다. */
	fun assignReferralCode(code: String): User =
		copy(referralCode = code)

	/** 나를 추천한 추천인을 기록한다. */
	fun referredBy(referrerId: Long): User =
		copy(referredByUserId = referrerId)

	/** 이 유저(추천인)가 해당 신규 유저를 추천해 보상받을 수 있는지 판정한다. (정식 가입 상태 + 본인 아님) */
	fun canRefer(newUserId: Long): Boolean =
		isRegistered && id != newUserId

	companion object {

		/** OAuth 인증 직후의 신규 사용자를 생성한다. (status ONBOARDING) */
		fun create(provider: String, providerId: String, email: String?): User =
			User(
				provider = provider,
				providerId = providerId,
				email = email,
				status = UserStatus.ONBOARDING,
			)
	}
}
