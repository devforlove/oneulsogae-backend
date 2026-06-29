package com.org.meeple.api.match.response

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.solomatch.command.domain.Match

/**
 * 매칭 상태만 담는 응답. (관심 보내기/수락 결과)
 * 상대 프로필 등 상세는 목록 조회([MatchResponse])에서 내려주고, 상태 변경 요청은 결과 상태만 반환한다.
 */
data class MatchStatusResponse(
	val matchStatus: MatchStatus,
) {
	companion object {
		fun of(match: Match): MatchStatusResponse =
			MatchStatusResponse(matchStatus = match.status)
	}
}
