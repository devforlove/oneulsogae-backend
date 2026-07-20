package com.org.meeple.infra.lounge.command.repository

import com.org.meeple.common.lounge.LoungePostType
import com.org.meeple.infra.lounge.command.entity.LoungePostEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

/**
 * 라운지 글 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.meeple.infra.lounge.command.adapter.LoungePostAdapter]가 구현한다.
 */
interface LoungePostJpaRepository : JpaRepository<LoungePostEntity, Long> {

	/** 유저가 [since] 이후에 등록한 해당 타입 글 수. (idx_user_id로 user_id를 seek한 뒤 타입·시각을 필터한다) */
	fun countByUserIdAndTypeAndCreatedAtAfter(userId: Long, type: LoungePostType, since: LocalDateTime): Int
}
