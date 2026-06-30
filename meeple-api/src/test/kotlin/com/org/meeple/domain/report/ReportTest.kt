package com.org.meeple.domain.report

import com.org.meeple.common.report.ReportTargetType
import com.org.meeple.common.report.ReportType
import com.org.meeple.core.report.command.domain.Report
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ReportTest : DescribeSpec({

	describe("Report.create") {

		context("대상 종류가 USER면") {
			it("targetId를 toUserId에 채우고 toTeamId는 비운다") {
				val report: Report = Report.create(
					type = ReportType.ABUSE_DEFAMATION,
					fromUserId = 1L,
					targetType = ReportTargetType.USER,
					targetId = 100L,
					description = "욕설",
				)

				report.toUserId shouldBe 100L
				report.toTeamId.shouldBeNull()
			}
		}

		context("대상 종류가 TEAM이면") {
			it("targetId를 toTeamId에 채우고 toUserId는 비운다") {
				val report: Report = Report.create(
					type = ReportType.FRAUD_IMPERSONATION,
					fromUserId = 1L,
					targetType = ReportTargetType.TEAM,
					targetId = 200L,
				)

				report.toTeamId shouldBe 200L
				report.toUserId.shouldBeNull()
			}
		}
	}
})
