package com.org.oneulsogae.api.admin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.NoticeEntityFixture
import com.org.oneulsogae.infra.notice.command.entity.QNoticeEntity
import org.hamcrest.Matchers.hasSize

/**
 * 어드민 공지 API E2E 테스트.
 * - GET /admin/v1/notices: 최신순(created_at desc, id desc) 페이징. 목록 행은 id/title/createdAt만.
 * - GET /admin/v1/notices/{id}: 상세(본문 포함), 없는 id 404(NOTICE-001).
 * - POST /admin/v1/notices: 공지 추가 후 재조회로 확인, 잘못된 입력 400.
 */
class AdminNoticeE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/notices") {

		it("최신순으로 페이징 조회하고 목록 행은 id/title/createdAt만 노출한다") {
			IntegrationUtil.persist(NoticeEntityFixture.create(title = "첫 공지", description = "본문1"))
			val lastId: Long = IntegrationUtil.persist(
				NoticeEntityFixture.create(title = "둘째 공지", description = "본문2"),
			).id!!

			get("/admin/v1/notices") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// 최신순: 마지막 저장분이 먼저.
				body("data.content[0].id", lastId.toInt())
				body("data.content[0].title", "둘째 공지")
				// 목록 행에 본문(description)은 없다.
				body("data.content[0].description", null)
			}
		}

		it("size로 페이지 크기를 제한한다") {
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(NoticeEntityFixture.create(title = "공지-$index"))
			}

			get("/admin/v1/notices?page=0&size=2") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.content", hasSize<Any>(2))
				body("data.size", 2)
				body("data.totalElements", 3)
				body("data.totalPages", 2)
				body("data.hasNext", true)
			}
		}
	}

	describe("GET /admin/v1/notices/{id}") {

		it("공지 상세를 본문과 함께 반환한다 (200)") {
			val id: Long = IntegrationUtil.persist(
				NoticeEntityFixture.create(title = "상세 공지", description = "상세 본문"),
			).id!!

			get("/admin/v1/notices/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.title", "상세 공지")
				body("data.description", "상세 본문")
			}
		}

		it("없는 id면 404다 (NOTICE-001)") {
			get("/admin/v1/notices/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "NOTICE-001")
			}
		}
	}

	describe("POST /admin/v1/notices") {

		it("공지를 추가하면 목록에서 조회된다 (200)") {
			post("/admin/v1/notices") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"title":"신규 공지","description":"신규 본문"}""")
			} expect {
				status(200)
				body("success", true)
			}

			get("/admin/v1/notices") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content[0].title", "신규 공지")
			}
		}

		it("제목이 비면 400이다") {
			post("/admin/v1/notices") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"title":"","description":"본문"}""")
			} expect {
				status(400)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QNoticeEntity.noticeEntity)
	}
})
