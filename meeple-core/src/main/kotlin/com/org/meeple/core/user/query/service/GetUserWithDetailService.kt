package com.org.meeple.core.user.query.service

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.query.dao.GetUserWithDetailDao
import com.org.meeple.core.user.query.dto.UserWithDetailView
import com.org.meeple.core.user.query.service.port.`in`.GetUserWithDetailUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserWithDetailUseCase] 구현.
 * 사용자+프로필 조인 조회 dao([GetUserWithDetailDao])에 위임하고, 없으면 [UserErrorCode.USER_DETAIL_NOT_FOUND]를 던진다.
 */
@Service
class GetUserWithDetailService(
	private val getUserWithDetailDao: GetUserWithDetailDao,
) : GetUserWithDetailUseCase {

	@Transactional(readOnly = true)
	override fun getByUserId(userId: Long): UserWithDetailView =
		getUserWithDetailDao.findWithDetailByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")
}
