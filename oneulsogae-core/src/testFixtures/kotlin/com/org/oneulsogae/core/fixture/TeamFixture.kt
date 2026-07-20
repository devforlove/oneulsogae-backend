package com.org.oneulsogae.core.fixture

import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.teammatch.command.domain.Team
import com.org.oneulsogae.core.teammatch.command.domain.TeamMember
import com.org.oneulsogae.core.teammatch.command.domain.TeamMembers

/**
 * [Team] 도메인 모델 테스트 픽스처. 기본은 초대중(INVITING) 상태의, 같은 성별 두 구성원으로 결성된 팀이다.
 * 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 */
object TeamFixture {

	fun create(
		id: Long = 0,
		name: String = "우리팀",
		gender: Gender = Gender.MALE,
		regionId: Long = 1L,
		introduction: String? = "잘 부탁드려요",
		members: TeamMembers = membersOf(),
		status: TeamStatus = TeamStatus.INVITING,
	): Team =
		Team(
			id = id,
			name = name,
			gender = gender,
			regionId = regionId,
			introduction = introduction,
			members = members,
			status = status,
		)

	/** 두 구성원 묶음. (성별은 팀 단위로 [Team.gender]가 보관) */
	fun membersOf(
		ownerUserId: Long = 1L,
		invitedUserId: Long = 2L,
	): TeamMembers =
		TeamMembers(
			listOf(
				TeamMember(teamId = 0, userId = ownerUserId),
				TeamMember(teamId = 0, userId = invitedUserId),
			),
		)
}
