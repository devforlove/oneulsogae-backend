package com.org.oneulsogae.core.fixture

import com.org.oneulsogae.common.user.Role
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.domain.User
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
