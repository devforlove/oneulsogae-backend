package com.org.oneulsogae.core.user.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.query.dao.GetUserDetailDao
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetUserDetailUseCase] 구현. 조회 dao([GetUserDetailDao])에만 의존한다.
 */
@Service
class GetUserDetailService(
	private val getUserDetailDao: GetUserDetailDao,
) : GetUserDetailUseCase {

	/** userId로 프로필 상세를 조회한다. 없으면 [UserErrorCode.USER_DETAIL_NOT_FOUND]. */
	@Transactional(readOnly = true)
	override fun getByUserId(userId: Long): UserDetailView =
		getUserDetailDao.findByUserId(userId)
			?: throw BusinessException(UserErrorCode.USER_DETAIL_NOT_FOUND, "사용자 프로필을 찾을 수 없습니다: $userId")

	@Transactional(readOnly = true)
	override fun findByUserId(userId: Long): UserDetailView? =
		getUserDetailDao.findByUserId(userId)
}
