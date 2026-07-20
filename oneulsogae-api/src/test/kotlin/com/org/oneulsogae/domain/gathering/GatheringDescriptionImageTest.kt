package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.admin.gathering.command.domain.GatheringDescriptionImage
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class GatheringDescriptionImageTest : DescribeSpec({

	describe("GatheringDescriptionImage.isValidKey") {

		it("프리픽스로 시작하면 유효하다") {
			GatheringDescriptionImage.isValidKey("gathering-descriptions/a.jpg") shouldBe true
		}

		it("다른 프리픽스면 무효하다") {
			GatheringDescriptionImage.isValidKey("gatherings/a.jpg") shouldBe false
		}

		it("경로 조작(..)이 있으면 무효하다") {
			GatheringDescriptionImage.isValidKey("gathering-descriptions/../secret") shouldBe false
		}

		it("경로 조작(\\)이 있으면 무효하다") {
			GatheringDescriptionImage.isValidKey("gathering-descriptions/a\\b") shouldBe false
		}
	}
})
