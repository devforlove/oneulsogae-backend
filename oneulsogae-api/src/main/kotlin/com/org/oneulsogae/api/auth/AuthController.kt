package com.org.oneulsogae.api.auth

import com.org.oneulsogae.api.auth.response.MeResponse
import com.org.oneulsogae.api.auth.response.RefreshResponse
import com.org.oneulsogae.auth.AuthErrorCode
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.auth.jwt.IssuedTokens
import com.org.oneulsogae.auth.jwt.RefreshTokenService
import com.org.oneulsogae.auth.jwt.TokenCookieFactory
import com.org.oneulsogae.auth.jwt.TokenProvider
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.error.ErrorResponse
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserByIdUseCase
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증", description = "액세스/리프레시 토큰 발급·갱신·폐기 및 로그인 사용자 정보 조회")
@RestController
@RequestMapping("/auth/v1")
class AuthController(
	private val refreshTokenService: RefreshTokenService,
	private val tokenCookieFactory: TokenCookieFactory,
	private val tokenProvider: TokenProvider,
	private val getUserByIdUseCase: GetUserByIdUseCase,
) {

	/** 현재 로그인 사용자 정보. accessToken(쿠키)으로 식별하고, 조회한 가입 상태(status)를 함께 내려준다. */
	@Operation(summary = "로그인 사용자 정보 조회", description = "accessToken(쿠키)으로 현재 로그인 사용자를 식별하고 가입 상태(status)를 함께 반환한다.")
	@GetMapping("/me")
	fun me(@LoginUser user: AuthUser): ApiResponse<MeResponse> =
		ApiResponse.success(MeResponse.of(user, getUserByIdUseCase.getById(user.id)))

	/** refresh 쿠키로 access/refresh를 회전 재발급한다. 실패 시 쿠키를 비우고 401. */
	@Operation(summary = "토큰 갱신", description = "refresh 쿠키로 access/refresh 토큰을 회전 재발급한다. 새 accessToken 유효기간(초)을 함께 반환한다. 실패 시 쿠키를 비우고 401을 반환한다.")
	@PostMapping("/refresh")
	fun refresh(
		@CookieValue(name = TokenCookieFactory.REFRESH_TOKEN, required = false) refreshToken: String?,
		response: HttpServletResponse,
	): ResponseEntity<ApiResponse<RefreshResponse>> {
		if (refreshToken.isNullOrBlank()) {
			return unauthorized()
		}

		return try {
			val tokens: IssuedTokens = refreshTokenService.rotate(refreshToken)
			response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.accessTokenCookie(tokens.accessToken).toString())
			response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.refreshTokenCookie(tokens.refreshToken).toString())
			ResponseEntity.ok(ApiResponse.success(RefreshResponse(tokenProvider.accessTokenExpiresInSeconds())))
		} catch (e: BusinessException) {
			// 예측 가능한 인증 실패(무효/재사용 토큰, 세션 밀림 등): 죽은 쿠키를 비우고 에러 코드에 맞춰 응답한다.
			// (쿠키 삭제는 전역 핸들러가 못 하는 부수효과라 여기서 잡고, 상태·코드는 errorCode가 결정한다)
			clearAuthCookies(response)
			ResponseEntity
				.status(e.errorCode.status)
				.body(ApiResponse.error(ErrorResponse.of(e.errorCode)))
		}
	}

	/** 로그아웃: 서버의 refresh token을 폐기하고 인증 쿠키를 삭제한다. */
	@Operation(summary = "로그아웃", description = "서버에 저장된 refresh token을 폐기하고 인증 쿠키를 삭제한다.")
	@PostMapping("/logout")
	fun logout(
		@CookieValue(name = TokenCookieFactory.REFRESH_TOKEN, required = false) refreshToken: String?,
		response: HttpServletResponse,
	): ApiResponse<Unit> {
		refreshTokenService.revoke(refreshToken)
		clearAuthCookies(response)
		return ApiResponse.success()
	}

	/** refresh 쿠키 부재 등 토큰 검증 이전 단계의 인증 실패(401) 응답. */
	private fun unauthorized(): ResponseEntity<ApiResponse<RefreshResponse>> =
		ResponseEntity
			.status(HttpStatus.UNAUTHORIZED)
			.body(ApiResponse.error(ErrorResponse.of(AuthErrorCode.AUTHENTICATION_REQUIRED)))

	private fun clearAuthCookies(response: HttpServletResponse) {
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredAccessTokenCookie().toString())
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredRefreshTokenCookie().toString())
	}
}
