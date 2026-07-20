package com.org.oneulsogae.api.admin
import io.kotest.core.annotation.Ignored

import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.restassured.RestAssured
import org.hamcrest.Matchers.equalTo

/**
 * 어드민 모임 전체 수정 API E2E 테스트. (multipart POST /admin/v1/gatherings/{id})
 * 전체 데이터를 교체하고, 이미지 파트가 없으면 기존 이미지를 유지하며, status는 보존한다.
 * (실제 S3 업로드는 [com.org.oneulsogae.common.config.TestFileStorageConfig]의 페이크로 대체)
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
class AdminGatheringUpdateE2ETest : AbstractIntegrationSupport({

	val updateRequest: String =
		"""
		{
			"type": "COOKING", "title": "수정된 모임", "description": "수정된 소개", "region": "부산 해운대구",
			"minParticipants": 3, "maxParticipants": 8
		}
		""".trimIndent()

	fun savedById(id: Long): GatheringEntity {
		val gathering: QGatheringEntity = QGatheringEntity.gatheringEntity
		return IntegrationUtil.getQuery().selectFrom(gathering).where(gathering.id.eq(id)).fetchOne()!!
	}

	describe("POST /admin/v1/gatherings/{id}") {

		context("새 이미지와 함께 전체 데이터를 수정하면") {
			it("전 필드를 교체하고 이미지 키를 바꾸며 status는 보존한다 (200)") {
				val id: Long = IntegrationUtil.persist(
					GatheringEntityFixture.create(
						title = "옛 모임",
						imageKey = "gatherings/old.png",
						status = GatheringStatus.RECRUITING,
					),
				).id!!

				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("request", updateRequest, "application/json; charset=UTF-8")
					.multiPart("image", "new.png", "new-image-bytes".toByteArray(), "image/png")
					.post("/admin/v1/gatherings/$id")
					.then()
					.statusCode(200)
					.body("success", equalTo(true))

				val saved: GatheringEntity = savedById(id)
				saved.type shouldBe GatheringType.COOKING
				saved.title shouldBe "수정된 모임"
				saved.region shouldBe "부산 해운대구"
				saved.minParticipants shouldBe 3
				saved.maxParticipants shouldBe 8
				// 새 이미지로 교체 → 키가 바뀐다.
				saved.imageKey!! shouldStartWith "gatherings/"
				saved.imageKey shouldNotBe "gatherings/old.png"
				// status는 수정으로 바뀌지 않는다.
				saved.status shouldBe GatheringStatus.RECRUITING
			}
		}

		context("이미지 파트 없이 수정하면") {
			it("기존 대표 이미지를 유지한다 (200)") {
				val id: Long = IntegrationUtil.persist(
					GatheringEntityFixture.create(title = "옛 모임", imageKey = "gatherings/keep.png"),
				).id!!

				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("request", updateRequest, "application/json; charset=UTF-8")
					.post("/admin/v1/gatherings/$id")
					.then()
					.statusCode(200)

				val saved: GatheringEntity = savedById(id)
				saved.title shouldBe "수정된 모임"
				saved.imageKey shouldBe "gatherings/keep.png"
			}
		}

		context("최소 인원이 2 미만이면") {
			it("400을 반환한다") {
				val id: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
				val body: String =
					"""{"type": "PARTY", "title": "제목", "region": "서울", "minParticipants": 1, "maxParticipants": 4}"""

				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("request", body, "application/json; charset=UTF-8")
					.post("/admin/v1/gatherings/$id")
					.then()
					.statusCode(400)
			}
		}

		context("없는 모임을 수정하면") {
			it("404(GATHER-008)를 반환한다") {
				RestAssured.given()
					.header("Authorization", "Bearer ${adminAccessTokenFor(9901L)}")
					.multiPart("request", updateRequest, "application/json; charset=UTF-8")
					.post("/admin/v1/gatherings/999999")
					.then()
					.statusCode(404)
					.body("error.code", equalTo("GATHER-008"))
			}
		}

		context("ADMIN 권한이 없으면") {
			it("일반 사용자 토큰은 403을 반환한다") {
				val id: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(3001L)}")
					.multiPart("request", updateRequest, "application/json; charset=UTF-8")
					.post("/admin/v1/gatherings/$id")
					.then()
					.statusCode(403)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
