package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.core.lounge.command.application.port.`in`.UnlikeLoungePostUseCase
import com.org.oneulsogae.core.lounge.command.application.port.out.DeleteLoungePostLikePort
import com.org.oneulsogae.core.lounge.command.application.port.out.UpdateLoungePostLikeCountPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [UnlikeLoungePostUseCase] 구현. (멱등)
 * 좋아요 행을 실제로 지웠을 때만 표시용 총합(like_count)을 내린다. (누른 적 없으면 아무 일도 없다)
 * 글 존재는 확인하지 않는다 — 행이 없으면 어차피 no-op이라 없는 글도 그대로 성공이다.
 */
@Service
class UnlikeLoungePostService(
	private val deleteLoungePostLikePort: DeleteLoungePostLikePort,
	private val updateLoungePostLikeCountPort: UpdateLoungePostLikeCountPort,
) : UnlikeLoungePostUseCase {

	@Transactional
	override fun unlike(userId: Long, postId: Long) {
		if (deleteLoungePostLikePort.delete(postId, userId)) {
			updateLoungePostLikeCountPort.decreaseLikeCount(postId)
		}
	}
}
