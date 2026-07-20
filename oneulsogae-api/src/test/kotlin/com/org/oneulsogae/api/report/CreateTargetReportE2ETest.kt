package com.org.oneulsogae.api.report

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.report.ReportType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.report.command.entity.QReportEntity
import com.org.oneulsogae.infra.report.command.entity.ReportEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.teammatch.command.entity.TeamEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * `POST /reports/v1/targets` E2E 테스트. (대상 직접 지정 신고)
 * 채팅방 없이 요청의 targetType(USER/TEAM)+targetId로 신고 대상을 정한다.
 * - USER: targetId가 실제 유저여야 하며 to_user_id로 저장한다.
 * - TEAM: targetId가 실제 팀이어야 하며 to_team_id로 저장한다.
 */
class CreateTargetReportE2ETest : AbstractIntegrationSupport({

	fun persistUser(providerId: String): Long =
		IntegrationUtil.persist(UserEntityFixture.create(providerId = providerId)).id!!

	// 초대중(INVITING) 팀을 만들고 teamId를 돌려준다. (신고 대상 준비 — 팀 엔티티를 직접 적재)
	fun invitingTeam(): Long =
		IntegrationUtil.persist(
			TeamEntity(name = "우리팀", gender = Gender.MALE, regionId = 1L, introduction = "함께 즐겁게 활동할 팀이에요", status = TeamStatus.INVITING),
		).id!!

	fun reportOf(fromUserId: Long): ReportEntity? {
		val report: QReportEntity = QReportEntity.reportEntity
		return IntegrationUtil.getQuery().selectFrom(report).where(report.fromUserId.eq(fromUserId)).fetchFirst()
	}

	describe("POST /reports/v1/targets") {

		context("USER 대상을 신고하면") {
			it("targetId를 to_user_id로 채워 저장한다 (200)") {
				val reporter = 9001L
				val targetUserId: Long = persistUser("target-user-1")

				post("/reports/v1/targets") {
					bearer(accessTokenFor(reporter))
					jsonBody("""{"type": "ABUSE_DEFAMATION", "targetType": "USER", "targetId": $targetUserId, "description": "욕설을 했어요"}""")
				} expect {
					status(200)
					body("success", true)
				}

				val saved: ReportEntity = reportOf(reporter)!!
				saved.type shouldBe ReportType.ABUSE_DEFAMATION
				saved.toUserId shouldBe targetUserId
				saved.toTeamId.shouldBeNull()
				saved.chatRoomId.shouldBeNull()
				saved.description shouldBe "욕설을 했어요"
			}
		}

		context("TEAM 대상을 신고하면") {
			it("targetId를 to_team_id로 채워 저장한다 (200)") {
				val reporter = 9101L
				val teamId: Long = invitingTeam()

				post("/reports/v1/targets") {
					bearer(accessTokenFor(reporter))
					jsonBody("""{"type": "FRAUD_IMPERSONATION", "targetType": "TEAM", "targetId": $teamId}""")
				} expect {
					status(200)
					body("success", true)
				}

				val saved: ReportEntity = reportOf(reporter)!!
				saved.type shouldBe ReportType.FRAUD_IMPERSONATION
				saved.toTeamId shouldBe teamId
				saved.toUserId.shouldBeNull()
				saved.chatRoomId.shouldBeNull()
			}
		}

		context("존재하지 않는 유저를 신고하면") {
			it("404(USER-001)를 반환하고 신고를 저장하지 않는다") {
				val reporter = 9003L

				post("/reports/v1/targets") {
					bearer(accessTokenFor(reporter))
					jsonBody("""{"type": "SPAM_ADVERTISEMENT", "targetType": "USER", "targetId": 999999}""")
				} expect {
					status(404)
					body("success", false)
					body("error.code", "USER-001")
				}

				reportOf(reporter).shouldBeNull()
			}
		}

		context("존재하지 않는 팀을 신고하면") {
			it("404(TEAM-005)를 반환하고 신고를 저장하지 않는다") {
				val reporter = 9004L

				post("/reports/v1/targets") {
					bearer(accessTokenFor(reporter))
					jsonBody("""{"type": "SPAM_ADVERTISEMENT", "targetType": "TEAM", "targetId": 999999}""")
				} expect {
					status(404)
					body("success", false)
					body("error.code", "TEAM-005")
				}

				reportOf(reporter).shouldBeNull()
			}
		}

		context("type·targetType·targetId가 없으면") {
			it("400을 반환한다") {
				post("/reports/v1/targets") {
					bearer(accessTokenFor(9005L))
					jsonBody("""{"description": "내용만 있음"}""")
				} expect {
					status(400)
					body("success", false)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QReportEntity.reportEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
