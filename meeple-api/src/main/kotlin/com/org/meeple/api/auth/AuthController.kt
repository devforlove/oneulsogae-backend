package com.org.meeple.api.auth

import com.org.meeple.api.auth.response.MeResponse
import com.org.meeple.auth.AuthErrorCode
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.auth.jwt.InvalidRefreshTokenException
import com.org.meeple.auth.jwt.IssuedTokens
import com.org.meeple.auth.jwt.RefreshTokenService
import com.org.meeple.auth.jwt.SessionTakenOverException
import com.org.meeple.auth.jwt.TokenCookieFactory
import com.org.meeple.core.common.error.ErrorResponse
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.query.service.port.`in`.GetUserByIdUseCase
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth/v1")
class AuthController(
	private val refreshTokenService: RefreshTokenService,
	private val tokenCookieFactory: TokenCookieFactory,
	private val getUserByIdUseCase: GetUserByIdUseCase,
) {

	/** 현재 로그인 사용자 정보. accessToken(쿠키)으로 식별하고, 조회한 가입 상태(status)를 함께 내려준다. */
	@GetMapping("/me")
	fun me(@LoginUser user: AuthUser): ApiResponse<MeResponse> =
		ApiResponse.success(MeResponse.of(user, getUserByIdUseCase.getById(user.id)))

	/** refresh 쿠키로 access/refresh를 회전 재발급한다. 실패 시 쿠키를 비우고 401. */
	@PostMapping("/refresh")
	fun refresh(
		@CookieValue(name = TokenCookieFactory.REFRESH_TOKEN, required = false) refreshToken: String?,
		response: HttpServletResponse,
	): ResponseEntity<ApiResponse<Unit>> {
		if (refreshToken.isNullOrBlank()) {
			return unauthorized()
		}

		return try {
			val tokens: IssuedTokens = refreshTokenService.rotate(refreshToken)
			response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.accessTokenCookie(tokens.accessToken).toString())
			response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.refreshTokenCookie(tokens.refreshToken).toString())
			ResponseEntity.ok(ApiResponse.success())
		} catch (e: SessionTakenOverException) {
			// 다른 기기/브라우저의 새 로그인에 밀려난 세션 → 일반 인증 실패와 구분해 안내한다.
			clearAuthCookies(response)
			unauthorized(AuthErrorCode.SESSION_TAKEN_OVER)
		} catch (e: InvalidRefreshTokenException) {
			clearAuthCookies(response)
			unauthorized()
		}
	}

	/** 로그아웃: 서버의 refresh token을 폐기하고 인증 쿠키를 삭제한다. */
	@PostMapping("/logout")
	fun logout(
		@CookieValue(name = TokenCookieFactory.REFRESH_TOKEN, required = false) refreshToken: String?,
		response: HttpServletResponse,
	): ApiResponse<Unit> {
		refreshTokenService.revoke(refreshToken)
		clearAuthCookies(response)
		return ApiResponse.success()
	}

	/** 인증 실패(401) 응답을 공통 봉투로 내려준다. */
	private fun unauthorized(errorCode: AuthErrorCode = AuthErrorCode.AUTHENTICATION_REQUIRED): ResponseEntity<ApiResponse<Unit>> =
		ResponseEntity
			.status(HttpStatus.UNAUTHORIZED)
			.body(ApiResponse.error(ErrorResponse.of(errorCode)))

	private fun clearAuthCookies(response: HttpServletResponse) {
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredAccessTokenCookie().toString())
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredRefreshTokenCookie().toString())
	}
}
