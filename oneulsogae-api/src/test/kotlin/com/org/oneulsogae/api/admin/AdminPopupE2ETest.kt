package com.org.oneulsogae.api.admin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.PopupEntityFixture
import com.org.oneulsogae.infra.popup.command.entity.QPopupEntity
import org.hamcrest.Matchers.hasSize

/**
 * 어드민 팝업 API E2E 테스트.
 * - GET /admin/v1/popups: 노출 순서(display_order asc, id desc) 페이징. 개인 팝업 제외.
 * - GET /admin/v1/popups/{id}: 상세, 없는 id·개인 팝업 404(POPUP-001).
 * - POST /admin/v1/popups: 생성 후 재조회로 확인, 기간 역전 400(POPUP-002), 제거 유형 400(POPUP-003).
 * - POST /admin/v1/popups/{id}: 전체 수정 후 상세로 확인, 없는 id 404(POPUP-001).
 */
class AdminPopupE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/popups") {

		it("전역 팝업만 노출 순서로 페이징 조회한다 (개인 팝업 제외)") {
			val secondId: Long = IntegrationUtil.persist(
				PopupEntityFixture.create(title = "둘째 팝업", displayOrder = 2),
			).id!!
			val firstId: Long = IntegrationUtil.persist(
				PopupEntityFixture.create(title = "첫 팝업", displayOrder = 1),
			).id!!
			// 개인(환불 안내) 팝업은 어드민 목록에 나오지 않는다.
			IntegrationUtil.persist(
				PopupEntityFixture.create(title = "개인 팝업", displayOrder = 0, userId = 1L),
			)

			get("/admin/v1/popups") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// display_order 오름차순.
				body("data.content[0].id", firstId.toInt())
				body("data.content[0].title", "첫 팝업")
				body("data.content[1].id", secondId.toInt())
				// 목록 행에 상세 필드(본문)는 없다.
				body("data.content[0].description", null)
			}
		}

		it("size로 페이지 크기를 제한한다") {
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(PopupEntityFixture.create(title = "팝업-$index", displayOrder = index))
			}

			get("/admin/v1/popups?page=0&size=2") {
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

	describe("GET /admin/v1/popups/{id}") {

		it("팝업 상세를 본문·링크와 함께 반환한다 (200)") {
			val id: Long = IntegrationUtil.persist(
				PopupEntityFixture.create(
					title = "상세 팝업",
					description = "상세 본문",
					displayOrder = 1,
					linkUrl = "https://example.com",
					buttonText = "확인",
				),
			).id!!

			get("/admin/v1/popups/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.title", "상세 팝업")
				body("data.description", "상세 본문")
				body("data.linkUrl", "https://example.com")
				body("data.buttonText", "확인")
				body("data.popUpType", PopupType.NORMAL.name)
			}
		}

		it("없는 id면 404다 (POPUP-001)") {
			get("/admin/v1/popups/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "POPUP-001")
			}
		}

		it("개인 팝업은 어드민 상세에서 조회할 수 없다 (POPUP-001)") {
			val personalId: Long = IntegrationUtil.persist(
				PopupEntityFixture.create(title = "개인 팝업", userId = 1L),
			).id!!

			get("/admin/v1/popups/$personalId") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("error.code", "POPUP-001")
			}
		}
	}

	describe("POST /admin/v1/popups") {

		it("팝업을 생성하면 목록에서 조회된다 (200)") {
			post("/admin/v1/popups") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody(
					"""{"title":"신규 팝업","description":"신규 본문","displayOrder":1,
					"exposedFrom":"2026-07-01T00:00:00","exposedTo":"2026-08-01T00:00:00"}""",
				)
			} expect {
				status(200)
				body("success", true)
			}

			get("/admin/v1/popups") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content[0].title", "신규 팝업")
				body("data.content[0].popUpType", PopupType.NORMAL.name)
			}
		}

		it("노출 종료가 시작보다 앞서면 400이다 (POPUP-002)") {
			post("/admin/v1/popups") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody(
					"""{"title":"기간 역전","displayOrder":1,
					"exposedFrom":"2026-08-01T00:00:00","exposedTo":"2026-07-01T00:00:00"}""",
				)
			} expect {
				status(400)
				body("error.code", "POPUP-002")
			}
		}

		it("1회 조회 후 제거되는 유형이면 400이다 (POPUP-003)") {
			post("/admin/v1/popups") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"title":"환불 유형","displayOrder":1,"popUpType":"MATCH_FAILED_REFUND"}""")
			} expect {
				status(400)
				body("error.code", "POPUP-003")
			}
		}

		it("노출 순서가 없으면 400이다") {
			post("/admin/v1/popups") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"title":"순서 없음"}""")
			} expect {
				status(400)
			}
		}
	}

	describe("POST /admin/v1/popups/{id}") {

		it("팝업 전체를 교체하고 상세에서 확인된다 (200)") {
			val id: Long = IntegrationUtil.persist(
				PopupEntityFixture.create(title = "수정 전", description = "본문 전", displayOrder = 1),
			).id!!

			post("/admin/v1/popups/$id") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"title":"수정 후","description":"본문 후","displayOrder":5,"buttonText":"이동"}""")
			} expect {
				status(200)
				body("success", true)
			}

			get("/admin/v1/popups/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.title", "수정 후")
				body("data.description", "본문 후")
				body("data.displayOrder", 5)
				body("data.buttonText", "이동")
			}
		}

		it("없는 id면 404다 (POPUP-001)") {
			post("/admin/v1/popups/999999") {
				bearer(adminAccessTokenFor(9901L))
				jsonBody("""{"title":"없음","displayOrder":1}""")
			} expect {
				status(404)
				body("error.code", "POPUP-001")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPopupEntity.popupEntity)
	}
})
