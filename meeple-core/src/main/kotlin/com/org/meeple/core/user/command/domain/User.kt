package com.org.meeple.core.user.command.domain

import com.org.meeple.common.user.Role
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import java.time.LocalDateTime

/**
 * 사용자 도메인 모델. 인증/가입 상태 등 계정 식별에 관한 도메인 행위를 정의한다.
 * 닉네임/프로필 등 프로필 상세 정보는 [UserDetail]이 담당한다.
 * 영속성은 [com.org.meeple.infra.user.command.entity.UserEntity]가 담당한다.
 */
data class User(
	val id: Long = 0,
	val provider: String,
	val providerId: String,
	val email: String? = null,
	val role: Role = Role.USER,
	val status: UserStatus = UserStatus.ONBOARDING,
	val lastLoginAt: LocalDateTime? = null,
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
