package com.org.oneulsogae.infra.auth.code

/** 일회용 코드에 매핑되어 저장되는 access/refresh 토큰 쌍. (infra 모듈: api의 IssuedTokens에 의존하지 않는다) */
data class StoredTokens(
	val accessToken: String,
	val refreshToken: String,
)
