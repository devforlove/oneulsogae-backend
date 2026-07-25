package com.org.oneulsogae.infra.user.command.repository

import com.org.oneulsogae.infra.user.command.entity.UniversityImageVerificationEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 학교 서류 이미지 인증 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.oneulsogae.infra.user.command.adapter.UniversityImageVerificationRepositoryAdapter]가 구현한다.
 */
interface UniversityImageVerificationJpaRepository : JpaRepository<UniversityImageVerificationEntity, Long>
