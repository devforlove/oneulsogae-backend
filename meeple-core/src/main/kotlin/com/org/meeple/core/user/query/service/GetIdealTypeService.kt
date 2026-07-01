package com.org.meeple.core.user.query.service

import com.org.meeple.core.user.query.dao.GetIdealTypeDao
import com.org.meeple.core.user.query.dto.IdealTypeView
import com.org.meeple.core.user.query.service.port.`in`.GetIdealTypeUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetIdealTypeUseCase] 구현. 조회 dao([GetIdealTypeDao])에만 의존한다. */
@Service
class GetIdealTypeService(
	private val getIdealTypeDao: GetIdealTypeDao,
) : GetIdealTypeUseCase {

	@Transactional(readOnly = true)
	override fun findByUserId(userId: Long): IdealTypeView? =
		getIdealTypeDao.findByUserId(userId)
}
