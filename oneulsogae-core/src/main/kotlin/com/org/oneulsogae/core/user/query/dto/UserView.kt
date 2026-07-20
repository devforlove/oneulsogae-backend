package com.org.oneulsogae.core.user.query.dto

import com.org.oneulsogae.common.user.UserStatus

/**
 * 사용자 계정 조회 결과(read model). query는 command 도메인([com.org.oneulsogae.core.user.command.domain.User]) 대신 이 view를 쓴다.
 */
data class UserView(
	val id: Long,
	val email: String?,
	val status: UserStatus,
) {

	/** 정식 가입(ACTIVE 등) 상태인지 여부. */
	val isRegistered: Boolean
		get() = status.isRegistered()
}
