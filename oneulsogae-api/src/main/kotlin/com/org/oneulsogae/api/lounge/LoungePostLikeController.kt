package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.lounge.command.application.port.`in`.LikeLoungePostUseCase
import com.org.oneulsogae.core.lounge.command.application.port.`in`.UnlikeLoungePostUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 라운지 글 좋아요 엔드포인트. (인증 필요)
 * - POST /lounge/v1/self-intro-posts/{postId}/likes: 좋아요 등록. 멱등 — 이미 눌렀어도 200.
 * - DELETE /lounge/v1/self-intro-posts/{postId}/likes: 좋아요 취소. 멱등 — 누른 적 없어도 200.
 */
@RestController
@RequestMapping("/lounge/v1")
@Tag(name = "라운지 좋아요", description = "라운지 셀소 좋아요 등록·취소 엔드포인트 (멱등, 인증 필요)")
class LoungePostLikeController(
	private val likeLoungePostUseCase: LikeLoungePostUseCase,
	private val unlikeLoungePostUseCase: UnlikeLoungePostUseCase,
) {

	/** 좋아요를 등록한다. (멱등) */
	@Operation(
		summary = "좋아요 등록",
		description = "글에 좋아요를 누른다. 이미 눌렀으면 아무 일도 하지 않고 200이다(멱등). 글이 없으면 404(LOUNGE-008). likeCount는 목록·상세 응답에 반영된다.",
	)
	@PostMapping("/self-intro-posts/{postId}/likes")
	fun like(
		@LoginUser user: AuthUser,
		@PathVariable("postId") postId: Long,
	): ApiResponse<Unit> {
		likeLoungePostUseCase.like(user.id, postId)
		return ApiResponse.success()
	}

	/** 좋아요를 취소한다. (멱등) */
	@Operation(
		summary = "좋아요 취소",
		description = "글에 누른 좋아요를 취소한다. 누른 적 없으면 아무 일도 하지 않고 200이다(멱등).",
	)
	@DeleteMapping("/self-intro-posts/{postId}/likes")
	fun unlike(
		@LoginUser user: AuthUser,
		@PathVariable("postId") postId: Long,
	): ApiResponse<Unit> {
		unlikeLoungePostUseCase.unlike(user.id, postId)
		return ApiResponse.success()
	}
}
