package com.org.meeple.auth

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 인증(JWT/세션) 관련 에러 코드. */
enum class AuthErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	/** 인증 정보가 없거나(미로그인) accessToken이 만료·위조되어 유효하지 않은 경우. */
	AUTHENTICATION_REQUIRED("AUTH-001", "인증이 필요합니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),

	/** 다른 기기/브라우저에서 같은 계정으로 로그인되어 현재 세션이 종료된 경우. (단일 활성 세션) */
	SESSION_TAKEN_OVER("AUTH-002", "다른 기기 또는 브라우저에서 로그인되어 현재 세션이 종료되었습니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
}
