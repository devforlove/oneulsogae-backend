package com.org.meeple.domain.gathering

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.gathering.command.domain.AdminGathering
import com.org.meeple.admin.gathering.command.domain.GatheringFee
import com.org.meeple.admin.gathering.command.domain.GatheringImage
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class AdminGatheringTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
	val future: LocalDateTime = now.plusDays(1)
	val fee: GatheringFee = GatheringFee(male = 10000, female = 8000)
	val earlyBird: GatheringFee = GatheringFee(male = 7000, female = 5000)

	describe("AdminGathering.create") {

		it("정상 입력이면 RECRUITING 상태로 생성된다") {
			val gathering: AdminGathering = AdminGathering.create(
				type = GatheringType.PARTY,
				title = "주말 파티",
				description = "함께 즐겨요",
				imageKey = "gatherings/party.png",
				region = "서울 강남구",
				gatheringAt = future,
				minParticipants = 2,
				maxParticipants = 4,
				fee = fee,
				earlyBirdFee = earlyBird,
				earlyBirdCapacity = 2,
				discountFee = null,
				now = now,
			)

			gathering.status shouldBe GatheringStatus.RECRUITING
			gathering.minParticipants shouldBe 2
			gathering.maxParticipants shouldBe 4
			gathering.earlyBirdFee shouldBe earlyBird
			gathering.earlyBirdCapacity shouldBe 2
		}

		it("제목이 공백이면 GATHERING_INVALID_TITLE을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "  ", null, null, "서울", future, 2, 4, fee, null, null, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_TITLE
		}

		it("제목이 100자를 초과하면 GATHERING_TITLE_TOO_LONG을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "가".repeat(101), null, null, "서울", future, 2, 4, fee, null, null, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_TITLE_TOO_LONG
		}

		it("소개가 1000자를 초과하면 GATHERING_DESCRIPTION_TOO_LONG을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", "가".repeat(1001), null, "서울", future, 2, 4, fee, null, null, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_DESCRIPTION_TOO_LONG
		}

		it("지역이 공백이면 GATHERING_INVALID_REGION을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "  ", future, 2, 4, fee, null, null, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_REGION
		}

		it("최소 인원이 2 미만이면 GATHERING_INVALID_MIN_PARTICIPANTS를 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "서울", future, 1, 4, fee, null, null, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_MIN_PARTICIPANTS
		}

		it("최대 인원이 최소 인원보다 작으면 GATHERING_INVALID_MAX_PARTICIPANTS를 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "서울", future, 4, 2, fee, null, null, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_MAX_PARTICIPANTS
		}

		it("모임 일시가 현재와 같거나 이전이면 GATHERING_INVALID_GATHERING_AT을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "서울", now, 2, 4, fee, null, null, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_GATHERING_AT
		}

		it("얼리버드 가격은 있는데 적용 인원이 없으면 GATHERING_INVALID_EARLY_BIRD_CAPACITY를 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "서울", future, 2, 4, fee, earlyBird, null, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY
		}

		it("얼리버드 적용 인원은 있는데 가격이 없으면 GATHERING_INVALID_EARLY_BIRD_CAPACITY를 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "서울", future, 2, 4, fee, null, 2, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY
		}

		it("얼리버드 적용 인원이 최대 인원을 초과하면 GATHERING_INVALID_EARLY_BIRD_CAPACITY를 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "서울", future, 2, 4, fee, earlyBird, 5, null, now)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY
		}

		it("경계값(최소=최대 2·얼리버드 적용 인원=최대)은 통과한다") {
			val gathering: AdminGathering = AdminGathering.create(
				type = GatheringType.ONE_ON_ONE_ROTATION,
				title = "가".repeat(100),
				description = "가".repeat(1000),
				imageKey = null,
				region = "서울",
				gatheringAt = now.plusSeconds(1),
				minParticipants = 2,
				maxParticipants = 4,
				fee = GatheringFee(male = 0, female = 0),
				earlyBirdFee = earlyBird,
				earlyBirdCapacity = 4,
				discountFee = null,
				now = now,
			)

			gathering.minParticipants shouldBe 2
			gathering.earlyBirdCapacity shouldBe 4
		}
	}

	describe("GatheringFee") {

		it("남/녀 참가비가 0원 이상이면 생성된다") {
			val created: GatheringFee = GatheringFee(male = 10000, female = 0)

			created.male shouldBe 10000
			created.female shouldBe 0
		}

		it("참가비가 0 미만이면 GATHERING_INVALID_FEE를 던진다") {
			shouldThrow<AdminException> { GatheringFee(male = -1, female = 0) }
				.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_FEE
			shouldThrow<AdminException> { GatheringFee(male = 0, female = -1) }
				.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_FEE
		}

		it("optional: 남/녀 둘 다 있으면 값 객체, 둘 다 없으면 null이다") {
			GatheringFee.optional(male = 5000, female = 3000) shouldBe GatheringFee(male = 5000, female = 3000)
			GatheringFee.optional(male = null, female = null) shouldBe null
		}

		it("optional: 한쪽만 있으면 GATHERING_INVALID_FEE를 던진다") {
			shouldThrow<AdminException> { GatheringFee.optional(male = 5000, female = null) }
				.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_FEE
			shouldThrow<AdminException> { GatheringFee.optional(male = null, female = 3000) }
				.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_FEE
		}
	}

	describe("GatheringImage.validate") {

		it("JPEG·PNG는 통과한다") {
			GatheringImage.validate("image/jpeg", 1024)
			GatheringImage.validate("image/png", 1024)
		}

		it("허용하지 않는 형식(gif)이면 GATHERING_INVALID_IMAGE_TYPE을 던진다") {
			shouldThrow<AdminException> { GatheringImage.validate("image/gif", 1024) }
				.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_IMAGE_TYPE
		}

		it("빈 파일(size 0)이면 GATHERING_INVALID_IMAGE_TYPE을 던진다") {
			shouldThrow<AdminException> { GatheringImage.validate("image/png", 0) }
				.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_IMAGE_TYPE
		}

		it("5MB를 초과하면 GATHERING_IMAGE_TOO_LARGE를 던진다") {
			shouldThrow<AdminException> { GatheringImage.validate("image/png", 5L * 1024 * 1024 + 1) }
				.errorCode shouldBe AdminErrorCode.GATHERING_IMAGE_TOO_LARGE
		}

		it("extensionOf는 콘텐츠 타입에 맞는 확장자를 준다") {
			GatheringImage.extensionOf("image/jpeg") shouldBe "jpg"
			GatheringImage.extensionOf("image/png") shouldBe "png"
		}
	}
})
