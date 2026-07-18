package com.org.meeple.admin.memberverification.command.application.port.out

/**
 * 멤버 인증 승인 시 유저 프로필(user_details)에 확정 정보를 반영하는 out-port.
 * 어드민이 심사에서 확정한 회사명·직종·직장 상세를 프로필에 쓴다.
 */
fun interface UpdateUserVerifiedProfilePort {

	fun updateVerifiedProfile(userId: Long, companyName: String, jobCategory: String, jobDetail: String)
}
