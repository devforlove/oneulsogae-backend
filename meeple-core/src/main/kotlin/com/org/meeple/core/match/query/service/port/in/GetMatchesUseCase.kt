package com.org.meeple.core.match.query.service.port.`in`

import com.org.meeple.core.match.query.dto.MatchWithPartner

/**
 * 내 매칭 목록 조회 인포트(유스케이스).
 * 해당 사용자가 참가한 모든 매칭(진행중/성사/거절)을 상대방 프로필과 함께 반환한다.
 * [isAfterOnboarding]가 true(온보딩 직후 첫 진입)인 경우에 한해, 오늘 소개가 없으면 신규 매칭을 1건 만들어 준다.
 */
interface GetMatchesUseCase {

	fun getMatches(userId: Long, isAfterOnboarding: Boolean): List<MatchWithPartner>
}
