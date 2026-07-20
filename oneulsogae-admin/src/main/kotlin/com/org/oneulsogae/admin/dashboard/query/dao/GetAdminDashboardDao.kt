package com.org.oneulsogae.admin.dashboard.query.dao

import com.org.oneulsogae.admin.dashboard.query.dto.AdminDashboardView
import java.time.LocalDateTime

/** 어드민 대시보드 지표 조회 dao. 구현은 infra가 담당한다. */
interface GetAdminDashboardDao {

	/** [todayStart](금일 00시)를 기준으로 전체/금일 지표를 집계해 돌려준다. */
	fun load(todayStart: LocalDateTime): AdminDashboardView
}
