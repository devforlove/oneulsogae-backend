package com.org.oneulsogae.domain.lounge

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.domain.LoungeComment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [LoungeComment] 도메인 유닛 테스트.
 * 생성(내용·부모 유효성·깊이 1단계)과 수정·삭제(소유권·삭제 행 차단) 규칙이 도메인에 캡슐화됐는지 검증한다.
 */
class LoungeCommentTest : DescribeSpec({

	val postId = 10L
	val authorUserId = 1L
	val otherUserId = 2L

	describe("create") {

		context("내용을 채워 root 댓글을 만들면") {
			it("parentId가 null인 댓글이 만들어진다") {
				val comment: LoungeComment = LoungeComment.create(
					postId = postId,
					userId = authorUserId,
					content = "댓글 내용",
					parent = null,
				)

				comment.postId shouldBe postId
				comment.userId shouldBe authorUserId
				comment.parentId shouldBe null
				comment.content shouldBe "댓글 내용"
				comment.deleted shouldBe false
			}
		}

		context("root 댓글에 답글을 달면") {
			it("parentId가 부모 댓글 id인 대댓글이 만들어진다") {
				val parent = LoungeComment(id = 100L, postId = postId, userId = authorUserId, content = "부모 댓글")

				val reply: LoungeComment = LoungeComment.create(
					postId = postId,
					userId = otherUserId,
					content = "대댓글 내용",
					parent = parent,
				)

				reply.parentId shouldBe 100L
			}
		}

		context("내용이 비었으면") {
			it("LOUNGE_COMMENT_INVALID_CONTENT 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeComment.create(postId = postId, userId = authorUserId, content = "  ", parent = null)
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_INVALID_CONTENT
			}
		}

		context("내용이 최대 길이(500자)를 넘으면") {
			it("LOUNGE_COMMENT_INVALID_CONTENT 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeComment.create(
						postId = postId,
						userId = authorUserId,
						content = "가".repeat(LoungeComment.MAX_CONTENT_LENGTH + 1),
						parent = null,
					)
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_INVALID_CONTENT
			}
		}

		context("대댓글에 다시 답글을 달면") {
			it("LOUNGE_COMMENT_REPLY_DEPTH_EXCEEDED 예외를 던진다") {
				val reply = LoungeComment(id = 101L, postId = postId, userId = authorUserId, parentId = 100L, content = "대댓글")

				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeComment.create(postId = postId, userId = otherUserId, content = "답글의 답글", parent = reply)
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_REPLY_DEPTH_EXCEEDED
			}
		}

		context("삭제된 댓글에 답글을 달면") {
			it("LOUNGE_COMMENT_NOT_FOUND 예외를 던진다") {
				val parent = LoungeComment(id = 100L, postId = postId, userId = authorUserId, content = "부모 댓글", deleted = true)

				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeComment.create(postId = postId, userId = otherUserId, content = "대댓글", parent = parent)
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND
			}
		}

		context("다른 글의 댓글을 부모로 답글을 달면") {
			it("LOUNGE_COMMENT_NOT_FOUND 예외를 던진다") {
				val parent = LoungeComment(id = 100L, postId = 999L, userId = authorUserId, content = "다른 글의 댓글")

				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeComment.create(postId = postId, userId = otherUserId, content = "대댓글", parent = parent)
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND
			}
		}
	}

	describe("editBy") {

		context("작성자 본인이 수정하면") {
			it("내용이 바뀐 새 모델을 반환한다") {
				val comment = LoungeComment(id = 100L, postId = postId, userId = authorUserId, content = "원래 내용")

				val edited: LoungeComment = comment.editBy(authorUserId, "수정한 내용")

				edited.content shouldBe "수정한 내용"
				edited.id shouldBe 100L
			}
		}

		context("본인 댓글이 아니면") {
			it("LOUNGE_COMMENT_NOT_OWNED 예외를 던진다") {
				val comment = LoungeComment(id = 100L, postId = postId, userId = authorUserId, content = "원래 내용")

				val exception: BusinessException = shouldThrow<BusinessException> {
					comment.editBy(otherUserId, "수정한 내용")
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_NOT_OWNED
			}
		}

		context("삭제된 댓글을 수정하면") {
			it("LOUNGE_COMMENT_NOT_FOUND 예외를 던진다") {
				val comment = LoungeComment(id = 100L, postId = postId, userId = authorUserId, content = "원래 내용", deleted = true)

				val exception: BusinessException = shouldThrow<BusinessException> {
					comment.editBy(authorUserId, "수정한 내용")
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND
			}
		}

		context("수정 내용이 비었으면") {
			it("LOUNGE_COMMENT_INVALID_CONTENT 예외를 던진다") {
				val comment = LoungeComment(id = 100L, postId = postId, userId = authorUserId, content = "원래 내용")

				val exception: BusinessException = shouldThrow<BusinessException> {
					comment.editBy(authorUserId, "")
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_INVALID_CONTENT
			}
		}
	}

	describe("validateOwnedBy") {

		context("본인 댓글이 아니면") {
			it("LOUNGE_COMMENT_NOT_OWNED 예외를 던진다") {
				val comment = LoungeComment(id = 100L, postId = postId, userId = authorUserId, content = "댓글")

				val exception: BusinessException = shouldThrow<BusinessException> {
					comment.validateOwnedBy(otherUserId)
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_NOT_OWNED
			}
		}

		context("삭제된 댓글이면") {
			it("LOUNGE_COMMENT_NOT_FOUND 예외를 던진다") {
				val comment = LoungeComment(id = 100L, postId = postId, userId = authorUserId, content = "댓글", deleted = true)

				val exception: BusinessException = shouldThrow<BusinessException> {
					comment.validateOwnedBy(authorUserId)
				}
				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_COMMENT_NOT_FOUND
			}
		}
	}
})
