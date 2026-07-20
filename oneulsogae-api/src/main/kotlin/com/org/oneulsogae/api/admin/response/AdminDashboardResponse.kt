package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.dashboard.query.dto.AdminDashboardView

/** 어드민 대시보드 응답. 전체 사용자·금일 가입자·금일 DAU·금일 코인 결제액·진행중 매치·미처리 신고를 담는다. */
data class AdminDashboardResponse(
	val totalUsers: Long,
	val todaySignups: Long,
	val todayActiveUsers: Long,
	val todayCoinPurchaseAmount: Long,
	val ongoingSoloMatches: Long,
	val ongoingTeamMatches: Long,
	val pendingReports: Long,
) {
	companion object {

		fun of(view: AdminDashboardView): AdminDashboardResponse =
			AdminDashboardResponse(
				totalUsers = view.totalUsers,
				todaySignups = view.todaySignups,
				todayActiveUsers = view.todayActiveUsers,
				todayCoinPurchaseAmount = view.todayCoinPurchaseAmount,
				ongoingSoloMatches = view.ongoingSoloMatches,
				ongoingTeamMatches = view.ongoingTeamMatches,
				pendingReports = view.pendingReports,
			)
	}
}
