package com.org.meeple.infra.fixture

import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.user.entity.UserEntity
import java.time.LocalDateTime

/**
 * [UserEntity] 테스트 픽스처. 기본은 온보딩(ONBOARDING) 단계의 계정이다.
 * PK(id)는 저장 시 생성되므로, 로그인 사용자 토큰에는 persist 후의 id를 사용한다.
 * (provider, providerId) 유니크 제약이 있어 한 테스트에서 여러 사용자를 만들면 providerId를 달리한다.
 * 매칭 배치 대상은 최근 로그인(lastLoginAt) 기준으로 추리므로, 배치 테스트에선 lastLoginAt을 최근 값으로 채운다.
 */
object UserEntityFixture {

	fun create(
		provider: String = "kakao",
		providerId: String = "test-provider-id",
		email: String? = "user@test.com",
		status: UserStatus = UserStatus.ONBOARDING,
		lastLoginAt: LocalDateTime? = null,
	): UserEntity =
		UserEntity(
			provider = provider,
			providerId = providerId,
			email = email,
			status = status,
			lastLoginAt = lastLoginAt,
		)
}
