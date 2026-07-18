package com.org.meeple.core.gathering.command.application.port.`in`

import java.time.LocalDate

/**
 * 유저 프로필 변경을 gathering_profile에 반영하는 유스케이스.
 * 멤버 인증 승인으로 생성된 모임 프로필의 유저 유래 필드(프로필이미지·생일·키)를 최신 값으로 맞춘다.
 * (직종·직장상세는 어드민 확정값이라 동기화 대상이 아니다. gathering_profile이 없으면 no-op)
 */
interface SyncGatheringProfileUseCase {

	fun sync(userId: Long, profileImageCode: String?, birthday: LocalDate?, height: Int?)
}
