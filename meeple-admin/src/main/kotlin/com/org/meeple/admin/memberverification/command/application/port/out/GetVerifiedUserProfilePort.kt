package com.org.meeple.admin.memberverification.command.application.port.out

/** 승인 시 gathering_profile 스냅샷에 담을 유저 프로필 소스(생일·키)를 조회하는 out-port. */
fun interface GetVerifiedUserProfilePort {

	/** [userId]의 프로필 소스를 조회한다. user_details 행이 없으면 null. */
	fun findProfileSource(userId: Long): VerifiedUserProfile?
}
