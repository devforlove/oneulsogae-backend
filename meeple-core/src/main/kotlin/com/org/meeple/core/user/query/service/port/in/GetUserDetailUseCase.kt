package com.org.meeple.core.user.query.service.port.`in`

import com.org.meeple.core.user.query.dto.UserDetailView

/**
 * userId로 사용자 프로필 상세를 조회하는 인포트(유스케이스). read model([UserDetailView])을 반환한다.
 */
interface GetUserDetailUseCase {

	/** userId로 프로필 상세를 조회한다. 없으면 USER_DETAIL_NOT_FOUND를 던진다. */
	fun getByUserId(userId: Long): UserDetailView

	/** userId로 프로필 상세를 조회한다. 없으면 null. (알람 문구 등 부가 정보를 best-effort로 채울 때) */
	fun findByUserId(userId: Long): UserDetailView?
}
