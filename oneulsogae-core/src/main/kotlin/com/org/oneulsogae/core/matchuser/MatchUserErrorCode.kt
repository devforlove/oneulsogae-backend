package com.org.oneulsogae.core.matchuser

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 매칭 가능 사용자(match_user 읽기 모델) 도메인 에러 코드.
 * [com.org.oneulsogae.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class MatchUserErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	PROFILE_INCOMPLETE("MATCH-005", "매칭을 위해 프로필(성별·닉네임·나이)을 먼저 완성해 주세요.", HttpStatus.BAD_REQUEST),
}
