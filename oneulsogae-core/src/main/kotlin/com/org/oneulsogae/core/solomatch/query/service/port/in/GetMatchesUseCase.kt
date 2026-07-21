package com.org.oneulsogae.core.solomatch.query.service.port.`in`

import com.org.oneulsogae.core.solomatch.query.dto.MyMatches

/**
 * 내 매칭 목록 조회 인포트(유스케이스).
 * 해당 사용자가 참가한 모든 매칭(진행중/성사/거절)을 상대방 프로필과 함께 반환하고, 조회한 사용자의 회사 인증 여부를 같이 담는다.
 */
interface GetMatchesUseCase {

	fun getMatches(userId: Long): MyMatches
}
