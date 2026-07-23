package com.org.oneulsogae.api.admin

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.PopupEntityFixture
import com.org.oneulsogae.infra.image.entity.QImageTemplateEntity
import com.org.oneulsogae.infra.popup.command.entity.QPopupEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.restassured.RestAssured
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import java.util.Base64

/**
 * 어드민 팝업 API E2E 테스트. (생성·수정은 multipart: request(JSON) 파트 + image(파일) 파트)
 * - GET /admin/v1/popups: 노출 순서(display_order asc, id desc) 페이징. 개인 팝업 제외.
 * - GET /admin/v1/popups/{id}: 상세, 없는 id·개인 팝업 404(POPUP-001).
 * - POST /admin/v1/popups: 생성(이미지는 템플릿으로 저장), 기간 역전 400(POPUP-002), 제거 유형 400(POPUP-003), 잘못된 이미지 400(POPUP-004).
 * - POST /admin/v1/popups/{id}: 전체 수정 후 상세로 확인, 없는 id 404(POPUP-001).
 * (실제 S3 업로드는 TestFileStorageConfig의 페이크로 대체 — 넘긴 key를 그대로 저장)
 */
class AdminPopupE2ETest : AbstractIntegrationSupport({

	// 1x1 투명 PNG. 이미지 치수 추출(ImageIO)까지 실제로 통과하도록 유효한 PNG 바이트를 쓴다.
	val onePixelPng: ByteArray = Base64.getDecoder().decode(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
	)

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

		it("이미지와 함께 생성하면 템플릿이 저장되고 목록·상세에 이미지 URL이 내려간다 (200)") {
			RestAssured.given()
				.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
				.multiPart(
					"request",
					"""{"title":"신규 팝업","description":"신규 본문","displayOrder":1,
					"exposedFrom":"2026-07-01T00:00:00","exposedTo":"2026-08-01T00:00:00"}""",
					"application/json; charset=UTF-8",
				)
				.multiPart("image", "popup.png", onePixelPng, "image/png")
				.post("/admin/v1/popups")
				.then()
				.statusCode(200)
				.body("success", equalTo(true))

			get("/admin/v1/popups") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content[0].title", "신규 팝업")
				body("data.content[0].popUpType", PopupType.NORMAL.name)
				// 이미지가 템플릿으로 저장돼 미리보기 URL로 해석된다. (프록시 절대 URL + popups/ 키)
				body("data.content[0].imageUrl", notNullValue())
			}

			// 템플릿에는 공개 프록시 절대 URL(/images/popups/{uuid}.png)과 1x1 치수가 저장된다.
			val template = IntegrationUtil.getQuery()
				.selectFrom(QImageTemplateEntity.imageTemplateEntity)
				.fetchOne()!!
			template.imageUrl shouldContain "/images/popups/"
			template.imageWidth shouldBe 1
			template.imageHeight shouldBe 1
		}

		it("이미지 없이도 생성된다 (imageUrl null)") {
			RestAssured.given()
				.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
				.multiPart("request", """{"title":"이미지 없음","displayOrder":1}""", "application/json; charset=UTF-8")
				.post("/admin/v1/popups")
				.then()
				.statusCode(200)

			get("/admin/v1/popups") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.content[0].imageUrl", null)
			}
		}

		it("노출 종료가 시작보다 앞서면 400이다 (POPUP-002)") {
			RestAssured.given()
				.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
				.multiPart(
					"request",
					"""{"title":"기간 역전","displayOrder":1,
					"exposedFrom":"2026-08-01T00:00:00","exposedTo":"2026-07-01T00:00:00"}""",
					"application/json; charset=UTF-8",
				)
				.post("/admin/v1/popups")
				.then()
				.statusCode(400)
				.body("error.code", equalTo("POPUP-002"))
		}

		it("1회 조회 후 제거되는 유형이면 400이다 (POPUP-003)") {
			RestAssured.given()
				.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
				.multiPart(
					"request",
					"""{"title":"환불 유형","displayOrder":1,"popUpType":"MATCH_FAILED_REFUND"}""",
					"application/json; charset=UTF-8",
				)
				.post("/admin/v1/popups")
				.then()
				.statusCode(400)
				.body("error.code", equalTo("POPUP-003"))
		}

		it("이미지 파일을 해석할 수 없으면 400이다 (POPUP-004)") {
			RestAssured.given()
				.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
				.multiPart("request", """{"title":"깨진 이미지","displayOrder":1}""", "application/json; charset=UTF-8")
				.multiPart("image", "broken.png", "not-an-image".toByteArray(), "image/png")
				.post("/admin/v1/popups")
				.then()
				.statusCode(400)
				.body("error.code", equalTo("POPUP-004"))
		}
	}

	describe("POST /admin/v1/popups/{id}") {

		it("팝업 전체를 교체하고 상세에서 확인된다 (200)") {
			val id: Long = IntegrationUtil.persist(
				PopupEntityFixture.create(title = "수정 전", description = "본문 전", displayOrder = 1),
			).id!!

			RestAssured.given()
				.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
				.multiPart(
					"request",
					"""{"title":"수정 후","description":"본문 후","displayOrder":5,"buttonText":"이동"}""",
					"application/json; charset=UTF-8",
				)
				.post("/admin/v1/popups/$id")
				.then()
				.statusCode(200)
				.body("success", equalTo(true))

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
			RestAssured.given()
				.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
				.multiPart("request", """{"title":"없음","displayOrder":1}""", "application/json; charset=UTF-8")
				.post("/admin/v1/popups/999999")
				.then()
				.statusCode(404)
				.body("error.code", equalTo("POPUP-001"))
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPopupEntity.popupEntity)
		IntegrationUtil.deleteAll(QImageTemplateEntity.imageTemplateEntity)
	}
})
