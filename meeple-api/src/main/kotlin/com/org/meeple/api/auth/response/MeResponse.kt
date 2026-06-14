package com.org.meeple.api.auth.response

import com.org.meeple.auth.AuthUser
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.user.domain.User

/** 현재 로그인 사용자 정보 응답. 인증 식별 정보에 더해 조회한 가입 상태(status)를 담는다. */
data class MeResponse(
    val id: Long,
    val email: String,
    val status: UserStatus,
) {
	companion object {
		fun of(authUser: AuthUser, user: User): MeResponse =
			MeResponse(id = authUser.id, email = authUser.email, status = user.status)
	}
}