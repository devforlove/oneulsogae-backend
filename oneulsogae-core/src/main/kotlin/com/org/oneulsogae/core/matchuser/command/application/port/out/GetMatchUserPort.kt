package com.org.oneulsogae.core.matchuser.command.application.port.out

import com.org.oneulsogae.core.matchuser.command.domain.MatchUser

/**
 * 매칭 읽기 모델(match_user) 단건 조회 아웃포트.
 * 요청자가 매칭 가능한지 판단하고 후보 선정에 쓸 기준 필드를 가져온다. (없으면 매칭 불가)
 */
interface GetMatchUserPort {

	fun findByUserId(userId: Long): MatchUser?
}
