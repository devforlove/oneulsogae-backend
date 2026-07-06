package com.org.meeple.api.gathering

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import java.time.LocalDateTime

/**
 * 유저 모임 조회 API E2E 테스트.
 * - GET /gatherings/v1: 모집중(RECRUITING)만, 타입별 그룹(3종 모두 포함, 없으면 빈 배열), 타입 내 gatheringAt 임박순.
 *   항목은 id·imageUrl(presigned)·region·title·gatheringAt을 포함한다.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class GatheringQueryE2ETest : AbstractIntegrationSupport({

	describe("GET /gatherings/v1") {

		it("모집중 모임을 타입별 그룹으로 반환하고 타입 3종을 선언 순서로 모두 포함한다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "쿠킹 모임",
					type = GatheringType.COOKING,
					status = GatheringStatus.RECRUITING,
					imageKey = "gatherings/cooking.png",
					region = "서울 마포구",
					gatheringAt = LocalDateTime.of(2999, 1, 1, 19, 0, 0),
				),
			)

			get("/gatherings/v1") {
				bearer(accessTokenFor(7001L))
			} expect {
				status(200)
				body("success", true)
				// 타입 3종을 선언 순서로 모두 포함한다.
				body("data.groups", hasSize<Any>(3))
				body(
					"data.groups.type",
					contains("ONE_ON_ONE_ROTATION", "COOKING", "PARTY"),
				)
				// 모임 없는 타입은 빈 배열.
				body("data.groups[0].gatherings", hasSize<Any>(0))
				body("data.groups[2].gatherings", hasSize<Any>(0))
				// 쿠킹 그룹에 1건, 필드 확인.
				body("data.groups[1].typeDescription", "쿠킹")
				body("data.groups[1].gatherings", hasSize<Any>(1))
				body("data.groups[1].gatherings[0].title", "쿠킹 모임")
				body("data.groups[1].gatherings[0].region", "서울 마포구")
				body("data.groups[1].gatherings[0].imageUrl", "https://presigned.test/gatherings/cooking.png")
				body("data.groups[1].gatherings[0].gatheringAt", "2999-01-01T19:00:00")
			}
		}

		it("모집중이 아닌 모임(DRAFT·CANCELED·FINISHED 등)은 제외한다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "준비중", type = GatheringType.PARTY, status = GatheringStatus.DRAFT),
			)
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "취소됨", type = GatheringType.PARTY, status = GatheringStatus.CANCELED),
			)
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "모집중", type = GatheringType.PARTY, status = GatheringStatus.RECRUITING),
			)

			get("/gatherings/v1") {
				bearer(accessTokenFor(7001L))
			} expect {
				status(200)
				body("data.groups[2].type", "PARTY")
				body("data.groups[2].gatherings", hasSize<Any>(1))
				body("data.groups[2].gatherings[0].title", "모집중")
			}
		}

		it("같은 타입 그룹 안에서 모임 일시 임박순으로 정렬한다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "나중",
					type = GatheringType.PARTY,
					status = GatheringStatus.RECRUITING,
					gatheringAt = LocalDateTime.of(2999, 12, 31, 18, 0, 0),
				),
			)
			IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "먼저",
					type = GatheringType.PARTY,
					status = GatheringStatus.RECRUITING,
					gatheringAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
				),
			)

			get("/gatherings/v1") {
				bearer(accessTokenFor(7001L))
			} expect {
				status(200)
				body("data.groups[2].gatherings[0].title", "먼저")
				body("data.groups[2].gatherings[1].title", "나중")
			}
		}

		it("대표 이미지가 없는 모임은 imageUrl이 null이다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "이미지 없음",
					type = GatheringType.PARTY,
					status = GatheringStatus.RECRUITING,
					imageKey = null,
				),
			)

			get("/gatherings/v1") {
				bearer(accessTokenFor(7001L))
			} expect {
				status(200)
				body("data.groups[2].gatherings[0].imageUrl", null)
			}
		}

		it("토큰이 없으면 401이다") {
			get("/gatherings/v1") { } expect {
				status(401)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
