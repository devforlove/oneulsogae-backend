package com.org.meeple.core.user.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.application.port.`in`.GetUserByIdUseCase
import com.org.meeple.core.user.application.port.out.GetUserPort
import com.org.meeple.core.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserByIdUseCase] 구현.
 * 조회 아웃포트([GetUserPort])에만 의존한다.
 */
@Service
class GetUserByIdService(
	private val getUserPort: GetUserPort,
) : GetUserByIdUseCase {

	/** id로 사용자를 조회한다. 없으면 [UserErrorCode.USER_NOT_FOUND]. */
	@Transactional(readOnly = true)
	override fun getById(id: Long): User =
		getUserPort.findById(id)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $id")
}
