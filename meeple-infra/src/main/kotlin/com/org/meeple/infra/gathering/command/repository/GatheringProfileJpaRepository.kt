package com.org.meeple.infra.gathering.command.repository

import com.org.meeple.infra.gathering.command.entity.GatheringProfileEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 모임 프로필(gathering_profile) 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.gathering.command.adapter.GatheringProfileAdapter]가 구현한다.
 */
interface GatheringProfileJpaRepository : JpaRepository<GatheringProfileEntity, Long> {

	/** 유저의 모임 프로필 1건. (user_id 유니크 — upsert 판단용) */
	fun findByUserId(userId: Long): GatheringProfileEntity?

	/** 유저의 모임 프로필(회원 인증 승인) 존재 여부. */
	fun existsByUserId(userId: Long): Boolean
}
