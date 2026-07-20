package com.org.oneulsogae.admin.memberverification.command.application.port.out

/** 멤버 인증 승인 시 유저 프로필(user_details)의 회사명을 확정하는 out-port. */
fun interface UpdateUserCompanyNamePort {

	fun updateCompanyName(userId: Long, companyName: String)
}
