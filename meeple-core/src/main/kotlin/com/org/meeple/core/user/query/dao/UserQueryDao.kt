package com.org.meeple.core.user.query.dao

import com.org.meeple.core.user.query.dto.UserView

/**
 * 사용자 계정 조회 dao(query out-port 인터페이스). read model([UserView])을 반환하며, QueryDSL 구현은 infra가 담당한다.
 * 명령 흐름의 단건 로드는 command 쪽 [com.org.meeple.core.user.command.service.port.out.GetUserPort]가 따로 둔다.
 */
interface UserQueryDao {

	/** id로 사용자를 조회한다. 없으면 null. */
	fun findById(id: Long): UserView?
}
