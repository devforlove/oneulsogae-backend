package com.org.oneulsogae.admin.universityverification.command.application.port.out

/** 유저의 학교명을 갱신하는 out-port. (승인 시 어드민이 기입한 학교명을 프로필에 확정한다) */
fun interface UpdateUserUniversityNamePort {

	fun updateUniversityName(userId: Long, universityName: String)
}
