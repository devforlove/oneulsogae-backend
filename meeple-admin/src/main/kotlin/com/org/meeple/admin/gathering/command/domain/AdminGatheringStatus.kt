package com.org.meeple.admin.gathering.command.domain

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.common.gathering.GatheringStatus

/**
 * 모임 상태 전이 판정용 최소 도메인 모델(명령 측). 상태 전이에 필요한 id·status만 담는다.
 * (상태 변경은 전체 모임을 로드하지 않고 이 모델로 규칙만 판정한다)
 */
data class AdminGatheringStatus(
	val id: Long,
	val status: GatheringStatus,
) {
	/**
	 * [target] 상태로의 전이가 가능한지 판정한다. (활성화·취소만 지원)
	 * - RECRUITING(활성화): 준비중(DRAFT)에서만.
	 * - CANCELED(취소): 준비중·모집중·모집마감에서. (종료·이미 취소된 모임은 불가)
	 * - 그 외 target: 이 경로에서 지원하지 않음.
	 * 불가하면 GATHERING_INVALID_STATUS_TRANSITION을 던진다.
	 */
	fun changeTo(target: GatheringStatus) {
		val allowed: Boolean = when (target) {
			GatheringStatus.RECRUITING -> status == GatheringStatus.DRAFT
			GatheringStatus.CANCELED -> status in CANCELABLE_FROM
			else -> false
		}
		if (!allowed) {
			throw AdminException(
				AdminErrorCode.GATHERING_INVALID_STATUS_TRANSITION,
				"모임 상태를 $status 에서 $target (으)로 전이할 수 없습니다: $id",
			)
		}
	}

	companion object {
		private val CANCELABLE_FROM: Set<GatheringStatus> =
			setOf(GatheringStatus.DRAFT, GatheringStatus.RECRUITING, GatheringStatus.CLOSED)
	}
}
