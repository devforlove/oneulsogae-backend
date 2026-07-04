package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.report.ReportType
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.ReportEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.report.command.entity.QReportEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import org.hamcrest.Matchers.hasSize

/**
 * `GET /admin/v1/reports` E2E 테스트. 유저 신고만 최신순 페이징 조회하고,
 * 팀 신고(to_user_id 없음)는 제외되며 신고자·대상 표시 정보가 조인됨을 검증한다.
 */
class AdminReportListE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/reports") {

		it("유저 신고만 최신순으로 페이징 조회한다 (팀 신고 제외)") {
			// 신고자·대상 유저와 프로필(닉네임) 준비. persist 반환값에서 실제 PK를 캡처한다.
			val reporterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "rep-reporter", email = "reporter@test.com")).id!!
			val targetId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "rep-target", email = "target@test.com")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = reporterId, nickname = "신고자닉"))
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = targetId, nickname = "대상닉"))

			// 유저 신고 2건 + 팀 신고 1건(제외 대상).
			IntegrationUtil.persist(ReportEntityFixture.create(type = ReportType.ABUSE_DEFAMATION, fromUserId = reporterId, toUserId = targetId))
			IntegrationUtil.persist(ReportEntityFixture.create(type = ReportType.SPAM_ADVERTISEMENT, fromUserId = reporterId, toUserId = targetId))
			IntegrationUtil.persist(ReportEntityFixture.create(type = ReportType.ETC, fromUserId = reporterId, toUserId = null))

			get("/admin/v1/reports") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// 최신순: 마지막에 저장한 유저 신고(SPAM_ADVERTISEMENT)가 먼저.
				body("data.content[0].type", "SPAM_ADVERTISEMENT")
				body("data.content[0].typeLabel", "스팸·광고")
				body("data.content[0].statusLabel", "접수")
				body("data.content[0].reporterNickname", "신고자닉")
				body("data.content[0].reporterEmail", "reporter@test.com")
				body("data.content[0].targetNickname", "대상닉")
				body("data.content[0].targetUserId", targetId.toInt())
			}
		}

		it("size로 페이지 크기를 제한한다") {
			val reporterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "pg-1")).id!!
			val targetId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "pg-2")).id!!
			(1..3).forEach { IntegrationUtil.persist(ReportEntityFixture.create(fromUserId = reporterId, toUserId = targetId)) }

			get("/admin/v1/reports?page=0&size=2") {
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

	afterTest {
		IntegrationUtil.deleteAll(QReportEntity.reportEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
