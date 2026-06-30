package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.matchuser.command.application.port.`in`.SyncMatchUserUseCase
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.`in`.WithdrawUserUseCase
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.application.port.out.RevokeUserTokensPort
import com.org.meeple.core.user.command.application.port.out.SoftDeleteUserPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [WithdrawUserUseCase] 구현.
 * 계정을 소프트삭제(비활성)하되 프로필·코인 등 데이터는 보존한다(10일 유예 후 배치가 파기).
 * 보안을 위해 토큰을 폐기하고, 매칭 읽기 모델(match_user)에서 즉시 제거해 매칭 풀에서 빠지게 한다.
 */
@Service
class WithdrawUserService(
	private val getUserPort: GetUserPort,
	private val softDeleteUserPort: SoftDeleteUserPort,
	private val revokeUserTokensPort: RevokeUserTokensPort,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
	private val timeGenerator: TimeGenerator,
) : WithdrawUserUseCase {

	@Transactional
	override fun withdraw(userId: Long) {
		// 이미 탈퇴(소프트삭제)한 계정은 조회되지 않으므로 USER_NOT_FOUND로 자연 차단된다.
		getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		val now: LocalDateTime = timeGenerator.now()
		softDeleteUserPort.softDelete(userId, now)
		revokeUserTokensPort.revokeAll(userId)
		syncMatchUserUseCase.sync(userId, null)
	}
}
