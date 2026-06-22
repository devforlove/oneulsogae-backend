package com.org.meeple.core.match

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 팀(2:2 매칭) 도메인 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 * 1:1 매칭 에러는 [MatchErrorCode]가 담당한다.
 */
enum class TeamErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	CANNOT_INVITE_SELF("TEAM-001", "자기 자신을 팀에 초대할 수 없습니다.", HttpStatus.BAD_REQUEST),
	INVALID_TEAM_NAME("TEAM-002", "팀 이름이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
	INVALID_TEAM_INTRODUCTION("TEAM-003", "팀 소개가 너무 깁니다.", HttpStatus.BAD_REQUEST),
	MUST_INVITE_SAME_GENDER("TEAM-004", "같은 성별만 팀에 초대할 수 있습니다.", HttpStatus.BAD_REQUEST),
	TEAM_NOT_FOUND("TEAM-005", "팀을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	NOT_TEAM_MEMBER("TEAM-006", "해당 팀의 구성원이 아닙니다.", HttpStatus.FORBIDDEN),
	NOT_INVITED_MEMBER("TEAM-007", "초대를 받은 구성원만 수락할 수 있습니다.", HttpStatus.BAD_REQUEST),
	INVALID_TEAM_STATUS("TEAM-008", "현재 팀 상태에서 할 수 없는 작업입니다.", HttpStatus.CONFLICT),
	ALREADY_IN_TEAM("TEAM-009", "이미 다른 팀에 속해 있습니다.", HttpStatus.CONFLICT),
	INVALID_TEAM_REGION("TEAM-010", "지원하지 않는 활동지역입니다.", HttpStatus.BAD_REQUEST),
}
