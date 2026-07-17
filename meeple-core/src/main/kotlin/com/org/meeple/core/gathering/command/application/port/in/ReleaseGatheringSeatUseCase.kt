package com.org.meeple.core.gathering.command.application.port.`in`

/**
 * 확보한 좌석을 되돌리는 인포트. 결제완료(payments)가 PG 승인 실패 시 보상으로 호출한다.
 * 방금 접수한 참가를 취소(CANCELED)하고 차감했던 일정 여분(성별·얼리버드)을 복원한다.
 */
interface ReleaseGatheringSeatUseCase {

	fun release(scheduleId: Long, userId: Long)
}
