package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.api.lounge.request.UpdateLoungeCommentRequest
import com.org.oneulsogae.api.lounge.request.WriteLoungeCommentRequest
import com.org.oneulsogae.api.lounge.response.LoungeCommentPageResponse
import com.org.oneulsogae.api.lounge.response.WriteLoungeCommentResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.lounge.command.application.port.`in`.DeleteLoungeCommentUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.UpdateLoungeCommentUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.WriteLoungeCommentUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.command.WriteLoungeCommentCommand
import com.org.oneulsogae.core.lounge.query.service.port.`in`.GetLoungeCommentsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 라운지 댓글 엔드포인트. (조회는 비로그인 허용, 작성·수정·삭제는 인증 필요)
 * - POST /lounge/v1/self-intro-posts/{postId}/comments: 댓글(또는 parentCommentId를 준 대댓글)을 작성한다.
 * - GET /lounge/v1/self-intro-posts/{postId}/comments: root 댓글을 오래된 순 20개씩 커서 페이징으로,
 *   각 root의 대댓글은 전부 중첩해 조회한다.
 * - PATCH /lounge/v1/comments/{commentId}: 본인 댓글 내용을 수정한다.
 * - DELETE /lounge/v1/comments/{commentId}: 본인 댓글을 삭제한다. (soft delete)
 */
@RestController
@RequestMapping("/lounge/v1")
@Tag(name = "라운지 댓글", description = "라운지 셀소 댓글·대댓글 작성/조회/수정/삭제 엔드포인트 (조회는 비로그인 허용)")
class LoungeCommentController(
	private val writeLoungeCommentUseCase: WriteLoungeCommentUseCase,
	private val updateLoungeCommentUseCase: UpdateLoungeCommentUseCase,
	private val deleteLoungeCommentUseCase: DeleteLoungeCommentUseCase,
	private val getLoungeCommentsUseCase: GetLoungeCommentsUseCase,
) {

	/** 댓글(또는 대댓글)을 작성한다. */
	@Operation(
		summary = "댓글 작성",
		description = "content(1~500자)로 댓글을 작성한다. parentCommentId를 주면 그 댓글의 대댓글이 된다(깊이 1단계 — 대댓글에는 다시 답글을 달 수 없다, 400 LOUNGE-019). 글이 없으면 404(LOUNGE-008), 부모 댓글이 없거나 삭제됐으면 404(LOUNGE-016). 성공하면 생성된 commentId를 반환하고, 글 작성자(대댓글이면 부모 댓글 작성자)에게 알람이 발송된다(본인 제외).",
	)
	@PostMapping("/self-intro-posts/{postId}/comments")
	fun writeComment(
		@LoginUser user: AuthUser,
		@PathVariable("postId") postId: Long,
		@RequestBody request: WriteLoungeCommentRequest,
	): ApiResponse<WriteLoungeCommentResponse> {
		val command = WriteLoungeCommentCommand(
			postId = postId,
			parentCommentId = request.parentCommentId,
			content = request.content.orEmpty(),
		)
		return ApiResponse.success(WriteLoungeCommentResponse.of(writeLoungeCommentUseCase.write(user.id, command)))
	}

	/** 댓글 목록 한 페이지를 조회한다. (비로그인 허용) */
	@Operation(
		summary = "댓글 목록 조회",
		description = "root 댓글을 오래된 순으로 20개씩 내려주고, 각 root의 대댓글(replies)은 전부 중첩해 내려준다. 삭제된 댓글은 대댓글이 남아 있으면 deleted=true·content=null로 내려가고('삭제된 댓글입니다' 표시), 대댓글까지 없으면 목록에서 빠진다. mine은 조회한 사용자의 본인 댓글 여부다(비로그인이면 모두 false). 다음 페이지는 응답의 nextCursor를 cursor 파라미터로 그대로 넘겨 조회한다(hasNext=false면 마지막 페이지).",
	)
	@GetMapping("/self-intro-posts/{postId}/comments")
	fun getComments(
		@LoginUser user: AuthUser?,
		@PathVariable("postId") postId: Long,
		@RequestParam("cursor", required = false) cursor: Long?,
	): ApiResponse<LoungeCommentPageResponse> =
		ApiResponse.success(LoungeCommentPageResponse.of(getLoungeCommentsUseCase.getComments(user?.id, postId, cursor)))

	/** 본인 댓글의 내용을 수정한다. */
	@Operation(
		summary = "댓글 수정",
		description = "본인 댓글의 content(1~500자)를 수정한다. 댓글이 없거나 삭제됐으면 404(LOUNGE-016), 본인 댓글이 아니면 403(LOUNGE-018).",
	)
	@PatchMapping("/comments/{commentId}")
	fun updateComment(
		@LoginUser user: AuthUser,
		@PathVariable("commentId") commentId: Long,
		@RequestBody request: UpdateLoungeCommentRequest,
	): ApiResponse<Unit> {
		updateLoungeCommentUseCase.update(user.id, commentId, request.content.orEmpty())
		return ApiResponse.success()
	}

	/** 본인 댓글을 삭제한다. (soft delete) */
	@Operation(
		summary = "댓글 삭제",
		description = "본인 댓글을 soft delete한다. 대댓글이 남아 있는 댓글은 목록에 deleted=true('삭제된 댓글입니다')로 계속 노출된다. 댓글이 없거나 이미 삭제됐으면 404(LOUNGE-016), 본인 댓글이 아니면 403(LOUNGE-018).",
	)
	@DeleteMapping("/comments/{commentId}")
	fun deleteComment(
		@LoginUser user: AuthUser,
		@PathVariable("commentId") commentId: Long,
	): ApiResponse<Unit> {
		deleteLoungeCommentUseCase.delete(user.id, commentId)
		return ApiResponse.success()
	}
}
