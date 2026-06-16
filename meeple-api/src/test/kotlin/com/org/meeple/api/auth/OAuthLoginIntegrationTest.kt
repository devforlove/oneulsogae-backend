package com.org.meeple.api.auth

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.service.port.`in`.RegisterUserUseCase
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

/**
 * OAuth 로그인의 사용자 식별·가입 경로([RegisterUserUseCase.registerIfAbsent]) 통합 테스트.
 *
 * 실 컨텍스트 + Testcontainers(MySQL)에서 인포트를 직접 호출한다. (배치 통합 테스트와 동일한 방식 -
 * 외부 OAuth provider 연동/세션 state가 필요한 전체 HTTP 플로우 대신, 로그인 시 사용자 식별·생성과
 * 방금 추가한 이메일 중복 체크가 실제 DB에 대해 동작하는지를 검증한다.)
 */
class OAuthLoginIntegrationTest(
	private val registerUserUseCase: RegisterUserUseCase,
) : AbstractIntegrationSupport({

	describe("registerIfAbsent (OAuth 로그인)") {

		context("처음 로그인하는 OAuth 사용자면") {
			it("신규 User를 ONBOARDING 상태로 저장하고 로그인 시점을 기록한다") {
				val user: User = registerUserUseCase.registerIfAbsent(
					provider = "google",
					providerId = "google-new",
					email = "new@test.com",
					profileImageUrl = "https://img/new.png",
				)

				user.id shouldBeGreaterThan 0
				user.provider shouldBe "google"
				user.providerId shouldBe "google-new"
				user.email shouldBe "new@test.com"
				user.status shouldBe UserStatus.ONBOARDING
				user.lastLoginAt.shouldNotBeNull()

				usersWithProviderId("google-new") shouldHaveSize 1
			}
		}

		context("이미 가입된 사용자가 다시 로그인하면") {
			it("새 계정을 만들지 않고 기존 정보를 유지한 채 마지막 로그인 시점만 갱신한다") {
				val past: LocalDateTime = LocalDateTime.of(2025, 1, 1, 0, 0)
				val existing: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(
						provider = "kakao",
						providerId = "kakao-existing",
						email = "existing@test.com",
						status = UserStatus.ACTIVE,
						lastLoginAt = past,
					),
				)

				val result: User = registerUserUseCase.registerIfAbsent(
					provider = "kakao",
					providerId = "kakao-existing",
					email = "existing@test.com",
					profileImageUrl = null,
				)

				// 동일 계정(id)을 반환하고, 기존 상태(ACTIVE)는 프로바이더 값으로 덮어쓰지 않는다.
				result.id shouldBe existing.id
				result.status shouldBe UserStatus.ACTIVE
				// 로그인 시점만 새로 기록된다.
				result.lastLoginAt.shouldNotBeNull()
				result.lastLoginAt shouldNotBe past

				usersWithProviderId("kakao-existing") shouldHaveSize 1
			}
		}

		context("같은 이메일이 다른 계정에서 이미 사용 중이면") {
			it("EMAIL_ALREADY_REGISTERED로 신규 가입을 막고 새 계정을 저장하지 않는다") {
				// kakao 계정이 dup@test.com 으로 이미 가입돼 있다.
				IntegrationUtil.persist(
					UserEntityFixture.create(
						provider = "kakao",
						providerId = "kakao-dup",
						email = "dup@test.com",
						status = UserStatus.ACTIVE,
					),
				)

				// 같은 이메일로 google 최초 로그인 시도 -> 중복으로 차단.
				val ex: BusinessException = shouldThrow {
					registerUserUseCase.registerIfAbsent(
						provider = "google",
						providerId = "google-dup",
						email = "dup@test.com",
						profileImageUrl = null,
					)
				}

				ex.errorCode shouldBe UserErrorCode.EMAIL_ALREADY_REGISTERED
				// 새 google 계정은 저장되지 않았다.
				usersWithProviderId("google-dup").shouldBeEmpty()
			}
		}

		context("이메일이 없는(null) OAuth 사용자면") {
			it("EMAIL_REQUIRED로 신규 가입을 막고 새 계정을 저장하지 않는다") {
				val ex: BusinessException = shouldThrow {
					registerUserUseCase.registerIfAbsent(
						provider = "google",
						providerId = "google-noemail",
						email = null,
						profileImageUrl = null,
					)
				}

				ex.errorCode shouldBe UserErrorCode.EMAIL_REQUIRED
				usersWithProviderId("google-noemail").shouldBeEmpty()
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

// 해당 providerId 로 저장된 사용자 전체. (신규 가입 여부/중복 저장 여부 확인용)
private fun usersWithProviderId(providerId: String): List<UserEntity> {
	val user: QUserEntity = QUserEntity.userEntity
	return IntegrationUtil.getQuery()
		.selectFrom(user)
		.where(user.providerId.eq(providerId))
		.fetch()
}
