package com.org.oneulsogae.core.user.query.service

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.query.dao.GetUserDetailDao
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CheckCompanyVerifiedUseCase] 구현. 조회 dao([GetUserDetailDao])에만 의존한다.
 * "회사명이 채워졌는가"라는 인증 완료 판정을 이 클래스 한 곳에만 두어, 여러 도메인이 같은 규칙을 각자 인라인하지 않게 한다.
 */
@Service
@Transactional(readOnly = true)
class CheckCompanyVerifiedService(
	private val getUserDetailDao: GetUserDetailDao,
) : CheckCompanyVerifiedUseCase {

	override fun isCompanyVerified(userId: Long): Boolean {
		val detail: UserDetailView? = getUserDetailDao.findByUserId(userId)
		return detail?.companyName != null
	}

	override fun validateCompanyVerified(userId: Long) {
		if (!isCompanyVerified(userId)) {
			throw BusinessException(UserErrorCode.COMPANY_NOT_VERIFIED)
		}
	}
}
