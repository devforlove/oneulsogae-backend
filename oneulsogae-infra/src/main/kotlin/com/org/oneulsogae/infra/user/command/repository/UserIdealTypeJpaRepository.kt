package com.org.oneulsogae.infra.user.command.repository

import com.org.oneulsogae.infra.user.command.entity.UserIdealTypeEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 사용자 이상형 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.oneulsogae.infra.user.command.adapter.UserIdealTypeCoreAdapter]가 구현하고,
 * 조회 read model 투영은 [com.org.oneulsogae.infra.user.query.GetIdealTypeDaoImpl]가 이 리포지토리를 재사용한다.
 */
interface UserIdealTypeJpaRepository : JpaRepository<UserIdealTypeEntity, Long> {

	fun findByUserId(userId: Long): UserIdealTypeEntity?
}
