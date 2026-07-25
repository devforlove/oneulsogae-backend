package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.application.port.`in`.LikeLoungePostUseCase
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungePostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungePostLikePort
import com.org.oneulsogae.core.lounge.command.application.port.out.UpdateLoungePostLikeCountPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [LikeLoungePostUseCase] 구현. (멱등)
 * 좋아요 행이 실제로 새로 저장됐을 때만 표시용 총합(like_count)을 올린다 —
 * 이미 눌렀거나 더블클릭 경합에서 진 요청은 총합을 건드리지 않아 행 수와 총합이 어긋나지 않는다.
 */
@Service
class LikeLoungePostService(
	private val getLoungePostPort: GetLoungePostPort,
	private val saveLoungePostLikePort: SaveLoungePostLikePort,
	private val updateLoungePostLikeCountPort: UpdateLoungePostLikeCountPort,
) : LikeLoungePostUseCase {

	@Transactional
	override fun like(userId: Long, postId: Long) {
		getLoungePostPort.findById(postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: $postId")

		if (saveLoungePostLikePort.saveIfAbsent(postId, userId)) {
			updateLoungePostLikeCountPort.increaseLikeCount(postId)
		}
	}
}
