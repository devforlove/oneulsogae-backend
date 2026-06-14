package com.org.meeple.core.user.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.application.port.`in`.GetUserDetailUseCase
import com.org.meeple.core.user.application.port.out.GetUserDetailPort
import com.org.meeple.core.user.domain.UserDetail
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserDetailUseCase] 구현.
 * 조회 아웃포트([GetUserDetailPort])에만 의존한다.
 */
@Service
class GetUserDetailService(
	private val getUserDetailPort: GetUserDetailPort,
) : GetUserDetailUseCase {

	/** userId로 프로필 상세를 조회한다. 없으면 [UserErrorCode.USER_DETAIL_NOT_FOUND]. */
	@Transactional(readOnly = true)
	override fun getByUserId(userId: Long): UserDetail =
		getUserDetailPort.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")

	@Transactional(readOnly = true)
	override fun findByUserId(userId: Long): UserDetail? =
		getUserDetailPort.findByUserId(userId)
}
