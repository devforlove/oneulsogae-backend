package com.org.meeple.core.gathering.query.service

import com.org.meeple.core.gathering.query.dao.GetMemberVerificationDao
import com.org.meeple.core.gathering.query.dto.MemberVerificationView
import com.org.meeple.core.gathering.query.service.port.`in`.GetMyMemberVerificationUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetMyMemberVerificationUseCase] 구현.
 * 유저의 최신 멤버 인증 제출 1건을 조회한다. (없으면 null)
 */
@Service
class GetMyMemberVerificationService(
	private val getMemberVerificationDao: GetMemberVerificationDao,
) : GetMyMemberVerificationUseCase {

	@Transactional(readOnly = true)
	override fun findLatest(userId: Long): MemberVerificationView? =
		getMemberVerificationDao.findLatestByUserId(userId)
}
