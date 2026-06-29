package com.org.meeple.core.matchuser.command.application.port.`in`

import com.org.meeple.core.matchuser.command.domain.MatchUser

/**
 * 매칭 읽기 모델(match_user) 단건 조회 유스케이스(in-port).
 * 다른 매칭 도메인(solo·team)이 요청자의 매칭 가능 여부·후보 선정 기준 필드가 필요할 때 이 in-port로 읽는다. (없으면 매칭 불가)
 */
interface GetMatchUserUseCase {

	fun findByUserId(userId: Long): MatchUser?
}
