package com.org.meeple.infra.user.adapter

import com.org.meeple.core.user.application.port.out.GetUserCompanyPort
import com.org.meeple.core.user.domain.UserCompany
import com.org.meeple.infra.user.mapper.toDomain
import com.org.meeple.infra.user.repository.UserCompanyJpaRepository
import org.springframework.stereotype.Component

/**
 * 회사 매핑 아웃포트([GetUserCompanyPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환([UserCompanyMapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class UserCompanyRepositoryAdapter(
	private val userCompanyJpaRepository: UserCompanyJpaRepository,
) : GetUserCompanyPort {

	override fun findByEmailDomain(emailDomain: String): UserCompany? =
		userCompanyJpaRepository.findByEmailDomain(emailDomain)?.toDomain()
}
