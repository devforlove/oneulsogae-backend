package com.org.meeple.infra.match.query

import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.match.command.repository.ActiveUserJpaRepository
import com.org.meeple.scheduler.match.query.dao.ActiveUserQueryDao
import com.org.meeple.scheduler.match.query.dto.ActiveUser
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [ActiveUserQueryDao]의 구현. 매칭 풀 그룹핑용 활성 사용자 조회를 [ActiveUserJpaRepository]에 위임한다.
 * 쿼리가 [ActiveUser] read model로 직접 투영하므로 그대로 반환한다. (repository는 command 아래에 있고, query→command 참조는 허용)
 */
@Component
class ActiveUserQueryDaoImpl(
	private val activeUserJpaRepository: ActiveUserJpaRepository,
) : ActiveUserQueryDao {

	override fun findActiveUsers(loginAfter: LocalDateTime): List<ActiveUser> =
		activeUserJpaRepository.findActiveUsers(status = UserStatus.ACTIVE, loginAfter = loginAfter)
}
