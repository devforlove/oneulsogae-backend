package com.org.oneulsogae.core.gathering.command.application.port.out

import java.time.LocalDate

/**
 * gathering_profile의 유저 유래 필드(프로필이미지·생일·키)를 최신화하는 out-port. (유저 프로필 변경 이벤트 동기화용)
 * gathering_profile 행이 없으면(멤버 인증 미승인) no-op.
 */
fun interface UpdateGatheringProfilePort {

	fun updateUserFields(userId: Long, profileImageCode: String?, birthday: LocalDate?, height: Int?)
}
