package com.org.meeple.core.match.command.application

import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.command.application.port.`in`.RemoveMatchUseCase
import com.org.meeple.core.match.command.application.port.out.DeleteMatchPort
import com.org.meeple.core.match.command.application.port.out.GetMatchPort
import com.org.meeple.core.match.command.domain.Match
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [RemoveMatchUseCase] 구현.
 * 매칭을 조회한 뒤 도메인에서 소프트 삭제 상태로 전이([Match.delete])해 저장한다. (조회 → 제거 → 저장 흐름은 유스케이스가 들고, 아웃포트는 영속화만 한다)
 * 이미 없으면(소프트 삭제 포함) 조회 결과가 null이라 멱등 no-op이다.
 * 미성사 매칭의 코인 환불은 이 경로에서 동기로 처리하지 않고 별도 배치에서 일괄 처리한다.
 */
@Service
@Transactional
class RemoveMatchService(
	private val getMatchPort: GetMatchPort,
	private val deleteMatchPort: DeleteMatchPort,
	private val timeGenerator: TimeGenerator,
) : RemoveMatchUseCase {

	override fun remove(matchId: Long) {
		val match: Match = getMatchPort.findById(matchId) ?: return
		deleteMatchPort.delete(match.delete(timeGenerator.now()))
	}
}
