package com.org.meeple.core.admin.query.dto

/**
 * 어드민 대시보드 read model.
 * - [totalUsers]: 전체 사용자 수. (탈퇴 처리(soft delete)된 계정 제외)
 * - [todaySignups]: 금일 생성된 계정 수. (온보딩 완료 여부 무관, created_at 기준)
 * - [todayActiveUsers]: 금일 로그인한 사용자 수(DAU). (last_login_at 기준)
 * - [todayCoinPurchaseAmount]: 금일 결제(PURCHASE)로 적립된 코인 수량 합.
 */
data class AdminDashboardView(
	val totalUsers: Long,
	val todaySignups: Long,
	val todayActiveUsers: Long,
	val todayCoinPurchaseAmount: Long,
)
