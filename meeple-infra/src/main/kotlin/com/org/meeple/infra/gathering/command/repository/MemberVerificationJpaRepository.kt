package com.org.meeple.infra.gathering.command.repository

import com.org.meeple.infra.gathering.command.entity.MemberVerificationEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 멤버 인증(본인인증) 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.gathering.command.adapter.MemberVerificationRepositoryAdapter]가 구현한다.
 */
interface MemberVerificationJpaRepository : JpaRepository<MemberVerificationEntity, Long> {

	/** 유저의 최신 제출 1건. (user_id 필터 후 PK 내림차순 — idx_user_id 인덱스 seek) */
	fun findFirstByUserIdOrderByIdDesc(userId: Long): MemberVerificationEntity?
}
