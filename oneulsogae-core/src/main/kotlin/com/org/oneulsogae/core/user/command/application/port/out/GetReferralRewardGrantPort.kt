package com.org.oneulsogae.core.user.command.application.port.out

/** 추천 보상 지급 이력 조회 out-port. */
interface GetReferralRewardGrantPort {

	/** 이 DI 해시로 추천 보상이 이미 지급된 적이 있는지 여부. (탈퇴·재가입해도 DI는 같아 재지급을 막는다) */
	fun existsByReferredDiHash(referredDiHash: String): Boolean
}
