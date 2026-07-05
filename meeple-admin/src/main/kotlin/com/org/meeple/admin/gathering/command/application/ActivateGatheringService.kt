package com.org.meeple.admin.gathering.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.gathering.command.application.port.`in`.ActivateGatheringUseCase
import com.org.meeple.admin.gathering.command.application.port.out.ActivateAdminGatheringPort
import com.org.meeple.admin.gathering.command.application.port.out.GetAdminGatheringPort
import com.org.meeple.admin.gathering.command.domain.AdminGatheringStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ActivateGatheringUseCase] 구현. (명령)
 * 대상 모임을 로드해 없으면 GATHERING_NOT_FOUND. 활성화 가능 여부(준비중)는 도메인이 판정하고,
 * 통과 시 status를 RECRUITING(모집중)으로 전이한다.
 */
@Service
@Transactional
class ActivateGatheringService(
	private val getAdminGatheringPort: GetAdminGatheringPort,
	private val activateAdminGatheringPort: ActivateAdminGatheringPort,
) : ActivateGatheringUseCase {

	override fun activate(id: Long) {
		val gathering: AdminGatheringStatus = getAdminGatheringPort.findById(id)
			?: throw AdminException(AdminErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: $id")
		gathering.validateActivatable()
		activateAdminGatheringPort.activate(id)
	}
}
