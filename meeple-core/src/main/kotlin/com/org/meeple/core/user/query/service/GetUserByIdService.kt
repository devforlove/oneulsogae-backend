package com.org.meeple.core.user.query.service

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.query.dao.GetUserDao
import com.org.meeple.core.user.query.dto.UserView
import com.org.meeple.core.user.query.service.port.`in`.GetUserByIdUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserByIdUseCase] 구현. 조회 dao([GetUserDao])에만 의존한다.
 */
@Service
class GetUserByIdService(
	private val getUserDao: GetUserDao,
) : GetUserByIdUseCase {

	/** id로 사용자를 조회한다. 없으면 [UserErrorCode.USER_NOT_FOUND]. */
	@Transactional(readOnly = true)
	override fun getById(id: Long): UserView =
		getUserDao.findById(id)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $id")
}
