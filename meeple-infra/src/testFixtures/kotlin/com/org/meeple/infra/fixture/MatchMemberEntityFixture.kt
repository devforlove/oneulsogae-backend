package com.org.meeple.infra.fixture

import com.org.meeple.common.match.MatchMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.match.command.entity.MatchMemberEntity

/**
 * [MatchMemberEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 기본은 갓 참가한(미응답) 활성 남성 참가자다. (matchId·userId·gender·accepted·status를 필요에 맞게 지정)
 */
object MatchMemberEntityFixture {

	fun create(
		matchId: Long = 1L,
		userId: Long = 1L,
		gender: Gender = Gender.MALE,
		accepted: Boolean? = null,
		status: MatchMemberStatus = MatchMemberStatus.ACTIVE,
	): MatchMemberEntity =
		MatchMemberEntity(
			matchId = matchId,
			userId = userId,
			gender = gender,
			accepted = accepted,
			status = status,
		)
}
