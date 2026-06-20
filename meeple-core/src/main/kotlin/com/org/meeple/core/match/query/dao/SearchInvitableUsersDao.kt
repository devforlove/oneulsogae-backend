package com.org.meeple.core.match.query.dao

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.InvitableUser

/**
 * 초대 가능한 유저(닉네임 정확 일치) 조회 dao(query out-port). 실제 QueryDSL 구현은 infra가 담당한다.
 * 후보는 매칭 가능(match_user 존재)하고 [requesterGender]와 같은 성별이며, [requesterId] 자신과 활성 팀 소속자는 제외한다.
 */
interface SearchInvitableUsersDao {

	/** [nickname]이 정확히 일치하는 초대 가능 유저 목록을 userId 오름차순으로 반환한다. */
	fun search(requesterGender: Gender, requesterId: Long, nickname: String): List<InvitableUser>
}
