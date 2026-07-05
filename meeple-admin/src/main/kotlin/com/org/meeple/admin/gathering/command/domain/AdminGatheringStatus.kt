package com.org.meeple.admin.gathering.command.domain

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.common.gathering.GatheringStatus

/**
 * 모임 상태 전이 판정용 최소 도메인 모델(명령 측). 상태 전이에 필요한 id·status만 담는다.
 * (활성화 등은 전체 모임을 로드하지 않고 이 모델로 규칙만 판정한다)
 */
data class AdminGatheringStatus(
	val id: Long,
	val status: GatheringStatus,
) {
	/**
	 * 활성화 가능 여부를 판정한다. 준비중(DRAFT)이 아니면(이미 활성화됐거나 마감/종료/취소) GATHERING_NOT_ACTIVATABLE.
	 * (활성화 시 저장 상태는 [GatheringStatus.RECRUITING]으로 전이한다 — 전이 대상이 상수라 이 판정만 도메인이 책임진다)
	 */
	fun validateActivatable() {
		if (status != GatheringStatus.DRAFT) {
			throw AdminException(AdminErrorCode.GATHERING_NOT_ACTIVATABLE, "준비중 상태만 활성화할 수 있습니다: $status")
		}
	}
}
