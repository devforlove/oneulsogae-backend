package com.org.meeple.admin.companyverification.command.application.port.out

/** 유저의 회사명을 갱신하는 out-port. (승인 시 어드민이 기입한 회사명을 프로필에 확정한다) */
fun interface UpdateUserCompanyNamePort {

	fun updateCompanyName(userId: Long, companyName: String)
}
