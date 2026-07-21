package com.org.oneulsogae.domain.lounge

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [LoungeChatRequest] 도메인 유닛 테스트.
 * 생성(본인 글 차단)과 수락(소유권·중복 수락 차단, 상태 전이) 규칙이 도메인에 캡슐화됐는지 검증한다.
 */
class LoungeChatRequestTest : DescribeSpec({

	val postId = 10L
	val authorUserId = 1L
	val requesterUserId = 2L

	describe("create") {

		context("다른 사람의 글에 신청하면") {
			it("PENDING 상태의 신청이 만들어진다") {
				val request: LoungeChatRequest = LoungeChatRequest.create(
					postId = postId,
					requesterUserId = requesterUserId,
					postAuthorUserId = authorUserId,
				)

				request.postId shouldBe postId
				request.requesterUserId shouldBe requesterUserId
				request.status shouldBe LoungeChatRequestStatus.PENDING
			}
		}

		context("본인이 작성한 글에 신청하면") {
			it("LOUNGE_CHAT_REQUEST_SELF 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeChatRequest.create(
						postId = postId,
						requesterUserId = authorUserId,
						postAuthorUserId = authorUserId,
					)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF
			}
		}
	}

	describe("acceptBy") {

		context("글 작성자가 PENDING 신청을 수락하면") {
			it("상태가 ACCEPTED로 전이된 새 모델을 반환한다") {
				val request = LoungeChatRequest(id = 100L, postId = postId, requesterUserId = requesterUserId)

				val accepted: LoungeChatRequest = request.acceptBy(
					postAuthorUserId = authorUserId,
					actorUserId = authorUserId,
				)

				accepted.status shouldBe LoungeChatRequestStatus.ACCEPTED
				accepted.id shouldBe 100L
				// 원본은 불변이다.
				request.status shouldBe LoungeChatRequestStatus.PENDING
			}
		}

		context("글 작성자가 아닌 사람이 수락하면") {
			it("LOUNGE_POST_NOT_OWNED 예외를 던진다") {
				val request = LoungeChatRequest(id = 100L, postId = postId, requesterUserId = requesterUserId)

				val exception: BusinessException = shouldThrow<BusinessException> {
					request.acceptBy(postAuthorUserId = authorUserId, actorUserId = requesterUserId)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_POST_NOT_OWNED
			}
		}

		context("이미 수락한 신청을 다시 수락하면") {
			it("LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED 예외를 던진다") {
				val request = LoungeChatRequest(
					id = 100L,
					postId = postId,
					requesterUserId = requesterUserId,
					status = LoungeChatRequestStatus.ACCEPTED,
				)

				val exception: BusinessException = shouldThrow<BusinessException> {
					request.acceptBy(postAuthorUserId = authorUserId, actorUserId = authorUserId)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED
			}
		}
	}
})
