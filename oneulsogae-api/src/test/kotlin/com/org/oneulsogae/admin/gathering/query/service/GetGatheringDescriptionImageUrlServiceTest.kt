package com.org.oneulsogae.admin.gathering.query.service

import com.org.oneulsogae.admin.gathering.query.service.port.out.GatheringImageUrlPort
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class GetGatheringDescriptionImageUrlServiceTest : DescribeSpec({

	val port = GatheringImageUrlPort { key -> "https://s3/signed?$key" }
	val service = GetGatheringDescriptionImageUrlService(port)

	describe("GetGatheringDescriptionImageUrlService.execute") {

		it("소개 이미지 프리픽스(gathering-descriptions/) key면 presigned URL을 반환한다") {
			service.execute("gathering-descriptions/a.jpg") shouldBe "https://s3/signed?gathering-descriptions/a.jpg"
		}

		it("타 프리픽스 key면 null을 반환한다") {
			service.execute("gatherings/a.jpg").shouldBeNull()
		}

		it("경로 조작(..)이 섞인 key면 null을 반환한다") {
			service.execute("gathering-descriptions/../secret").shouldBeNull()
		}
	}
})
