package com.org.oneulsogae.core.matchuser.command.application

import com.org.oneulsogae.core.matchuser.command.application.port.`in`.GetMatchUserUseCase
import com.org.oneulsogae.core.matchuser.command.application.port.out.GetMatchUserPort
import com.org.oneulsogae.core.matchuser.command.domain.MatchUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetMatchUserUseCase] 구현. 매칭 읽기 모델(match_user)을 소유한 matchuser 도메인이 자기 out-port로 단건 조회한다.
 * 다른 매칭 도메인(solo·team)은 이 in-port로만 읽어 도메인 의존 방향(in-port 주입)을 지킨다.
 */
@Service
@Transactional(readOnly = true)
class GetMatchUserService(
	private val getMatchUserPort: GetMatchUserPort,
) : GetMatchUserUseCase {

	override fun findByUserId(userId: Long): MatchUser? =
		getMatchUserPort.findByUserId(userId)
}
