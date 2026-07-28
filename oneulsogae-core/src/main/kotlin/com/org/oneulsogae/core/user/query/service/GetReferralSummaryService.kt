package com.org.oneulsogae.core.user.query.service

import com.org.oneulsogae.core.user.query.dao.GetReferralSummaryDao
import com.org.oneulsogae.core.user.query.dto.ReferralSummary
import com.org.oneulsogae.core.user.query.service.port.`in`.GetReferralSummaryUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetReferralSummaryUseCase] 구현. 조회 dao([GetReferralSummaryDao])에만 의존한다. (쓰기 부수효과 없음)
 */
@Service
@Transactional(readOnly = true)
class GetReferralSummaryService(
	private val getReferralSummaryDao: GetReferralSummaryDao,
) : GetReferralSummaryUseCase {

	override fun getSummary(userId: Long): ReferralSummary =
		getReferralSummaryDao.findSummary(userId)
}
