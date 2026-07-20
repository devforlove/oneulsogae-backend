package com.org.oneulsogae.core.user.query.dao

import com.org.oneulsogae.core.user.query.dto.UserWithDetailView

/**
 * 사용자 + 프로필 상세 조인 조회 dao(query out-port 인터페이스). (1+N 방지) read model([UserWithDetailView])을 반환한다.
 */
interface GetUserWithDetailDao {

	/** userId로 사용자와 프로필 상세를 조인해 함께 조회한다. 둘 중 하나라도 없으면 null. */
	fun findWithDetailByUserId(userId: Long): UserWithDetailView?
}
