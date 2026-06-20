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
}
