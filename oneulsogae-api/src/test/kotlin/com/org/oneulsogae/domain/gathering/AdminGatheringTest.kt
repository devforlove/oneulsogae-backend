package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.command.domain.AdminGathering
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringStatus
import com.org.oneulsogae.admin.gathering.command.domain.GatheringFee
import com.org.oneulsogae.admin.gathering.command.domain.GatheringImage
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AdminGatheringTest : DescribeSpec({

	describe("AdminGathering.create") {

		it("정상 입력이면 활성화(RECRUITING) 상태로 생성된다") {
			val gathering: AdminGathering = AdminGathering.create(
				type = GatheringType.PARTY,
				title = "주말 파티",
				description = "함께 즐겨요",
				imageKey = "gatherings/party.png",
				region = "서울 강남구",
				minParticipants = 2,
				maxParticipants = 4,
			)

			gathering.status shouldBe GatheringStatus.RECRUITING
			gathering.minParticipants shouldBe 2
			gathering.maxParticipants shouldBe 4
		}

		it("제목이 공백이면 GATHERING_INVALID_TITLE을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "  ", null, null, "서울", 2, 4)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_TITLE
		}

		it("제목이 100자를 초과하면 GATHERING_TITLE_TOO_LONG을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "가".repeat(101), null, null, "서울", 2, 4)
			}.errorCode shouldBe AdminErrorCode.GATHERING_TITLE_TOO_LONG
		}

		it("소개가 4000자면 통과한다") {
			val gathering: AdminGathering = AdminGathering.create(
				GatheringType.PARTY, "파티", "가".repeat(4000), null, "서울", 2, 4,
			)

			gathering.description?.length shouldBe 4000
		}

		it("소개가 4000자를 초과하면 GATHERING_DESCRIPTION_TOO_LONG을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", "가".repeat(4001), null, "서울", 2, 4)
			}.errorCode shouldBe AdminErrorCode.GATHERING_DESCRIPTION_TOO_LONG
		}

		it("지역이 공백이면 GATHERING_INVALID_REGION을 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "  ", 2, 4)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_REGION
		}

		it("최소 인원이 2 미만이면 GATHERING_INVALID_MIN_PARTICIPANTS를 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "서울", 1, 4)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_MIN_PARTICIPANTS
		}

		it("최대 인원이 최소 인원보다 작으면 GATHERING_INVALID_MAX_PARTICIPANTS를 던진다") {
			shouldThrow<AdminException> {
				AdminGathering.create(GatheringType.PARTY, "파티", null, null, "서울", 4, 2)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_MAX_PARTICIPANTS
		}

		it("경계값(최소=최대 2)은 통과한다") {
			val gathering: AdminGathering = AdminGathering.create(
				type = GatheringType.ONE_ON_ONE_ROTATION,
				title = "가".repeat(100),
				description = "가".repeat(1000),
				imageKey = null,
				region = "서울",
				minParticipants = 2,
				maxParticipants = 2,
			)

			gathering.minParticipants shouldBe 2
			gathering.maxParticipants shouldBe 2
		}
	}

	describe("AdminGathering.update") {

		val existing: AdminGathering = AdminGathering(
			id = 5L,
			type = GatheringType.PARTY,
			title = "옛 제목",
			description = "옛 소개",
			imageKey = "gatherings/old.png",
			region = "서울",
			minParticipants = 2,
			maxParticipants = 4,
			status = GatheringStatus.RECRUITING,
		)

		it("전체 데이터를 교체하되 id·status는 보존한다") {
			val updated: AdminGathering = existing.update(
				type = GatheringType.COOKING,
				title = "새 제목",
				description = "새 소개",
				imageKey = "gatherings/new.png",
				region = "부산",
				minParticipants = 3,
				maxParticipants = 8,
			)

			updated.id shouldBe 5L
			updated.status shouldBe GatheringStatus.RECRUITING
			updated.type shouldBe GatheringType.COOKING
			updated.title shouldBe "새 제목"
			updated.region shouldBe "부산"
			updated.maxParticipants shouldBe 8
			updated.imageKey shouldBe "gatherings/new.png"
		}

		it("생성과 동일한 규칙으로 검증한다 (최소 인원 2 미만이면 예외)") {
			shouldThrow<AdminException> {
				existing.update(
					type = GatheringType.PARTY, title = "제목", description = null, imageKey = null, region = "서울",
					minParticipants = 1, maxParticipants = 4,
				)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_MIN_PARTICIPANTS
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

		it("10MB까지는 통과한다") {
			GatheringImage.validate("image/png", 10L * 1024 * 1024)
		}

		it("10MB를 초과하면 GATHERING_IMAGE_TOO_LARGE를 던진다") {
			shouldThrow<AdminException> { GatheringImage.validate("image/png", 10L * 1024 * 1024 + 1) }
				.errorCode shouldBe AdminErrorCode.GATHERING_IMAGE_TOO_LARGE
		}

		it("extensionOf는 콘텐츠 타입에 맞는 확장자를 준다") {
			GatheringImage.extensionOf("image/jpeg") shouldBe "jpg"
			GatheringImage.extensionOf("image/png") shouldBe "png"
		}
	}

	describe("AdminGatheringStatus.changeTo") {

		it("취소: 활성화(RECRUITING)에서 CANCELED로 전이 가능") {
			AdminGatheringStatus(id = 1L, status = GatheringStatus.RECRUITING).changeTo(GatheringStatus.CANCELED)
		}

		it("취소: 이미 취소된 모임은 GATHERING_INVALID_STATUS_TRANSITION") {
			shouldThrow<AdminException> {
				AdminGatheringStatus(id = 1L, status = GatheringStatus.CANCELED).changeTo(GatheringStatus.CANCELED)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_STATUS_TRANSITION
		}

		it("지원하지 않는 목표 상태(RECRUITING)는 GATHERING_INVALID_STATUS_TRANSITION") {
			shouldThrow<AdminException> {
				AdminGatheringStatus(id = 1L, status = GatheringStatus.RECRUITING).changeTo(GatheringStatus.RECRUITING)
			}.errorCode shouldBe AdminErrorCode.GATHERING_INVALID_STATUS_TRANSITION
		}
	}
})
