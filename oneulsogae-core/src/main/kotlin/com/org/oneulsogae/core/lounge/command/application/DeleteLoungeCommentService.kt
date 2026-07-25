package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.application.port.`in`.DeleteLoungeCommentUseCase
import com.org.oneulsogae.core.lounge.command.application.port.out.DeleteLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeComment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [DeleteLoungeCommentUseCase] 구현. (soft delete)
 * 소유권·삭제 여부 검증은 도메인([LoungeComment.validateOwnedBy])이 한다.
 * 살아있는 대댓글이 남은 삭제 댓글은 조회 화면에 "삭제된 댓글"로 계속 노출된다. (행은 유지)
 */
@Service
class DeleteLoungeCommentService(
	private val getLoungeCommentPort: GetLoungeCommentPort,
	private val deleteLoungeCommentPort: DeleteLoungeCommentPort,
	private val timeGenerator: TimeGenerator,
) : DeleteLoungeCommentUseCase {

	@Transactional
	override fun delete(userId: Long, commentId: Long) {
		val comment: LoungeComment = getLoungeCommentPort.findById(commentId)
			?: throw BusinessException(LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다: $commentId")

		comment.validateOwnedBy(userId)
		deleteLoungeCommentPort.delete(commentId, timeGenerator.now())
	}
}
