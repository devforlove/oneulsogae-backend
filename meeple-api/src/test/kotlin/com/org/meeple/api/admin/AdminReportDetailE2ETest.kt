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

/**
 * `GET /admin/v1/reports/{id}` E2E 테스트. 유저 신고 상세(사유·채팅방 포함) 200,
 * 없는 id·팀 신고 id는 404(REPORT-001)를 검증한다.
 */
class AdminReportDetailE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/reports/{id}") {

		it("유저 신고 상세를 반환한다 (200)") {
			// 신고자·대상 유저를 persist하고 반환값에서 실제 PK를 캡처한다.
			val reporterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "d-reporter", email = "r@test.com")).id!!
			val targetId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "d-target", email = "t@test.com")).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = reporterId, nickname = "신고자"))
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = targetId, nickname = "대상"))
			val id: Long = IntegrationUtil.persist(
				ReportEntityFixture.create(
					type = ReportType.FRAUD_IMPERSONATION,
					fromUserId = reporterId,
					toUserId = targetId,
					description = "사칭 신고 상세 사유",
				),
			).id!!

			get("/admin/v1/reports/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.type", "FRAUD_IMPERSONATION")
				body("data.typeLabel", "사기·사칭")
				body("data.description", "사칭 신고 상세 사유")
				body("data.reporterNickname", "신고자")
				body("data.targetNickname", "대상")
			}
		}

		it("없는 신고 id면 404다") {
			get("/admin/v1/reports/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "REPORT-001")
			}
		}

		it("팀 신고 id면 404다 (유저 신고만 조회)") {
			val reporterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "team-reporter")).id!!
			val teamReportId: Long = IntegrationUtil.persist(
				ReportEntityFixture.create(type = ReportType.ETC, fromUserId = reporterId, toUserId = null),
			).id!!

			get("/admin/v1/reports/$teamReportId") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("error.code", "REPORT-001")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QReportEntity.reportEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
