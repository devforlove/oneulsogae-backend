package com.org.meeple.infra.user.repository

import com.org.meeple.infra.user.entity.CompanyEmailVerificationEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 회사 이메일 인증 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.user.adapter.CompanyEmailVerificationRepositoryAdapter]가 구현한다.
 */
interface CompanyEmailVerificationJpaRepository : JpaRepository<CompanyEmailVerificationEntity, Long> {

	/**
	 * 해당 사용자의 가장 최근 인증 요청 1건. (id가 단조 증가하므로 id 내림차순 첫 행이 최신)
	 * 만료 여부는 조회 후 서비스에서 판단한다.
	 */
	fun findFirstByUserIdOrderByIdDesc(userId: Long): CompanyEmailVerificationEntity?
}
