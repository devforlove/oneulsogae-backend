package com.org.meeple.core.fixture

import com.org.meeple.common.user.Role
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.user.command.domain.User
import java.time.LocalDateTime

/** [User] 도메인 모델 테스트 픽스처. 기본은 정식 가입(ACTIVE) 계정이다. */
object UserFixture {

	fun create(
		id: Long = 0,
		provider: String = "kakao",
		providerId: String = "test-provider-id",
		email: String? = "user@test.com",
		role: Role = Role.USER,
		status: UserStatus = UserStatus.ACTIVE,
		lastLoginAt: LocalDateTime? = null,
	): User =
		User(
			id = id,
			provider = provider,
			providerId = providerId,
			email = email,
			role = role,
			status = status,
			lastLoginAt = lastLoginAt,
		)
}
