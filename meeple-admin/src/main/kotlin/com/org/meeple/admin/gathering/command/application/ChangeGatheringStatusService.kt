package com.org.meeple.admin.gathering.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.gathering.command.application.port.`in`.ChangeGatheringStatusUseCase
import com.org.meeple.admin.gathering.command.application.port.out.ChangeAdminGatheringStatusPort
import com.org.meeple.admin.gathering.command.application.port.out.GetAdminGatheringPort
import com.org.meeple.admin.gathering.command.domain.AdminGatheringStatus
import com.org.meeple.common.gathering.GatheringStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ChangeGatheringStatusUseCase] 구현. (명령)
 * 대상 모임을 로드해 없으면 GATHERING_NOT_FOUND. 전이 가능 여부는 도메인이 판정하고, 통과 시 status를 전이한다.
 * (활성화 RECRUITING·취소 CANCELED를 하나의 경로로 처리)
 */
@Service
@Transactional
class ChangeGatheringStatusService(
	private val getAdminGatheringPort: GetAdminGatheringPort,
	private val changeAdminGatheringStatusPort: ChangeAdminGatheringStatusPort,
) : ChangeGatheringStatusUseCase {

	override fun changeStatus(id: Long, status: GatheringStatus) {
		val gathering: AdminGatheringStatus = getAdminGatheringPort.findById(id)
			?: throw AdminException(AdminErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: $id")
		gathering.changeTo(status)
		changeAdminGatheringStatusPort.changeStatus(id, status)
	}
}
