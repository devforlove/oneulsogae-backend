package com.org.meeple.domain.notice

import com.org.meeple.core.notice.query.dto.NoticePage
import com.org.meeple.core.notice.query.dto.NoticeView
import com.org.meeple.core.notice.query.dto.NoticeViews
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [NoticePage] read model 유닛 테스트.
 * limit/offset 페이징 메타데이터 계산(전체 페이지 수 [NoticePage.totalPages], 다음 페이지 존재 [NoticePage.hasNext])을 검증한다.
 */
class NoticePageTest : DescribeSpec({

	fun view(id: Long): NoticeView =
		NoticeView(id = id, title = "title-$id", description = "description-$id", createdAt = null)

	fun views(count: Int): NoticeViews =
		NoticeViews((1..count).map { view(it.toLong()) })

	describe("totalPages") {
		it("전체 개수를 size로 나눈 올림 값이다") {
			NoticePage(notices = views(2), page = 0, size = 2, totalElements = 5).totalPages shouldBe 3
		}

		it("전체 개수가 size로 나누어떨어지면 그 몫이다") {
			NoticePage(notices = views(2), page = 0, size = 2, totalElements = 4).totalPages shouldBe 2
		}

		it("전체가 0이면 0이다") {
			NoticePage.empty(page = 0, size = 20).totalPages shouldBe 0
		}
	}

	describe("hasNext") {
		it("뒤에 페이지가 더 있으면 true다") {
			NoticePage(notices = views(2), page = 0, size = 2, totalElements = 5).hasNext shouldBe true
		}

		it("마지막(부분) 페이지면 false다") {
			NoticePage(notices = views(1), page = 2, size = 2, totalElements = 5).hasNext shouldBe false
		}

		it("정확히 꽉 찬 마지막 페이지면 false다") {
			NoticePage(notices = views(2), page = 1, size = 2, totalElements = 4).hasNext shouldBe false
		}
	}
})
