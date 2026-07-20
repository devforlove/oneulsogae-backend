package com.org.oneulsogae.api.auth.response

import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.query.dto.UserView

/** 현재 로그인 사용자 정보 응답. 인증 식별 정보에 더해 조회한 가입 상태(status)를 담는다. */
data class MeResponse(
    val id: Long,
    val email: String,
    val status: UserStatus,
) {
	companion object {
		fun of(authUser: AuthUser, user: UserView): MeResponse =
			MeResponse(id = authUser.id, email = authUser.email, status = user.status)
	}
}