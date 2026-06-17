package com.org.meeple.api.auth

import com.org.meeple.auth.PrincipalDetails
import com.org.meeple.auth.jwt.IssuedTokens
import com.org.meeple.auth.jwt.RefreshTokenService
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.auth.entity.QRefreshTokenEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserEntity
import io.restassured.RestAssured
import org.hamcrest.Matchers.equalTo
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * 단일 활성 세션(마지막 로그인 우선) E2E.
 *
 * 같은 계정으로 다른 브라우저/기기에서 새로 로그인하면(= [RefreshTokenService.issue] 재호출로 활성 세션이 덮어써지면)
 * 이전 세션의 accessToken은 매 요청에서 검사되는 활성 세션 마커와 어긋나 401(SESSION_TAKEN_OVER)로 끊기고,
 * 이전 세션의 refresh token으로는 재발급도 거부됨을 실서버 + Redis(Testcontainers)로 검증한다.
 */
class SingleSessionIntegrationTest(
	private val refreshTokenService: RefreshTokenService,
) : AbstractIntegrationSupport({

	describe("단일 활성 세션") {

		context("같은 계정으로 다른 브라우저에서 새로 로그인하면") {
			it("이전 세션의 accessToken은 다음 요청에서 401(SESSION_TAKEN_OVER)로 끊긴다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "single-session-a", email = "a@test.com", status = UserStatus.ACTIVE),
				)
				val authentication: Authentication = authenticationFor(user.id!!,"a@test.com")

				val first: IssuedTokens = refreshTokenService.issue(authentication)
				// 첫 세션은 정상 동작한다.
				get("/auth/v1/me") { bearer(first.accessToken) } expect { status(200) }

				// 다른 브라우저에서 새 로그인 → 활성 세션이 새 sessionId로 덮어써진다.
				val second: IssuedTokens = refreshTokenService.issue(authentication)

				// 새 세션은 정상, 이전 세션은 끊긴다.
				get("/auth/v1/me") { bearer(second.accessToken) } expect { status(200) }
				get("/auth/v1/me") { bearer(first.accessToken) } expect {
					status(401)
					body("error.code", "AUTH-002")
				}
			}
		}

		context("밀려난 세션의 refresh token으로 재발급을 시도하면") {
			it("401(SESSION_TAKEN_OVER)로 거부되고, 현재 활성 세션의 refresh는 정상 회전된다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "single-session-b", email = "b@test.com", status = UserStatus.ACTIVE),
				)
				val authentication: Authentication = authenticationFor(user.id!!,"b@test.com")

				val first: IssuedTokens = refreshTokenService.issue(authentication)
				val second: IssuedTokens = refreshTokenService.issue(authentication)

				// 밀려난 세션의 refresh → 거부.
				RestAssured.given()
					.cookie("refreshToken", first.refreshToken)
					.post("/auth/v1/refresh")
					.then()
					.statusCode(401)
					.body("error.code", equalTo("AUTH-002"))

				// 현재 활성 세션의 refresh → 정상 회전.
				RestAssured.given()
					.cookie("refreshToken", second.refreshToken)
					.post("/auth/v1/refresh")
					.then()
					.statusCode(200)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRefreshTokenEntity.refreshTokenEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

// E2E용 Authentication. (로그인 성공 시 SuccessHandler가 넘기는 인증 객체와 동일한 형태)
private fun authenticationFor(userId: Long, email: String): Authentication {
	val authorities: List<SimpleGrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER"))
	val principal = PrincipalDetails(email = email, id = userId, authorities = authorities)
	return UsernamePasswordAuthenticationToken(principal, "", authorities)
}
