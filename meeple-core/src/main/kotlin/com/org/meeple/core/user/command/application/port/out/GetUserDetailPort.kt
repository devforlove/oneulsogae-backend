package com.org.meeple.core.user.command.application.port.out

import com.org.meeple.core.user.command.domain.UserDetail

/**
 * 사용자 프로필 상세 조회 아웃포트.
 * 도메인 모델([UserDetail])만을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface GetUserDetailPort {

	/** userId로 프로필 상세를 조회한다. 없으면 null. */
	fun findByUserId(userId: Long): UserDetail?
}
