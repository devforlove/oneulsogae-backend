package com.org.meeple.core.match.query.dao

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.InvitableUser

/**
 * 초대 가능한 유저(닉네임 정확 일치) 조회 dao(query out-port). 실제 QueryDSL 구현은 infra가 담당한다.
 * 후보는 매칭 가능(match_user 존재)하고 [requesterGender]와 같은 성별이며, [userId] 자신은 제외한다.
 * (활성 팀 소속 여부는 거르지 않는다 — 초대 시점에 invite 명령이 차단한다)
 */
interface SearchInvitableUsersDao {

	/** 요청자의 성별을 match_user 에서 조회한다. 매칭 불가 상태이면 null을 반환한다. */
	fun findRequesterGender(userId: Long): Gender?

	/** [nickname]이 정확히 일치하는 초대 가능 유저 목록을 userId 오름차순으로 반환한다. */
	fun search(requesterGender: Gender, userId: Long, nickname: String): List<InvitableUser>
}
