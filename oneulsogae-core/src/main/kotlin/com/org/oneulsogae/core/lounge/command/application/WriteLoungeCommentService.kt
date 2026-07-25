package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.application.port.`in`.WriteLoungeCommentUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.command.WriteLoungeCommentCommand
import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.WriteLoungeCommentResult
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungePostPort
import com.org.oneulsogae.core.lounge.command.application.port.out.SaveLoungeCommentPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeComment
import com.org.oneulsogae.core.lounge.command.domain.LoungePost
import com.org.oneulsogae.core.lounge.command.domain.event.LoungeCommentAdded
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [WriteLoungeCommentUseCase] 구현.
 * 글 존재를 확인하고 댓글을 저장한다. 내용·깊이(1단계)·부모 유효성 검증은 도메인([LoungeComment.create])이 한다.
 * 알람은 부가 효과라 커밋 후 별도 트랜잭션에서 best-effort([LoungeEventHandler])로 처리하며,
 * 수신자(댓글이면 글 작성자, 대댓글이면 부모 댓글 작성자)가 본인이면 발행하지 않는다.
 */
@Service
class WriteLoungeCommentService(
	private val getLoungePostPort: GetLoungePostPort,
	private val getLoungeCommentPort: GetLoungeCommentPort,
	private val saveLoungeCommentPort: SaveLoungeCommentPort,
	private val domainEventPublisher: DomainEventPublisher,
) : WriteLoungeCommentUseCase {

	@Transactional
	override fun write(userId: Long, command: WriteLoungeCommentCommand): WriteLoungeCommentResult {
		val post: LoungePost = getLoungePostPort.findById(command.postId)
			?: throw BusinessException(LoungeErrorCode.SELF_INTRO_POST_NOT_FOUND, "셀소를 찾을 수 없습니다: ${command.postId}")

		val parent: LoungeComment? = command.parentCommentId?.let { parentCommentId: Long ->
			getLoungeCommentPort.findById(parentCommentId)
				?: throw BusinessException(LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다: $parentCommentId")
		}

		val saved: LoungeComment = saveLoungeCommentPort.save(
			LoungeComment.create(
				postId = command.postId,
				userId = userId,
				content = command.content,
				parent = parent,
			),
		)

		// 알람 수신자: 댓글 → 글 작성자, 대댓글 → 부모 댓글 작성자. 본인에게는 알람을 보내지 않는다.
		val receiverUserId: Long = parent?.userId ?: post.userId
		if (receiverUserId != userId) {
			domainEventPublisher.publish(
				LoungeCommentAdded(
					commentId = saved.id,
					postId = command.postId,
					commentAuthorUserId = userId,
					receiverUserId = receiverUserId,
					reply = parent != null,
				),
			)
		}

		return WriteLoungeCommentResult(saved.id)
	}
}
