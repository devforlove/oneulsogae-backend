package com.org.meeple.core.matchuser.command.application

import com.org.meeple.core.common.event.MatchProfileSnapshot
import com.org.meeple.core.matchuser.command.application.port.`in`.SyncMatchUserUseCase
import com.org.meeple.core.matchuser.command.application.port.out.DeleteMatchUserPort
import com.org.meeple.core.matchuser.command.application.port.out.SaveMatchUserPort
import com.org.meeple.core.matchuser.command.domain.MatchUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [SyncMatchUserUseCase] 구현. 매칭 읽기 모델(match_user)을 소유한 match 도메인이 자기 out-port로 적재/삭제/갱신한다.
 * 매칭 가능 여부 판단·스냅샷 생성은 user 도메인이 맡고, 이 서비스는 그 결과를 자기 읽기 모델에 반영하기만 한다.
 */
@Service
@Transactional
class SyncMatchUserService(
	private val saveMatchUserPort: SaveMatchUserPort,
	private val deleteMatchUserPort: DeleteMatchUserPort,
) : SyncMatchUserUseCase {

	override fun sync(userId: Long, snapshot: MatchProfileSnapshot?) {
		if (snapshot != null) {
			saveMatchUserPort.save(MatchUser.from(userId, snapshot))
		} else {
			deleteMatchUserPort.deleteByUserId(userId)
		}
	}

	override fun updateLastLogin(userId: Long, lastLoginAt: LocalDateTime) {
		saveMatchUserPort.updateLastLoginAt(userId, lastLoginAt)
	}
}
