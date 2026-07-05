package com.org.meeple.api.admin

import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import org.hamcrest.Matchers.hasSize

/**
 * 어드민 모임 조회 API E2E 테스트.
 * - GET /admin/v1/gatherings: 최신순(created_at desc, id desc) 페이징. 목록 행은 소개·참가비 상세 제외.
 * - GET /admin/v1/gatherings/{id}: 상세(소개·참가비 상세 포함), 없는 id 404(GATHER-008).
 */
class AdminGatheringQueryE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/gatherings") {

		it("최신순으로 페이징 조회하고 목록 행은 소개·참가비 상세를 노출하지 않는다") {
			IntegrationUtil.persist(GatheringEntityFixture.create(title = "첫 모임"))
			val lastId: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "둘째 모임", type = GatheringType.COOKING),
			).id!!

			get("/admin/v1/gatherings") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// 최신순: 마지막 저장분이 먼저.
				body("data.content[0].id", lastId.toInt())
				body("data.content[0].title", "둘째 모임")
				body("data.content[0].type", "COOKING")
				body("data.content[0].status", "RECRUITING")
				// 목록 행에 소개·참가비 상세는 없다.
				body("data.content[0].description", null)
				body("data.content[0].maleFee", null)
			}
		}

		it("size로 페이지 크기를 제한한다") {
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(GatheringEntityFixture.create(title = "모임-$index"))
			}

			get("/admin/v1/gatherings?page=0&size=2") {
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

	describe("GET /admin/v1/gatherings/{id}") {

		it("모임 상세를 소개·참가비 상세와 함께 반환한다 (200)") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "상세 모임",
					description = "상세 소개",
					region = "서울 마포구",
					maleFee = 10000,
					femaleFee = 8000,
					earlyBirdMaleFee = 7000,
					earlyBirdFemaleFee = 5000,
					discountMaleFee = 9000,
					discountFemaleFee = 7000,
				),
			).id!!

			get("/admin/v1/gatherings/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.title", "상세 모임")
				body("data.description", "상세 소개")
				body("data.region", "서울 마포구")
				body("data.maleFee", 10000)
				body("data.femaleFee", 8000)
				body("data.earlyBirdMaleFee", 7000)
				body("data.discountFemaleFee", 7000)
			}
		}

		it("얼리버드·할인가가 없는 모임은 해당 필드가 null이다 (200)") {
			val id: Long = IntegrationUtil.persist(GatheringEntityFixture.create(title = "특가 없는 모임")).id!!

			get("/admin/v1/gatherings/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.earlyBirdMaleFee", null)
				body("data.discountMaleFee", null)
			}
		}

		it("없는 id면 404다 (GATHER-008)") {
			get("/admin/v1/gatherings/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "GATHER-008")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
