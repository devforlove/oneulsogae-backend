package com.org.oneulsogae.api.auth

import com.org.oneulsogae.api.auth.request.MobileExchangeRequest
import com.org.oneulsogae.api.auth.request.MobileRefreshRequest
import com.org.oneulsogae.api.auth.response.MobileTokenResponse
import com.org.oneulsogae.auth.AuthErrorCode
import com.org.oneulsogae.auth.jwt.IssuedTokens
import com.org.oneulsogae.auth.jwt.RefreshTokenService
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.error.ErrorResponse
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.infra.auth.code.MobileAuthCodeStore
import com.org.oneulsogae.infra.auth.code.StoredTokens
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 모바일 앱 전용 인증 엔드포인트. 웹은 HttpOnly 쿠키를 쓰지만, 앱은 쿠키를 못 쓰므로
 * 일회용 code 교환·refresh·logout을 JSON body로 주고받는다. (access token 없이 호출되므로 permitAll)
 */
@Tag(name = "인증(모바일)", description = "모바일 앱용 일회용 코드 교환·토큰 갱신·로그아웃")
@RestController
@RequestMapping("/auth/v1/mobile")
class MobileAuthController(
	private val mobileAuthCodeStore: MobileAuthCodeStore,
	private val refreshTokenService: RefreshTokenService,
) {

	@Operation(summary = "일회용 코드 교환", description = "OAuth 성공 후 딥링크로 받은 code를 access/refresh 토큰으로 교환한다.")
	@PostMapping("/exchange")
	fun exchange(@RequestBody request: MobileExchangeRequest): ResponseEntity<ApiResponse<MobileTokenResponse>> {
		val stored: StoredTokens = mobileAuthCodeStore.consume(request.code) ?: return unauthorized()
		return ResponseEntity.ok(ApiResponse.success(MobileTokenResponse(stored.accessToken, stored.refreshToken)))
	}

	@Operation(summary = "토큰 갱신(모바일)", description = "refresh token(body)으로 access/refresh를 회전 재발급한다.")
	@PostMapping("/refresh")
	fun refresh(@RequestBody request: MobileRefreshRequest): ResponseEntity<ApiResponse<MobileTokenResponse>> =
		try {
			val tokens: IssuedTokens = refreshTokenService.rotate(request.refreshToken)
			ResponseEntity.ok(ApiResponse.success(MobileTokenResponse(tokens.accessToken, tokens.refreshToken)))
		} catch (e: BusinessException) {
			ResponseEntity.status(e.errorCode.status).body(ApiResponse.error(ErrorResponse.of(e.errorCode)))
		}

	@Operation(summary = "로그아웃(모바일)", description = "refresh token(body)을 폐기한다.")
	@PostMapping("/logout")
	fun logout(@RequestBody request: MobileRefreshRequest): ApiResponse<Unit> {
		refreshTokenService.revoke(request.refreshToken)
		return ApiResponse.success()
	}

	private fun unauthorized(): ResponseEntity<ApiResponse<MobileTokenResponse>> =
		ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ApiResponse.error(ErrorResponse.of(AuthErrorCode.AUTHENTICATION_REQUIRED)))
}
