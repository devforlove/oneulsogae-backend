package com.org.oneulsogae.api.auth

import com.org.oneulsogae.auth.PrincipalDetails
import com.org.oneulsogae.auth.jwt.TokenCookieFactory
import com.org.oneulsogae.auth.oauth.LoginOriginCookieFactory
import com.org.oneulsogae.auth.oauth.OAuth2SuccessHandler
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.auth.code.MobileAuthCodeStore
import com.org.oneulsogae.infra.auth.code.StoredTokens
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.org.oneulsogae.infra.user.command.entity.UserEntity
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import jakarta.servlet.http.Cookie
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * [OAuth2SuccessHandler]의 모바일 출처(loginOrigin=mobile) 분기 통합테스트.
 *
 * 이 프로젝트에는 mockk/mockito 등 목킹 라이브러리가 없으므로(확인됨), 브리프의 mockk 기반 단위테스트 대신
 * [AbstractIntegrationSupport]를 상속한 통합테스트로 작성한다. 실제 유저를 저장하고 실제 Authentication으로
 * 핸들러를 호출해, 응답이 `oneulsogaemobile://auth?code=<code>`로 리다이렉트되고 그 code가
 * [MobileAuthCodeStore]에서 실제 발급된 토큰으로 소비됨을 검증한다.
 */
class OAuth2SuccessHandlerMobileTest(
	private val oAuth2SuccessHandler: OAuth2SuccessHandler,
	private val mobileAuthCodeStore: MobileAuthCodeStore,
) : AbstractIntegrationSupport({

	describe("모바일 출처(loginOrigin=mobile) 로그인 성공") {
		it("일회용 code를 발급해 mobileRedirectUri로 리다이렉트하고, code는 실제 발급된 토큰으로 교환된다") {
			val user: UserEntity = IntegrationUtil.persist(
				UserEntityFixture.create(providerId = "mobile-success-a", email = "mobile-a@test.com", status = UserStatus.ACTIVE),
			)
			val authentication: Authentication = authenticationFor(user.id!!, "mobile-a@test.com")

			val request = MockHttpServletRequest().apply {
				setCookies(Cookie(LoginOriginCookieFactory.LOGIN_ORIGIN, LoginOriginCookieFactory.MOBILE_ORIGIN))
			}
			val response = MockHttpServletResponse()

			oAuth2SuccessHandler.onAuthenticationSuccess(request, response, authentication)

			val redirectedUrl: String? = response.redirectedUrl
			redirectedUrl.shouldNotBeNull()
			redirectedUrl.shouldStartWith("oneulsogaemobile://auth?code=")
			// 출처 쿠키(loginOrigin) 만료 처리는 모든 분기 공통이지만, 모바일 분기는 accessToken/refreshToken 쿠키를 심지 않는다.
			response.cookies.none { cookie ->
				cookie.name == TokenCookieFactory.ACCESS_TOKEN || cookie.name == TokenCookieFactory.REFRESH_TOKEN
			} shouldBe true

			val code: String = redirectedUrl.substringAfter("code=")
			val storedTokens: StoredTokens? = mobileAuthCodeStore.consume(code)

			storedTokens.shouldNotBeNull()
			storedTokens.accessToken.shouldNotBeBlank()
			storedTokens.refreshToken.shouldNotBeBlank()
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

// E2E용 Authentication. (로그인 성공 시 SuccessHandler가 넘기는 인증 객체와 동일한 형태)
// SingleSessionIntegrationTest.kt의 동명 헬퍼는 파일 프라이빗이라 재사용할 수 없어 동일하게 둔다.
private fun authenticationFor(userId: Long, email: String): Authentication {
	val authorities: List<SimpleGrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER"))
	val principal = PrincipalDetails(email = email, id = userId, authorities = authorities)
	return UsernamePasswordAuthenticationToken(principal, "", authorities)
}
