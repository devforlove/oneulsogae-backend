package com.org.meeple.core.teammatch.command.application.port.`in`.command

/**
 * 팀 초대(결성) 명령. (초대자는 인증 사용자라 ownerId는 유스케이스 인자로 따로 받는다)
 * [invitedUserId]를 구성원으로 합류시켜 [name]/[introduction]의 팀을 결성한다.
 */
data class InviteTeamCommand(
	val invitedUserId: Long,
	val name: String,
	val introduction: String,
	val regionId: Long,
)
