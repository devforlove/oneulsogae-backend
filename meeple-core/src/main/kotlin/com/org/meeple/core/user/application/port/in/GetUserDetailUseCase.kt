package com.org.meeple.core.user.application.port.`in`

import com.org.meeple.core.user.domain.UserDetail

/**
 * userId로 사용자 프로필 상세를 조회하는 인포트(유스케이스).
 */
interface GetUserDetailUseCase {

	/** userId로 프로필 상세를 조회한다. 없으면 USER_DETAIL_NOT_FOUND를 던진다. */
	fun getByUserId(userId: Long): UserDetail

	/** userId로 프로필 상세를 조회한다. 없으면 null. (알람 문구 등 부가 정보를 best-effort로 채울 때) */
	fun findByUserId(userId: Long): UserDetail?
}
