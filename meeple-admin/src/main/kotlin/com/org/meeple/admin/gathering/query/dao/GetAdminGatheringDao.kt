package com.org.meeple.admin.gathering.query.dao

import com.org.meeple.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringViews

/** 어드민 모임 조회 dao(query out-port). */
interface GetAdminGatheringDao {

	/** 모임을 최신순(created_at desc, id desc)으로 [offset]부터 [limit]건 조회한다. */
	fun findPage(offset: Long, limit: Int): AdminGatheringViews

	/** (soft delete 제외) 전체 모임 개수. (페이징 메타데이터 계산용) */
	fun count(): Long

	/** 모임 상세를 [id]로 조회한다. 없거나 soft-delete면 null. */
	fun findDetailById(id: Long): AdminGatheringDetailView?
}
