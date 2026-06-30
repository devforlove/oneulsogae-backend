package com.org.meeple.api.user

import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.auth.jwt.TokenCookieFactory
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.user.command.application.port.`in`.WithdrawUserUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "회원", description = "회원 계정 관리(탈퇴 등)")
@RestController
@RequestMapping("/users/v1/account")
class UserAccountController(
	private val withdrawUserUseCase: WithdrawUserUseCase,
	private val tokenCookieFactory: TokenCookieFactory,
) {

	/** 회원 탈퇴: 계정을 비활성(소프트삭제)하고 인증 쿠키를 삭제한다. 10일 내 같은 소셜로 재로그인하면 복구된다. */
	@Operation(summary = "회원 탈퇴", description = "계정을 비활성화하고 토큰·쿠키를 폐기한다. 10일 내 재로그인 시 복구된다.")
	@DeleteMapping
	fun withdraw(@LoginUser user: AuthUser, response: HttpServletResponse): ApiResponse<Unit> {
		withdrawUserUseCase.withdraw(user.id)
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredAccessTokenCookie().toString())
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredRefreshTokenCookie().toString())
		return ApiResponse.success()
	}
}
