package com.org.meeple.infra.user.command.repository

import com.org.meeple.infra.user.command.entity.CompanyImageVerificationEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 직장 서류 이미지 인증 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.user.command.adapter.CompanyImageVerificationRepositoryAdapter]가 구현한다.
 */
interface CompanyImageVerificationJpaRepository : JpaRepository<CompanyImageVerificationEntity, Long>
