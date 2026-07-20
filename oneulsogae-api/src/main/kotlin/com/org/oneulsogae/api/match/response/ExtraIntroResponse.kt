package com.org.oneulsogae.api.match.response

import com.org.oneulsogae.core.solomatch.command.domain.Match

/** 추가 소개 결과. 생성된 매칭 id와 상대 userId. (상대 프로필은 매칭 목록 조회로 표시) */
data class ExtraIntroResponse(
	val matchId: Long,
	val partnerUserId: Long,
) {
	companion object {
		fun of(match: Match, requesterId: Long): ExtraIntroResponse =
			ExtraIntroResponse(matchId = match.id, partnerUserId = match.partnerOf(requesterId))
	}
}
