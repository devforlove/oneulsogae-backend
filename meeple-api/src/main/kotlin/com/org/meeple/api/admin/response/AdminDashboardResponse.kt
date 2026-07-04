package com.org.meeple.api.admin.response

import com.org.meeple.core.admin.query.dto.AdminDashboardView

/** 어드민 대시보드 응답. 전체 사용자·금일 가입자·금일 DAU·금일 코인 결제액을 담는다. */
data class AdminDashboardResponse(
	val totalUsers: Long,
	val todaySignups: Long,
	val todayActiveUsers: Long,
	val todayCoinPurchaseAmount: Long,
) {
	companion object {

		fun of(view: AdminDashboardView): AdminDashboardResponse =
			AdminDashboardResponse(
				totalUsers = view.totalUsers,
				todaySignups = view.todaySignups,
				todayActiveUsers = view.todayActiveUsers,
				todayCoinPurchaseAmount = view.todayCoinPurchaseAmount,
			)
	}
}
