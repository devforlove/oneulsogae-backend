package com.org.oneulsogae.api.mission

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MissionEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.mission.command.entity.QMissionCompletionEntity
import com.org.oneulsogae.infra.mission.command.entity.QMissionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity

/**
 * `GET /missions/v1` E2E 테스트.
 * 자기소개 길이에 따른 eligible 표시와, 활성 미션 목록 노출을 검증한다.
 */
class GetMissionsE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QMissionCompletionEntity.missionCompletionEntity)
		IntegrationUtil.deleteAll(QMissionEntity.missionEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}

	describe("GET /missions/v1") {

		context("자기소개가 100자 미만인 사용자가 조회하면") {
			it("자기소개 미션이 eligible=false로 내려간다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-list-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(50)))
				IntegrationUtil.persist(MissionEntityFixture.create())

				get("/missions/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 1)
					body("data[0].type", "WRITE_INTRODUCTION")
					body("data[0].rewardCoin", 50)
					body("data[0].completed", false)
					body("data[0].eligible", false)
				}
			}
		}

		context("자기소개가 100자 이상인 사용자가 조회하면") {
			it("자기소개 미션이 eligible=true로 내려간다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-list-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				IntegrationUtil.persist(MissionEntityFixture.create())

				get("/missions/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data[0].eligible", true)
					body("data[0].completed", false)
				}
			}
		}

		context("비활성 미션은") {
			it("목록에서 빠진다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-list-3")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				IntegrationUtil.persist(MissionEntityFixture.create(active = false))

				get("/missions/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.size()", 0)
				}
			}
		}
	}
})
