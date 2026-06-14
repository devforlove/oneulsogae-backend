package com.org.meeple.infra.user.repository

import com.org.meeple.infra.user.entity.UserCompanyEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 회사 매핑 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.user.adapter.UserCompanyRepositoryAdapter]가 구현한다.
 */
interface UserCompanyJpaRepository : JpaRepository<UserCompanyEntity, Long> {

	fun findByEmailDomain(emailDomain: String): UserCompanyEntity?
}
