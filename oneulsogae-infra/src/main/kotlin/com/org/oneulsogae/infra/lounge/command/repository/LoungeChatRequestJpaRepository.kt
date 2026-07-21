package com.org.oneulsogae.infra.lounge.command.repository

import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 라운지 대화 신청 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트는 [com.org.oneulsogae.infra.lounge.command.adapter.LoungeChatRequestAdapter]가 구현한다.
 */
interface LoungeChatRequestJpaRepository : JpaRepository<LoungeChatRequestEntity, Long> {

	/** 이 사용자가 이 글에 이미 신청했는지 여부. (ux_post_requester 유니크 인덱스로 seek) */
	fun existsByPostIdAndRequesterUserId(postId: Long, requesterUserId: Long): Boolean
}
