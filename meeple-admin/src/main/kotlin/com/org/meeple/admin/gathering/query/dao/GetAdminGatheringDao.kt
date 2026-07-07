package com.org.meeple.admin.gathering.query.dao

import com.org.meeple.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringScheduleView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringViews
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType

/** 어드민 모임 조회 dao(query out-port). */
interface GetAdminGatheringDao {

	/** 모임을 최신순(created_at desc, id desc)으로 [offset]부터 [limit]건 조회한다. [status]·[type]이 주어지면 동등 필터를 적용한다. */
	fun findPage(offset: Long, limit: Int, status: GatheringStatus?, type: GatheringType?): AdminGatheringViews

	/** (soft delete 제외) [status]·[type] 필터를 반영한 전체 모임 개수. (페이징 메타데이터 계산용) */
	fun count(status: GatheringStatus?, type: GatheringType?): Long

	/** 모임 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminGatheringDetailView?

	/** [gatheringId] 모임의 일정 목록을 시작 시각 오름차순으로 조회한다. (soft delete 제외) */
	fun findSchedulesByGatheringId(gatheringId: Long): List<AdminGatheringScheduleView>
}
