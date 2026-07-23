package com.org.oneulsogae.domain.lounge

import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.lounge.LoungeErrorCode
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [LoungeChatRequest] 도메인 유닛 테스트.
 * 생성(본인 글 차단, 만료 시각 확정)과 수락(소유권·중복 수락·만료 차단, 상태 전이) 규칙이 도메인에 캡슐화됐는지 검증한다.
 */
class LoungeChatRequestTest : DescribeSpec({

	val postId = 10L
	val authorUserId = 1L
	val requesterUserId = 2L
	val now: LocalDateTime = LocalDateTime.of(2026, 7, 23, 12, 0)

	describe("create") {

		context("다른 사람의 글에 신청하면") {
			it("PENDING 상태·만료 시각(신청 시각 + 3일)의 신청이 만들어진다") {
				val request: LoungeChatRequest = LoungeChatRequest.create(
					postId = postId,
					requesterUserId = requesterUserId,
					postAuthorUserId = authorUserId,
					requesterGender = Gender.MALE,
					postAuthorGender = Gender.FEMALE,
					now = now,
					initCoinAmount = 32,
				)

				request.postId shouldBe postId
				request.requesterUserId shouldBe requesterUserId
				// 글 작성자를 수신자로 확정해 둔다. (수락 판정·목록 조회가 이 값을 쓴다)
				request.receiverUserId shouldBe authorUserId
				request.status shouldBe LoungeChatRequestStatus.PENDING
				request.expiredAt shouldBe now.plus(LoungeChatRequest.EXPIRATION)
			}
		}

		context("본인이 작성한 글에 신청하면") {
			it("LOUNGE_CHAT_REQUEST_SELF 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeChatRequest.create(
						postId = postId,
						requesterUserId = authorUserId,
						postAuthorUserId = authorUserId,
						requesterGender = Gender.MALE,
						postAuthorGender = Gender.MALE,
						now = now,
						initCoinAmount = 32,
					)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF
			}
		}

		context("성별이 같은 상대의 글에 신청하면") {
			it("LOUNGE_CHAT_REQUEST_SAME_GENDER 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeChatRequest.create(
						postId = postId,
						requesterUserId = requesterUserId,
						postAuthorUserId = authorUserId,
						requesterGender = Gender.FEMALE,
						postAuthorGender = Gender.FEMALE,
						now = now,
						initCoinAmount = 32,
					)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_SAME_GENDER
			}
		}

		context("한쪽 성별을 확인할 수 없으면") {
			it("이성임을 보장할 수 없으므로 LOUNGE_CHAT_REQUEST_SAME_GENDER 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeChatRequest.create(
						postId = postId,
						requesterUserId = requesterUserId,
						postAuthorUserId = authorUserId,
						requesterGender = Gender.MALE,
						postAuthorGender = null,
						now = now,
						initCoinAmount = 32,
					)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_SAME_GENDER
			}
		}

		context("본인 글이면서 성별도 같으면") {
			it("성별 사유가 아니라 LOUNGE_CHAT_REQUEST_SELF 예외를 던진다") {
				val exception: BusinessException = shouldThrow<BusinessException> {
					LoungeChatRequest.create(
						postId = postId,
						requesterUserId = authorUserId,
						postAuthorUserId = authorUserId,
						requesterGender = Gender.MALE,
						postAuthorGender = Gender.MALE,
						now = now,
						initCoinAmount = 32,
					)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_SELF
			}
		}
	}

	describe("acceptBy") {

		context("글 작성자가 만료 전 PENDING 신청을 수락하면") {
			it("상태가 ACCEPTED로 전이된 새 모델을 반환한다") {
				val request = LoungeChatRequest(
					id = 100L,
					postId = postId,
					requesterUserId = requesterUserId,
					receiverUserId = authorUserId,
					expiredAt = now.plusDays(3),
				)

				val accepted: LoungeChatRequest = request.acceptBy(actorUserId = authorUserId, now = now)

				accepted.status shouldBe LoungeChatRequestStatus.ACCEPTED
				accepted.id shouldBe 100L
				// 원본은 불변이다.
				request.status shouldBe LoungeChatRequestStatus.PENDING
			}
		}

		context("글 작성자가 아닌 사람이 수락하면") {
			it("LOUNGE_POST_NOT_OWNED 예외를 던진다") {
				val request = LoungeChatRequest(
					id = 100L,
					postId = postId,
					requesterUserId = requesterUserId,
					receiverUserId = authorUserId,
					expiredAt = now.plusDays(3),
				)

				val exception: BusinessException = shouldThrow<BusinessException> {
					request.acceptBy(actorUserId = requesterUserId, now = now)
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
					receiverUserId = authorUserId,
					status = LoungeChatRequestStatus.ACCEPTED,
					expiredAt = now.plusDays(3),
				)

				val exception: BusinessException = shouldThrow<BusinessException> {
					request.acceptBy(actorUserId = authorUserId, now = now)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_ALREADY_ACCEPTED
			}
		}

		context("만료 시각이 지난 PENDING 신청을 수락하면") {
			it("LOUNGE_CHAT_REQUEST_EXPIRED 예외를 던진다") {
				val request = LoungeChatRequest(
					id = 100L,
					postId = postId,
					requesterUserId = requesterUserId,
					receiverUserId = authorUserId,
					expiredAt = now,
				)

				val exception: BusinessException = shouldThrow<BusinessException> {
					request.acceptBy(actorUserId = authorUserId, now = now)
				}

				exception.errorCode shouldBe LoungeErrorCode.LOUNGE_CHAT_REQUEST_EXPIRED
			}
		}

		context("만료 시각 직전의 PENDING 신청을 수락하면") {
			it("정상적으로 ACCEPTED로 전이된다") {
				val request = LoungeChatRequest(
					id = 100L,
					postId = postId,
					requesterUserId = requesterUserId,
					receiverUserId = authorUserId,
					expiredAt = now.plusSeconds(1),
				)

				val accepted: LoungeChatRequest = request.acceptBy(actorUserId = authorUserId, now = now)

				accepted.status shouldBe LoungeChatRequestStatus.ACCEPTED
			}
		}
	}

	describe("expiryRefundAmount") {

		it("initCoinAmount가 없는 구행 신청은 정책값(LOUNGE_CHAT_INIT 32코인)의 절반을 돌려준다") {
			val request = LoungeChatRequest(
				id = 100L,
				postId = postId,
				requesterUserId = requesterUserId,
				receiverUserId = authorUserId,
				expiredAt = now,
			)

			request.expiryRefundAmount() shouldBe 16
		}

		it("initCoinAmount가 있으면 실제 낸 신청 비용의 절반을 돌려준다 (여성 16 지불 → 8 환불)") {
			val request = LoungeChatRequest(
				id = 100L,
				postId = postId,
				requesterUserId = requesterUserId,
				receiverUserId = authorUserId,
				expiredAt = now,
				initCoinAmount = 16,
			)

			request.expiryRefundAmount() shouldBe 8
		}
	}

	describe("isExpired") {

		context("이미 수락된 신청은") {
			it("만료 시각이 지나도 만료로 보지 않는다") {
				val request = LoungeChatRequest(
					id = 100L,
					postId = postId,
					requesterUserId = requesterUserId,
					receiverUserId = authorUserId,
					status = LoungeChatRequestStatus.ACCEPTED,
					expiredAt = now.minusDays(10),
				)

				request.isExpired(now) shouldBe false
			}
		}
	}
})
