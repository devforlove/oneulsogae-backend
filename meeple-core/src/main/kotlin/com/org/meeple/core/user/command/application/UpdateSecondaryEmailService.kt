package com.org.meeple.core.user.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.application.port.`in`.UpdateSecondaryEmailUseCase
import com.org.meeple.core.user.command.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.command.application.port.out.SaveUserDetailPort
import com.org.meeple.core.user.command.domain.UserDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [UpdateSecondaryEmailUseCase] 구현.
 * 기존 프로필을 로드해 보조 이메일만 교체(또는 해제)하고 저장한다.
 * 보조 이메일은 매칭 읽기 모델에 투영되지 않는 알림용 필드이므로 [UserProfileChanged] 이벤트는 발행하지 않는다.
 */
@Service
class UpdateSecondaryEmailService(
	private val getUserDetailPort: GetUserDetailPort,
	private val saveUserDetailPort: SaveUserDetailPort,
) : UpdateSecondaryEmailUseCase {

	@Transactional
	override fun updateSecondaryEmail(userId: Long, secondaryEmail: String?): UserDetail {
		val existing: UserDetail = getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")

		val updated: UserDetail = existing.changeSecondaryEmail(secondaryEmail)
		return saveUserDetailPort.save(updated)
	}
}
