package com.org.oneulsogae.api.auth.response

/** 토큰 갱신 응답. 클라이언트가 만료 전 선제(silent) 갱신 타이머를 걸 수 있게 유효기간을 내려준다. */
data class RefreshResponse(
	/** 새로 발급된 accessToken의 유효기간(초). */
	val expiresInSeconds: Long,
)
