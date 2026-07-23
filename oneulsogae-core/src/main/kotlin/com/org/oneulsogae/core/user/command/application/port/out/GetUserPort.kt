package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.User

/**
 * 사용자 조회 아웃포트.
 * 도메인 모델([User])만을 반환하며, 실제 구현은 infra 레이어의 어댑터가 담당한다.
 */
interface GetUserPort {

	/** provider + providerId로 사용자를 조회한다. 없으면 null. */
	fun findByProviderAndProviderId(provider: String, providerId: String): User?

	/** id로 사용자를 조회한다. 없으면 null. */
	fun findById(id: Long): User?

	/** 추천 코드로 사용자를 조회한다. 없으면 null. */
	fun findByReferralCode(code: String): User?

	/** 해당 이메일을 쓰는 사용자가 이미 존재하는지 여부. */
	fun existsByEmail(email: String): Boolean

	/** 소프트삭제(탈퇴 유예중) 사용자를 원본 provider/providerId로 찾는다. 파기된 행은 provider_id가 치환돼 잡히지 않는다. */
	fun findWithdrawnUserId(provider: String, providerId: String): Long?
}
