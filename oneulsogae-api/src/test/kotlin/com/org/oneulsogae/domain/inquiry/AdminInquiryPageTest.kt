package com.org.oneulsogae.domain.inquiry

import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryPage
import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryView
import com.org.oneulsogae.admin.inquiry.query.dto.AdminInquiryViews
import com.org.oneulsogae.common.inquiry.InquiryCategory
import com.org.oneulsogae.common.inquiry.InquiryStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [AdminInquiryPage] read model 유닛 테스트.
 * offset 페이징 메타데이터([AdminInquiryPage.totalPages]/[AdminInquiryPage.hasNext]) 계산을 검증한다.
 */
class AdminInquiryPageTest : DescribeSpec({

	fun view(id: Long): AdminInquiryView =
		AdminInquiryView(
			id = id,
			category = InquiryCategory.ETC,
			status = InquiryStatus.PENDING,
			email = "user$id@test.com",
			createdAt = null,
		)

	fun views(count: Int): AdminInquiryViews =
		AdminInquiryViews((1..count).map { view(it.toLong()) })

	describe("totalPages") {
		it("전체 개수를 size로 나눈 올림 값이다") {
			AdminInquiryPage(content = views(2), page = 0, size = 2, totalElements = 5).totalPages shouldBe 3
		}

		it("전체 개수가 size로 나누어떨어지면 그 몫이다") {
			AdminInquiryPage(content = views(2), page = 0, size = 2, totalElements = 4).totalPages shouldBe 2
		}

		it("전체가 0이면 0이다") {
			AdminInquiryPage.empty(page = 0, size = 20).totalPages shouldBe 0
		}
	}

	describe("hasNext") {
		it("뒤에 페이지가 더 있으면 true다") {
			AdminInquiryPage(content = views(2), page = 0, size = 2, totalElements = 5).hasNext shouldBe true
		}

		it("마지막(부분) 페이지면 false다") {
			AdminInquiryPage(content = views(1), page = 2, size = 2, totalElements = 5).hasNext shouldBe false
		}

		it("정확히 꽉 찬 마지막 페이지면 false다") {
			AdminInquiryPage(content = views(2), page = 1, size = 2, totalElements = 4).hasNext shouldBe false
		}
	}
})
