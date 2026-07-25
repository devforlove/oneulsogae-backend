package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.application.port.`in`.UpdateLoungeCommentUseCase
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeComment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [UpdateLoungeCommentUseCase] 구현.
 * 소유권·삭제 여부·내용 검증은 도메인([LoungeComment.editBy])이 한다.
 */
@Service
class UpdateLoungeCommentService(
	private val getLoungeCommentPort: GetLoungeCommentPort,
	private val saveLoungeCommentPort: SaveLoungeCommentPort,
) : UpdateLoungeCommentUseCase {

	@Transactional
	override fun update(userId: Long, commentId: Long, content: String) {
		val comment: LoungeComment = getLoungeCommentPort.findById(commentId)
			?: throw BusinessException(LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다: $commentId")

		saveLoungeCommentPort.save(comment.editBy(userId, content))
	}
}
