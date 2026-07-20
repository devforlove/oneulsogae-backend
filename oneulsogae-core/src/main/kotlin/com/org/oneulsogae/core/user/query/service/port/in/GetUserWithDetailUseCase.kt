package com.org.oneulsogae.core.user.query.service.port.`in`

import com.org.oneulsogae.core.user.query.dto.UserWithDetailView

/**
 * userId로 사용자 + 프로필 상세를 함께 조회하는 인포트(유스케이스).
 * 다른 도메인(match 등)이 사용자+프로필을 한 번에 필요로 할 때, 이 in-port로 참조한다. (out-port 직접 주입 금지 규칙)
 */
interface GetUserWithDetailUseCase {

	/** userId로 사용자와 프로필 상세를 함께 조회한다. 없으면 USER_DETAIL_NOT_FOUND를 던진다. */
	fun getByUserId(userId: Long): UserWithDetailView
}
