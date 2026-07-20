package com.org.oneulsogae.admin.gathering.command.application.port.out

/** 유저의 회원 인증(gathering_profile) 완료 여부를 조회하는 out-port. */
fun interface ExistsGatheringProfilePort {

	/** [userId]의 gathering_profile(멤버 인증 승인) 행이 있으면 true. */
	fun existsByUserId(userId: Long): Boolean
}
