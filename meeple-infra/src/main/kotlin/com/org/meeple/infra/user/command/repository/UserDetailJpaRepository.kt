package com.org.meeple.infra.user.command.repository

import com.org.meeple.infra.user.command.entity.UserDetailEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 사용자 프로필 상세 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.user.adapter.UserDetailCoreAdapter]가 구현한다.
 */
interface UserDetailJpaRepository : JpaRepository<UserDetailEntity, Long> {

	fun findByUserId(userId: Long): UserDetailEntity?
}
