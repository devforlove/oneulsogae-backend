package com.org.meeple.core.match.query.dto

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus

/**
 * 내가 보낸 초대 현황(read model). 초대자(owner)가 자신이 보낸 초대 팀의 메타와 구성원 현황을 본다.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class SentInvitation(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val status: TeamStatus,
	val members: List<SentInvitationMember>,
)

/** 초대 팀 구성원 한 명의 현황. status=ACTIVE는 초대자, INVITED는 수락 대기 중인 초대 대상. */
data class SentInvitationMember(
	val userId: Long,
	val status: TeamMemberStatus,
)
