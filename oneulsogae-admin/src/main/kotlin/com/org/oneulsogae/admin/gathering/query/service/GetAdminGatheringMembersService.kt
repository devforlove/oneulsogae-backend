package com.org.oneulsogae.admin.gathering.query.service

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.query.dao.GetAdminGatheringMemberDao
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberDetailView
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringMemberPage
import com.org.oneulsogae.admin.gathering.query.service.port.`in`.GetAdminGatheringMembersUseCase
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** [GetAdminGatheringMembersUseCase] 구현. (조회 전용) */
@Service
@Transactional(readOnly = true)
class GetAdminGatheringMembersService(
	private val getAdminGatheringMemberDao: GetAdminGatheringMemberDao,
) : GetAdminGatheringMembersUseCase {

	override fun getMembers(
		scheduleId: Long,
		page: Int,
		size: Int,
		status: GatheringMemberStatus?,
	): AdminGatheringMemberPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize

		return AdminGatheringMemberPage(
			content = getAdminGatheringMemberDao.findPage(scheduleId, offset, pageSize, status),
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminGatheringMemberDao.count(scheduleId, status),
		)
	}

	override fun searchMembers(page: Int, size: Int, status: GatheringMemberStatus?): AdminGatheringMemberPage {
		val pageNumber: Int = page.coerceAtLeast(0)
		val pageSize: Int = size.coerceIn(1, MAX_PAGE_SIZE)
		val offset: Long = pageNumber.toLong() * pageSize

		return AdminGatheringMemberPage(
			content = getAdminGatheringMemberDao.findPage(null, offset, pageSize, status),
			page = pageNumber,
			size = pageSize,
			totalElements = getAdminGatheringMemberDao.count(null, status),
		)
	}

	override fun getMemberProfile(scheduleId: Long, memberId: Long): AdminGatheringMemberDetailView =
		getAdminGatheringMemberDao.findMemberProfile(scheduleId, memberId)
			?: throw AdminException(
				AdminErrorCode.GATHERING_MEMBER_NOT_FOUND,
				"모임 참가 신청을 찾을 수 없습니다: $memberId",
			)

	companion object {
		private const val MAX_PAGE_SIZE: Int = 100
	}
}
