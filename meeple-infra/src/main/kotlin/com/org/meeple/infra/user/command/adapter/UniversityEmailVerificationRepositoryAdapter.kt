package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.GetUniversityEmailVerificationPort
import com.org.meeple.core.user.command.application.port.out.SaveUniversityEmailVerificationPort
import com.org.meeple.core.user.command.domain.UniversityEmailVerification
import com.org.meeple.infra.user.command.mapper.toDomain
import com.org.meeple.infra.user.command.mapper.toEntity
import com.org.meeple.infra.user.command.repository.UniversityEmailVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * 학교 이메일 인증 아웃포트([GetUniversityEmailVerificationPort], [SaveUniversityEmailVerificationPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class UniversityEmailVerificationRepositoryAdapter(
	private val universityEmailVerificationJpaRepository: UniversityEmailVerificationJpaRepository,
) : GetUniversityEmailVerificationPort, SaveUniversityEmailVerificationPort {

	// 가장 최근 1건만 조회한다. 만료 여부는 서비스가 판단한다.
	override fun findLatestByUserId(userId: Long): UniversityEmailVerification? =
		universityEmailVerificationJpaRepository.findFirstByUserIdOrderByIdDesc(userId)?.toDomain()

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(verification: UniversityEmailVerification): UniversityEmailVerification =
		universityEmailVerificationJpaRepository.save(verification.toEntity()).toDomain()
}
