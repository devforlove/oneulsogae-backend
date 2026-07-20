package com.org.oneulsogae.common.integration

import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matcher
import org.hamcrest.Matchers.equalTo
import org.springframework.http.HttpHeaders

/**
 * E2E HTTP 호출용 Kotlin DSL.
 *
 * RestAssured의 `given()/when()/then()` 체이닝과 Hamcrest matcher 보일러플레이트를 줄이고, 요청/검증을 블록으로 분리해
 * 가독성을 높인다.
 *
 * ```
 * post("/matches/v1/$id/interest") {
 *     bearer(token)
 *     jsonBody("""{"coinAmount": 32}""")
 * } expect {
 *     status(200)
 *     body("data.status", "PARTIALLY_ACCEPTED")
 * }
 * ```
 */
class HttpRequestSpec {

	val spec: RequestSpecification = RestAssured.given().contentType(ContentType.JSON)

	/** Authorization: Bearer 토큰 헤더를 추가한다. */
	fun bearer(token: String) {
		spec.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
	}

	/** JSON 본문을 설정한다. */
	fun jsonBody(json: String) {
		spec.body(json)
	}

	/** 임의 요청 헤더를 추가한다. */
	fun header(name: String, value: String) {
		spec.header(name, value)
	}

	/** 302 등 리다이렉트 응답을 자동으로 따라가지 않고 그대로 검증하고 싶을 때 호출한다. */
	fun noRedirect() {
		spec.redirects().follow(false)
	}
}

class HttpResponseSpec(private val response: ValidatableResponse) {

	/** HTTP 상태 코드를 검증한다. */
	fun status(code: Int) {
		response.statusCode(code)
	}

	/** JSON 경로 값이 기대값과 같은지 검증한다. */
	fun body(path: String, expected: Any?) {
		response.body(path, equalTo(expected))
	}

	/** JSON 경로 값을 Hamcrest matcher로 검증한다. (예: notNullValue) */
	fun body(path: String, matcher: Matcher<*>) {
		response.body(path, matcher)
	}

	/** 응답 헤더 값을 Hamcrest matcher로 검증한다. (예: 302 리다이렉트의 Location) */
	fun header(name: String, matcher: Matcher<String>) {
		response.header(name, matcher)
	}
}

fun post(path: String, configure: HttpRequestSpec.() -> Unit = {}): ValidatableResponse =
	HttpRequestSpec().apply(configure).spec.post(path).then()

fun put(path: String, configure: HttpRequestSpec.() -> Unit = {}): ValidatableResponse =
	HttpRequestSpec().apply(configure).spec.put(path).then()

fun get(path: String, configure: HttpRequestSpec.() -> Unit = {}): ValidatableResponse =
	HttpRequestSpec().apply(configure).spec.get(path).then()

fun delete(path: String, configure: HttpRequestSpec.() -> Unit = {}): ValidatableResponse =
	HttpRequestSpec().apply(configure).spec.delete(path).then()

/** 응답 검증 블록을 연다. */
infix fun ValidatableResponse.expect(assertions: HttpResponseSpec.() -> Unit) {
	HttpResponseSpec(this).apply(assertions)
}
