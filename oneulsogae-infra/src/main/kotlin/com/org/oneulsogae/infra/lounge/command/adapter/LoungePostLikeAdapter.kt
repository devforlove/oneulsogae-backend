package com.org.oneulsogae.infra.lounge.command.adapter

import com.org.oneulsogae.core.lounge.command.application.port.out.DeleteLoungePostLikePort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungePostLikePort
import com.org.oneulsogae.infra.lounge.command.repository.LoungePostLikeJpaRepository
import org.springframework.stereotype.Component

/** 라운지 글 좋아요 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나 — 저장·삭제를 함께 구현) */
@Component
class LoungePostLikeAdapter(
	private val loungePostLikeJpaRepository: LoungePostLikeJpaRepository,
) : SaveLoungePostLikePort, DeleteLoungePostLikePort {

	// INSERT IGNORE 한 방이라 동시 요청이 겹쳐도 예외 없이 정확히 한 요청만 true를 받는다. (분산 락 불필요)
	override fun saveIfAbsent(postId: Long, userId: Long): Boolean =
		loungePostLikeJpaRepository.insertIgnore(postId, userId) == 1

	override fun delete(postId: Long, userId: Long): Boolean =
		loungePostLikeJpaRepository.deleteByPostIdAndUserId(postId, userId) > 0
}
