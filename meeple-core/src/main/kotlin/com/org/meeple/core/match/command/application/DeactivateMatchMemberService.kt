package com.org.meeple.core.match.command.application

import com.org.meeple.core.match.command.application.port.`in`.DeactivateMatchMemberUseCase
import com.org.meeple.core.match.command.application.port.out.GetMatchPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchPort
import com.org.meeple.core.match.command.domain.Match
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [DeactivateMatchMemberUseCase] 구현.
 * 매칭을 조회해 해당 참가자만 비활성([Match.deactivateMember]) 전이한 뒤 저장한다. (매칭 자체는 유지)
 * 매칭이 이미 없으면(소프트 삭제 포함) 조회 결과가 null이라 멱등 no-op이다.
 */
@Service
@Transactional
class DeactivateMatchMemberService(
	private val getMatchPort: GetMatchPort,
	private val saveMatchPort: SaveMatchPort,
) : DeactivateMatchMemberUseCase {

	override fun deactivate(matchId: Long, userId: Long) {
		val match: Match = getMatchPort.findById(matchId) ?: return
		saveMatchPort.save(match.deactivateMember(userId))
	}
}
