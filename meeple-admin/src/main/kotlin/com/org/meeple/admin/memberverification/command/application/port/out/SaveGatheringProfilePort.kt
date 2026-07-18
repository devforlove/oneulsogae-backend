package com.org.meeple.admin.memberverification.command.application.port.out

import java.time.LocalDate

/**
 * 유저의 모임 프로필(gathering_profile)을 저장하는 out-port. (유저당 1건 upsert)
 * 멤버 인증 승인 시 어드민이 확정한 직종·직장 상세와, user_details에서 가져온 생일·키를 함께 담는다.
 * (나이는 저장하지 않고 조회 시 [birthday]로부터 계산한다)
 */
fun interface SaveGatheringProfilePort {

	fun save(userId: Long, jobCategory: String, jobDetail: String, birthday: LocalDate?, height: Int?)
}
