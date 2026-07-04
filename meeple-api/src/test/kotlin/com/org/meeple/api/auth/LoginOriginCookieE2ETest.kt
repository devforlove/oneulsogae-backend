package com.org.meeple.api.auth

import com.org.meeple.common.integration.AbstractIntegrationSupport
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import io.restassured.response.Response

/**
 * OAuth2 로그인 시작 요청의 출처 쿠키(loginOrigin) E2E 테스트.
 * 어드민 프론트는 `?origin=admin`으로 로그인을 시작하고, 필터가 심은 쿠키를 성공 핸들러가 읽어
 * 리다이렉트를 가른다. 여기서는 시작 요청에서 쿠키가 심기고/지워지는지를 검증한다.
 * (공급자 왕복을 포함한 성공 핸들러 분기는 외부 공급자 의존이라 E2E 범위 밖)
 */
class LoginOriginCookieE2ETest : AbstractIntegrationSupport({

	// 공급자(카카오)로의 302를 따라가지 않도록 리다이렉트 추적을 끈 raw 호출.
	fun startLogin(query: String = ""): Response =
		RestAssured.given().redirects().follow(false).get("/oauth2/authorization/kakao$query")

	describe("GET /oauth2/authorization/kakao") {

		context("origin=admin으로 로그인을 시작하면") {
			it("공급자로 리다이렉트하며 어드민 출처 쿠키(loginOrigin=admin)를 심는다") {
				val response: Response = startLogin("?origin=admin")

				response.statusCode shouldBe 302
				val setCookies: List<String> = response.headers().getValues("Set-Cookie")
				setCookies.any { header: String -> header.startsWith("loginOrigin=admin") }.shouldBeTrue()
			}
		}

		context("출처 파라미터 없이 로그인을 시작하면") {
			it("잔여 어드민 출처 쿠키를 지운다 (loginOrigin 빈 값 + Max-Age=0)") {
				val response: Response = startLogin()

				response.statusCode shouldBe 302
				val setCookies: List<String> = response.headers().getValues("Set-Cookie")
				setCookies.any { header: String -> header.startsWith("loginOrigin=;") && header.contains("Max-Age=0") }.shouldBeTrue()
			}
		}
	}
})
