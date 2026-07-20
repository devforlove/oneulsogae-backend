package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.application.port.`in`.RegisterUserUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.WithdrawUserUseCase
import com.org.oneulsogae.core.user.command.domain.User
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

/**
 * 탈퇴 후 같은 provider/providerId로 재로그인(registerIfAbsent) 시 계정이 복구되는지 검증한다.
 * (OAuth 결선은 CustomOAuth2UserService → registerIfAbsent로 동일하므로 유스케이스 레벨로 검증한다)
 */
class RestoreAccountOnLoginE2ETest : AbstractIntegrationSupport() {

	@Autowired private lateinit var registerUserUseCase: RegisterUserUseCase
	@Autowired private lateinit var withdrawUserUseCase: WithdrawUserUseCase

	init {
		describe("탈퇴 후 같은 소셜로 재로그인") {

			it("기존 계정이 복구된다 (같은 id, deleted_at 해제, status 보존)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(
						provider = "kakao",
						providerId = "kakao-123",
						email = "u@test.com",
						status = UserStatus.ACTIVE,
					),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))

				withdrawUserUseCase.withdraw(userId)
				activeUserCountOf(userId) shouldBe 0   // 소프트삭제됨

				val restored: User = registerUserUseCase.registerIfAbsent("kakao", "kakao-123", "u@test.com", null)

				restored.id shouldBe userId            // 새 계정이 아니라 복구
				restored.status shouldBe UserStatus.ACTIVE
				activeUserCountOf(userId) shouldBe 1   // 다시 조회 가능
			}
		}

		afterTest {
			IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
			IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
			IntegrationUtil.deleteAll(QUserEntity.userEntity)
		}
	}
}

private fun activeUserCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetch().size
