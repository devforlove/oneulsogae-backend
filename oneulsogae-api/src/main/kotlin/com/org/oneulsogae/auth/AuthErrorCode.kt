package com.org.oneulsogae.auth

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 인증(JWT/세션) 관련 에러 코드. */
enum class AuthErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	/** 인증 정보가 없거나(미로그인) accessToken이 만료·위조되어 유효하지 않은 경우. */
	AUTHENTICATION_REQUIRED("AUTH-001", "인증이 필요합니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED),
}
