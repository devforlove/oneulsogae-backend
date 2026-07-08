package com.org.meeple.api.auth

import com.org.meeple.auth.PrincipalDetails
import com.org.meeple.auth.jwt.IssuedTokens
import com.org.meeple.auth.jwt.RefreshTokenService
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.infra.auth.code.MobileAuthCodeStore
import com.org.meeple.infra.auth.code.StoredTokens
import com.org.meeple.infra.auth.entity.QRefreshTokenEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import org.hamcrest.Matchers.notNullValue
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * 모바일 앱 전용 인증 엔드포인트(/auth/v1/mobile 이하) E2E.
 * 앱은 쿠키를 못 쓰므로 일회용 code 교환·refresh·logout을 JSON body로 주고받는다.
 */
class MobileAuthControllerE2ETest(
	private val mobileAuthCodeStore: MobileAuthCodeStore,
	private val refreshTokenService: RefreshTokenService,
) : AbstractIntegrationSupport({

	describe("POST /auth/v1/mobile/exchange") {
		context("유효한 code면") {
			it("access/refresh 토큰을 body로 돌려준다") {
				val code: String = mobileAuthCodeStore.issue(StoredTokens("access-e", "refresh-e"))

				post("/auth/v1/mobile/exchange") {
					jsonBody("""{"code": "$code"}""")
				} expect {
					status(200)
					body("data.accessToken", "access-e")
					body("data.refreshToken", "refresh-e")
				}
			}
		}

		context("이미 소비되었거나 없는 code면") {
			it("401을 반환한다") {
				post("/auth/v1/mobile/exchange") {
					jsonBody("""{"code": "gone"}""")
				} expect {
					status(401)
				}
			}
		}
	}

	describe("POST /auth/v1/mobile/refresh") {
		context("유효한 refreshToken이면") {
			it("access/refresh 토큰을 회전 재발급한다") {
				val authentication: Authentication = authenticationFor(9001L, "mobile-refresh@test.com")
				val issued: IssuedTokens = refreshTokenService.issue(authentication)

				post("/auth/v1/mobile/refresh") {
					jsonBody("""{"refreshToken": "${issued.refreshToken}"}""")
				} expect {
					status(200)
					body("data.accessToken", notNullValue())
					body("data.refreshToken", notNullValue())
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRefreshTokenEntity.refreshTokenEntity)
	}
})

// E2E용 Authentication. (로그인 성공 시 SuccessHandler가 넘기는 인증 객체와 동일한 형태)
private fun authenticationFor(userId: Long, email: String): Authentication {
	val authorities: List<SimpleGrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER"))
	val principal = PrincipalDetails(email = email, id = userId, authorities = authorities)
	return UsernamePasswordAuthenticationToken(principal, "", authorities)
}
