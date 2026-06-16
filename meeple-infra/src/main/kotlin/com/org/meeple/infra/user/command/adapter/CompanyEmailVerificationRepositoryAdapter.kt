package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.GetCompanyEmailVerificationPort
import com.org.meeple.core.user.command.application.port.out.SaveCompanyEmailVerificationPort
import com.org.meeple.core.user.command.domain.CompanyEmailVerification
import com.org.meeple.infra.user.command.mapper.toDomain
import com.org.meeple.infra.user.command.mapper.toEntity
import com.org.meeple.infra.user.command.repository.CompanyEmailVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * 회사 이메일 인증 아웃포트([GetCompanyEmailVerificationPort], [SaveCompanyEmailVerificationPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class CompanyEmailVerificationRepositoryAdapter(
	private val companyEmailVerificationJpaRepository: CompanyEmailVerificationJpaRepository,
) : GetCompanyEmailVerificationPort, SaveCompanyEmailVerificationPort {

	// 가장 최근 1건만 조회한다. 만료 여부는 서비스가 판단한다.
	override fun findLatestByUserId(userId: Long): CompanyEmailVerification? =
		companyEmailVerificationJpaRepository.findFirstByUserIdOrderByIdDesc(userId)?.toDomain()

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(verification: CompanyEmailVerification): CompanyEmailVerification =
		companyEmailVerificationJpaRepository.save(verification.toEntity()).toDomain()
}
