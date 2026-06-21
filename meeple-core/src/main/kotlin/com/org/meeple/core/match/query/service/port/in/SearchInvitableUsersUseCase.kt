package com.org.meeple.core.match.query.service.port.`in`

import com.org.meeple.core.match.query.dto.InvitableUser

/**
 * 초대 가능한 유저를 닉네임으로 검색하는 유스케이스(인포트).
 * 요청자([userId])와 같은 성별·매칭 가능·활성 팀 없음 조건을 만족하는, 닉네임이 정확히 일치하는 유저를 반환한다.
 */
interface SearchInvitableUsersUseCase {

	/** [userId]가 [nickname]으로 초대 가능한 유저를 검색한다. */
	fun search(userId: Long, nickname: String): List<InvitableUser>
}
