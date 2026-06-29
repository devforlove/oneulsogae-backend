package com.org.meeple.core.teammatch

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 팀 매칭(TeamMatch) 도메인 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class TeamMatchErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	TEAM_MATCH_NOT_FOUND("TEAM-MATCH-001", "팀 매칭을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	NOT_TEAM_MATCH_PARTICIPANT("TEAM-MATCH-002", "해당 팀 매칭의 참가 팀 구성원이 아닙니다.", HttpStatus.FORBIDDEN),
	TEAM_MATCH_ALREADY_CLOSED("TEAM-MATCH-003", "이미 종료된 팀 매칭입니다.", HttpStatus.CONFLICT),
	TEAM_MATCH_NOT_MATCHED("TEAM-MATCH-004", "성사된 팀 매칭만 종료할 수 있습니다.", HttpStatus.CONFLICT),
}
