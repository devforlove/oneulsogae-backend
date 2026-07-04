package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.SaveCompanyImageVerificationPort
import com.org.meeple.core.user.command.domain.CompanyImageVerification
import com.org.meeple.infra.user.command.mapper.toDomain
import com.org.meeple.infra.user.command.mapper.toEntity
import com.org.meeple.infra.user.command.repository.CompanyImageVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * 직장 서류 이미지 인증 아웃포트([SaveCompanyImageVerificationPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class CompanyImageVerificationRepositoryAdapter(
	private val companyImageVerificationJpaRepository: CompanyImageVerificationJpaRepository,
) : SaveCompanyImageVerificationPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(verification: CompanyImageVerification): CompanyImageVerification =
		companyImageVerificationJpaRepository.save(verification.toEntity()).toDomain()
}
