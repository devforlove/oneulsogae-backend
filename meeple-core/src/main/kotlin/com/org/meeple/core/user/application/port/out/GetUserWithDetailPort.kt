package com.org.meeple.core.user.application.port.out

import com.org.meeple.core.user.domain.UserWithDetail

/**
 * 사용자 + 프로필 상세 조회 아웃포트.
 * 사용자(users)와 프로필 상세(user_details)를 조인 한 번으로 함께 조회한다. (1+N 방지)
 * 실제 구현은 infra 레이어의 어댑터가 조인 쿼리로 담당한다.
 */
interface GetUserWithDetailPort {

	/**
	 * userId로 사용자와 프로필 상세를 조인해 함께 조회한다. 둘 중 하나라도 없으면 null.
	 * (단순 프로필만 보는 [GetUserDetailPort.findByUserId]와 한 어댑터에서 함께 구현되므로 이름을 구분한다)
	 */
	fun findWithDetailByUserId(userId: Long): UserWithDetail?
}
