package com.org.meeple.domain.lounge

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.lounge.LoungeErrorCode
import com.org.meeple.core.lounge.command.domain.SelfIntroPost
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [SelfIntroPost] 도메인 유닛 테스트.
 * 본문 검증(필수·최대 길이), 사진 검증(장수·빈 파일·형식·크기), 등록 빈도 제한(최근 24시간 1건),
 * 확장자 매핑을 확인한다.
 */
class SelfIntroPostTest : DescribeSpec({

	fun create(): SelfIntroPost = SelfIntroPost.create(
		postId = 1L,
		longDistance = "장거리 가능해요",
		desiredAge = "28~34세",
		mbti = "ENFP",
		marriageThought = "3년 안에 하고 싶어요",
		preferredPartner = "대화가 잘 통하는 사람",
		charmPoint = "잘 웃어요",
		freeWord = "편하게 연락 주세요",
	)

	describe("create") {
		it("본문 7개 항목을 담아 셀소를 만든다") {
			val post: SelfIntroPost = create()

			post.postId shouldBe 1L
			post.longDistance shouldBe "장거리 가능해요"
			post.desiredAge shouldBe "28~34세"
			post.mbti shouldBe "ENFP"
			post.marriageThought shouldBe "3년 안에 하고 싶어요"
			post.preferredPartner shouldBe "대화가 잘 통하는 사람"
			post.charmPoint shouldBe "잘 웃어요"
			post.freeWord shouldBe "편하게 연락 주세요"
			post.id shouldBe 0
		}
	}

	describe("validateContent") {
		it("본문 항목이 공백이면 SELF_INTRO_INVALID_CONTENT") {
			val exception: BusinessException = shouldThrow {
				SelfIntroPost.validateContent(
					longDistance = "장거리 가능해요",
					desiredAge = "  ",
					mbti = "ENFP",
					marriageThought = "3년 안에 하고 싶어요",
					preferredPartner = "대화가 잘 통하는 사람",
					charmPoint = "잘 웃어요",
					freeWord = "편하게 연락 주세요",
				)
			}

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_INVALID_CONTENT
		}

		it("서술형 항목이 최대 길이를 넘으면 SELF_INTRO_INVALID_CONTENT") {
			val exception: BusinessException = shouldThrow {
				SelfIntroPost.validateContent(
					longDistance = "장거리 가능해요",
					desiredAge = "28~34세",
					mbti = "ENFP",
					marriageThought = "가".repeat(SelfIntroPost.MAX_LONG_TEXT_LENGTH + 1),
					preferredPartner = "대화가 잘 통하는 사람",
					charmPoint = "잘 웃어요",
					freeWord = "편하게 연락 주세요",
				)
			}

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_INVALID_CONTENT
		}

		it("MBTI가 최대 길이를 넘으면 SELF_INTRO_INVALID_CONTENT") {
			val exception: BusinessException = shouldThrow {
				SelfIntroPost.validateContent(
					longDistance = "장거리 가능해요",
					desiredAge = "28~34세",
					mbti = "E".repeat(SelfIntroPost.MAX_MBTI_LENGTH + 1),
					marriageThought = "3년 안에 하고 싶어요",
					preferredPartner = "대화가 잘 통하는 사람",
					charmPoint = "잘 웃어요",
					freeWord = "편하게 연락 주세요",
				)
			}

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_INVALID_CONTENT
		}
	}

	describe("validatePhotoCount") {
		it("사진이 없으면 SELF_INTRO_PHOTO_REQUIRED") {
			val exception: BusinessException = shouldThrow { SelfIntroPost.validatePhotoCount(0) }

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_PHOTO_REQUIRED
		}

		it("최대 장수를 넘으면 SELF_INTRO_TOO_MANY_PHOTOS") {
			val exception: BusinessException = shouldThrow {
				SelfIntroPost.validatePhotoCount(SelfIntroPost.MAX_PHOTO_COUNT + 1)
			}

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_TOO_MANY_PHOTOS
		}
	}

	describe("validatePhoto") {
		it("빈 파일이면 SELF_INTRO_EMPTY_PHOTO") {
			val exception: BusinessException = shouldThrow { SelfIntroPost.validatePhoto("image/jpeg", 0L) }

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_EMPTY_PHOTO
		}

		it("허용하지 않는 형식이면 SELF_INTRO_INVALID_PHOTO_TYPE") {
			val exception: BusinessException = shouldThrow { SelfIntroPost.validatePhoto("image/gif", 100L) }

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_INVALID_PHOTO_TYPE
		}

		it("최대 크기를 넘으면 SELF_INTRO_PHOTO_TOO_LARGE") {
			val exception: BusinessException = shouldThrow {
				SelfIntroPost.validatePhoto("image/png", SelfIntroPost.MAX_PHOTO_SIZE_BYTES + 1)
			}

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_PHOTO_TOO_LARGE
		}
	}

	describe("등록 빈도 제한") {
		it("제한 구간은 현재 시각에서 24시간 전부터다") {
			val now: LocalDateTime = LocalDateTime.of(2026, 7, 20, 10, 0)

			SelfIntroPost.limitWindowSince(now) shouldBe LocalDateTime.of(2026, 7, 19, 10, 0)
		}

		it("구간 안에 등록한 셀소가 있으면 SELF_INTRO_DAILY_LIMIT_EXCEEDED") {
			val exception: BusinessException = shouldThrow { SelfIntroPost.validateDailyLimit(1) }

			exception.errorCode shouldBe LoungeErrorCode.SELF_INTRO_DAILY_LIMIT_EXCEEDED
		}
	}

	describe("extensionOf") {
		it("콘텐츠 타입에 맞는 확장자를 돌려준다") {
			SelfIntroPost.extensionOf("image/jpeg") shouldBe "jpg"
			SelfIntroPost.extensionOf("image/png") shouldBe "png"
		}
	}
})
