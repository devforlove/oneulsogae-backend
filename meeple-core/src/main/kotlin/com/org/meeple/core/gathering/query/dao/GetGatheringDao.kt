package com.org.meeple.core.gathering.query.dao

import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringParticipantView
import com.org.meeple.core.gathering.query.dto.GatheringProductIdentity
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import com.org.meeple.core.gathering.query.dto.GatheringViews

/**
 * 유저용 모임 조회 dao(query out-port). (조회 전용 read model 반환)
 * 모집중(RECRUITING) 모임만 조회한다. 실제 구현은 infra 레이어가 담당한다.
 */
interface GetGatheringDao {

	/** 모집중 모임을 최신 등록순으로 조회한다. (imageUrl은 서비스가 채운다) */
	fun findRecruiting(): GatheringViews

	/** 모집중 모임 한 건을 id로 조회한다. 없거나 모집중이 아니면 null. (imageUrl은 서비스가 채운다) */
	fun findRecruitingDetailById(id: Long): GatheringDetailView?

	/** [gatheringId] 모임의 일정 목록을 시작 시각 오름차순으로 조회한다. (soft delete 제외) */
	fun findSchedulesByGatheringId(gatheringId: Long): List<GatheringScheduleView>

	/** 상품 한 건의 식별 정보를 id로 조회한다. 없으면 null. (soft delete 제외) */
	fun findProductById(productId: Long): GatheringProductIdentity?

	/**
	 * [scheduleIds] 일정들의 참가자(승인대기·참가)를 프로필과 함께 조회한다. (거절·취소 제외, soft delete 제외)
	 * 비면 조회 없이 빈 리스트. 서비스가 scheduleId로 묶어 일정별 로스터로 만든다.
	 */
	fun findParticipantsByScheduleIds(scheduleIds: List<Long>): List<GatheringParticipantView>
}
