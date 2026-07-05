package com.org.meeple.domain.notice

import com.org.meeple.admin.notice.query.dto.AdminNoticePage
import com.org.meeple.admin.notice.query.dto.AdminNoticeView
import com.org.meeple.admin.notice.query.dto.AdminNoticeViews
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [AdminNoticePage] read model 유닛 테스트.
 * offset 페이징 메타데이터([AdminNoticePage.totalPages]/[AdminNoticePage.hasNext]) 계산을 검증한다.
 */
class AdminNoticePageTest : DescribeSpec({

	fun view(id: Long): AdminNoticeView =
		AdminNoticeView(id = id, title = "title-$id", createdAt = null)

	fun views(count: Int): AdminNoticeViews =
		AdminNoticeViews((1..count).map { view(it.toLong()) })

	describe("totalPages") {
		it("전체 개수를 size로 나눈 올림 값이다") {
			AdminNoticePage(content = views(2), page = 0, size = 2, totalElements = 5).totalPages shouldBe 3
		}

		it("전체 개수가 size로 나누어떨어지면 그 몫이다") {
			AdminNoticePage(content = views(2), page = 0, size = 2, totalElements = 4).totalPages shouldBe 2
		}

		it("전체가 0이면 0이다") {
			AdminNoticePage.empty(page = 0, size = 20).totalPages shouldBe 0
		}
	}

	describe("hasNext") {
		it("뒤에 페이지가 더 있으면 true다") {
			AdminNoticePage(content = views(2), page = 0, size = 2, totalElements = 5).hasNext shouldBe true
		}

		it("마지막(부분) 페이지면 false다") {
			AdminNoticePage(content = views(1), page = 2, size = 2, totalElements = 5).hasNext shouldBe false
		}

		it("정확히 꽉 찬 마지막 페이지면 false다") {
			AdminNoticePage(content = views(2), page = 1, size = 2, totalElements = 4).hasNext shouldBe false
		}
	}
})
