package com.org.meeple.core.user.query.dao

import com.org.meeple.core.user.query.dto.UserDetailView

/**
 * 사용자 프로필 상세 조회 dao(query out-port 인터페이스). read model([UserDetailView])을 반환하며, QueryDSL 구현은 infra가 담당한다.
 * 명령 흐름의 단건 로드는 command 쪽 [com.org.meeple.core.user.command.application.port.out.GetUserDetailPort]가 따로 둔다.
 */
interface GetUserDetailDao {

	/** userId로 프로필 상세를 조회한다. 없으면 null. */
	fun findByUserId(userId: Long): UserDetailView?
}
