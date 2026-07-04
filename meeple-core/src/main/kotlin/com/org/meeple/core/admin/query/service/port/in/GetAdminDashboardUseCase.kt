package com.org.meeple.core.admin.query.service.port.`in`

import com.org.meeple.core.admin.query.dto.AdminDashboardView

/** 어드민 대시보드 지표(전체 사용자·금일 가입자·금일 DAU·금일 코인 결제액) 조회 유스케이스. */
interface GetAdminDashboardUseCase {

	fun get(): AdminDashboardView
}
