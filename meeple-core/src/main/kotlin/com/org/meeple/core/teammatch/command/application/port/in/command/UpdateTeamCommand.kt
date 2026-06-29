package com.org.meeple.core.teammatch.command.application.port.`in`.command

/**
 * 팀 정보 수정 명령. (수정자는 인증 사용자라 userId·teamId는 유스케이스 인자로 따로 받는다)
 * 팀의 표시 정보인 [name]/[introduction]/[regionId]를 전체 교체한다.
 */
data class UpdateTeamCommand(
	val name: String,
	val introduction: String,
	val regionId: Long,
)
