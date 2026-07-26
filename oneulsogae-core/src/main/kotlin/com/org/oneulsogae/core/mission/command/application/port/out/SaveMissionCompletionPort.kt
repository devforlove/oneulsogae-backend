package com.org.oneulsogae.core.mission.command.application.port.out

/**
 * 미션 완료 가드 기록 저장 out-port.
 * (user_id, mission_id) 유니크라 이미 완료했으면 저장 시 DataIntegrityViolationException이 발생한다 —
 * 이 위반이 이중 수령을 원자적으로 막는 최종 방어선이다.
 */
interface SaveMissionCompletionPort {

	fun save(userId: Long, missionId: Long, rewardedCoin: Int)
}
