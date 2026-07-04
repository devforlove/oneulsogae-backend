package com.org.meeple.core.admin.query.service

import com.org.meeple.core.admin.query.dao.GetAdminDashboardDao
import com.org.meeple.core.admin.query.dto.AdminDashboardView
import com.org.meeple.core.admin.query.service.port.`in`.GetAdminDashboardUseCase
import com.org.meeple.core.common.time.TimeGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetAdminDashboardUseCase] 구현. 금일 경계(00시)를 계산해 조회 dao([GetAdminDashboardDao])에만 의존한다. */
@Service
class GetAdminDashboardService(
	private val getAdminDashboardDao: GetAdminDashboardDao,
	private val timeGenerator: TimeGenerator,
) : GetAdminDashboardUseCase {

	@Transactional(readOnly = true)
	override fun get(): AdminDashboardView =
		getAdminDashboardDao.load(timeGenerator.today().atStartOfDay())
}
