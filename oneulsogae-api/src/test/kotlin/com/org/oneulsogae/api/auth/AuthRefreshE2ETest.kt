package com.org.oneulsogae.api.auth

import com.org.oneulsogae.auth.PrincipalDetails
import com.org.oneulsogae.auth.jwt.IssuedTokens
import com.org.oneulsogae.auth.jwt.RefreshTokenService
import com.org.oneulsogae.auth.jwt.TokenCookieFactory
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.auth.entity.QRefreshTokenEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import org.hamcrest.Matchers.containsString
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * 웹(쿠키) 토큰 갱신 엔드포인트(POST /auth/v1/refresh) E2E.
 * 클라이언트가 만료 전 선제 갱신 타이머를 걸 수 있도록 accessToken 유효기간(초)을 응답으로 내려준다.
 */
class AuthRefreshE2ETest(
	private val refreshTokenService: RefreshTokenService,
) : AbstractIntegrationSupport({

	describe("POST /auth/v1/refresh") {
		context("유효한 refresh 쿠키면") {
			it("토큰을 회전 재발급하고 accessToken 유효기간(초)을 내려준다") {
				val authentication: Authentication = authenticationFor(9101L, "web-refresh@test.com")
				val issued: IssuedTokens = refreshTokenService.issue(authentication)

				post("/auth/v1/refresh") {
					header("Cookie", "${TokenCookieFactory.REFRESH_TOKEN}=${issued.refreshToken}")
				} expect {
					status(200)
					// 테스트 프로파일 기본 jwt.expiration_time(3600000ms) = 3600초
					body("data.expiresInSeconds", 3600)
					// Set-Cookie가 access·refresh 두 개라 단일 헤더 매처는 마지막 것만 본다 — 회전된 refresh 쿠키로 확인
					header("Set-Cookie", containsString("${TokenCookieFactory.REFRESH_TOKEN}="))
				}
			}
		}

		context("refresh 쿠키가 없으면") {
			it("401을 반환한다") {
				post("/auth/v1/refresh") expect {
					status(401)
				}
			}
		}

		context("유효하지 않은 refresh 쿠키면") {
			it("401을 반환한다") {
				post("/auth/v1/refresh") {
					header("Cookie", "${TokenCookieFactory.REFRESH_TOKEN}=garbage-token")
				} expect {
					status(401)
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
