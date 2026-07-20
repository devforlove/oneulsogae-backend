package com.org.oneulsogae.api.admin
import io.kotest.core.annotation.Ignored

import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.restassured.RestAssured
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue

/**
 * 어드민 모임 생성 API E2E 테스트. (multipart: request(JSON) 파트 + image(파일) 파트)
 * 운영 생성(user_id null)으로 저장하고, 대표 이미지는 S3 오브젝트 키로 저장되는지 검증한다.
 * (실제 S3 업로드는 [com.org.oneulsogae.common.config.TestFileStorageConfig]의 페이크로 대체 — 넘긴 key를 그대로 저장)
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
class AdminGatheringCreateE2ETest : AbstractIntegrationSupport({

	val validRequest: String =
		"""
		{
			"type": "PARTY", "title": "주말 파티", "description": "함께 즐겨요", "region": "서울 강남구",
			"minParticipants": 2, "maxParticipants": 4
		}
		""".trimIndent()

	fun savedById(id: Long): GatheringEntity {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		return IntegrationUtil.getQuery().selectFrom(gathering).where(gathering.id.eq(id)).fetchOne()!!
	}

	describe("POST /admin/v1/gatherings") {

		context("어드민이 유효한 모임을 이미지와 함께 생성하면") {
			it("운영 생성(user_id null)·활성화(RECRUITING)·이미지 키와 함께 저장하고 gatheringId를 반환한다 (200)") {
				val gatheringId: Long = RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("request", validRequest, "application/json; charset=UTF-8")
					.multiPart("image", "party.png", "fake-image-bytes".toByteArray(), "image/png")
					.post("/admin/v1/gatherings")
					.then()
					.statusCode(200)
					.body("success", equalTo(true))
					.body("data.gatheringId", notNullValue())
					.extract().path<Int>("data.gatheringId").toLong()

				val saved: GatheringEntity = savedById(gatheringId)
				saved.userId shouldBe null
				saved.status shouldBe GatheringStatus.RECRUITING
				saved.type shouldBe GatheringType.PARTY
				saved.title shouldBe "주말 파티"
				saved.region shouldBe "서울 강남구"
				saved.minParticipants shouldBe 2
				saved.maxParticipants shouldBe 4
				// 페이크 스토리지가 넘긴 key를 그대로 저장 → gatherings/{uuid}.png 형태.
				saved.imageKey!! shouldStartWith "gatherings/"
			}
		}

		context("이미지 없이 생성하면") {
			it("image_key가 null로 저장된다 (200)") {
				val gatheringId: Long = RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("request", validRequest, "application/json; charset=UTF-8")
					.post("/admin/v1/gatherings")
					.then()
					.statusCode(200)
					.extract().path<Int>("data.gatheringId").toLong()

				savedById(gatheringId).imageKey shouldBe null
			}
		}

		context("허용하지 않는 형식(gif) 이미지를 올리면") {
			it("400(GATHER-009)을 반환한다") {
				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("request", validRequest, "application/json; charset=UTF-8")
					.multiPart("image", "anim.gif", "gif-bytes".toByteArray(), "image/gif")
					.post("/admin/v1/gatherings")
					.then()
					.statusCode(400)
					.body("success", equalTo(false))
					.body("error.code", equalTo("GATHER-009"))
			}
		}

		context("최소 인원이 2 미만이면") {
			it("400을 반환한다") {
				val body: String =
					"""{"type": "COOKING", "title": "쿠킹", "region": "서울", "minParticipants": 1, "maxParticipants": 4}"""
				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("request", body, "application/json; charset=UTF-8")
					.post("/admin/v1/gatherings")
					.then()
					.statusCode(400)
					.body("success", equalTo(false))
			}
		}

		context("ADMIN 권한이 없으면") {
			it("일반 사용자 토큰은 403을 반환한다") {
				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(3001L)}")
					.multiPart("request", validRequest, "application/json; charset=UTF-8")
					.post("/admin/v1/gatherings")
					.then()
					.statusCode(403)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
