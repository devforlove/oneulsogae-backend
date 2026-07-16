package com.org.meeple.admin.gathering.query.service

import com.org.meeple.admin.gathering.query.dao.GetAdminGatheringMemberDao
import com.org.meeple.admin.gathering.query.dto.AdminGatheringMemberViews
import com.org.meeple.admin.gathering.query.service.port.`in`.GetAdminGatheringMembersUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetAdminGatheringMembersUseCase] 구현. (조회 전용) */
@Service
@Transactional(readOnly = true)
class GetAdminGatheringMembersService(
	private val getAdminGatheringMemberDao: GetAdminGatheringMemberDao,
) : GetAdminGatheringMembersUseCase {

	override fun getByScheduleId(scheduleId: Long): AdminGatheringMemberViews =
		getAdminGatheringMemberDao.findByScheduleId(scheduleId)
}
