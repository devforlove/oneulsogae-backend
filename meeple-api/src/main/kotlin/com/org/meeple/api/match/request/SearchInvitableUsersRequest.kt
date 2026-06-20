package com.org.meeple.api.match.request

import jakarta.validation.constraints.NotBlank

/**
 * 초대 가능 유저 검색 요청. 쿼리 파라미터 `nickname`을 바인딩한다.
 * 닉네임은 필수(@NotBlank) — 공백/누락이면 400.
 */
data class SearchInvitableUsersRequest(
	@field:NotBlank(message = "닉네임은 필수입니다.")
	val nickname: String? = null,
)
