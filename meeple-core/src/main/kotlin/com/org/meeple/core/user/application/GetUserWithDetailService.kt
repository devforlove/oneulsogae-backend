package com.org.meeple.core.user.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.application.port.`in`.GetUserWithDetailUseCase
import com.org.meeple.core.user.application.port.out.GetUserWithDetailPort
import com.org.meeple.core.user.domain.UserWithDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserWithDetailUseCase] 구현.
 * 사용자+프로필 조인 조회 아웃포트([GetUserWithDetailPort])에 위임하고, 없으면 [UserErrorCode.USER_DETAIL_NOT_FOUND]를 던진다.
 */
@Service
class GetUserWithDetailService(
	private val getUserWithDetailPort: GetUserWithDetailPort,
) : GetUserWithDetailUseCase {

	@Transactional(readOnly = true)
	override fun getByUserId(userId: Long): UserWithDetail =
		getUserWithDetailPort.findWithDetailByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")
}
