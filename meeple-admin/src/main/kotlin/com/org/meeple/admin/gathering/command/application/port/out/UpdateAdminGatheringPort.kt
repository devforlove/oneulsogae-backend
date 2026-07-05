package com.org.meeple.admin.gathering.command.application.port.out

import com.org.meeple.admin.gathering.command.domain.AdminGathering

/**
 * 모임 전체 수정 저장 out-port. 기존 행의 데이터 필드를 [gathering] 값으로 덮어쓴다. (status·생성 시각은 보존)
 * infra 어댑터가 구현한다.
 */
fun interface UpdateAdminGatheringPort {
	fun update(gathering: AdminGathering)
}
