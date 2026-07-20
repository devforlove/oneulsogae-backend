package com.org.oneulsogae.core.gathering.query.service.port.`in`

import com.org.oneulsogae.core.gathering.query.dto.GatheringDetailView
import com.org.oneulsogae.core.gathering.query.dto.GatheringProductIdentity
import com.org.oneulsogae.core.gathering.query.dto.GroupedGatherings

/** 유저용 모임 조회 인포트(유스케이스). */
interface GetGatheringsUseCase {

	/** 모집중 모임을 타입별로 그룹핑해 조회한다. (모든 타입 포함, 타입 내 최신 등록순) */
	fun getGatherings(): GroupedGatherings

	/** 모집중 모임 한 건의 상세를 id로 조회한다. 없거나 모집중이 아니면 404(GATHERING-001). */
	fun getGathering(id: Long): GatheringDetailView

	/**
	 * 모집중 모임 상세를 일정별 참가자 로스터(승인대기·참가)까지 포함해 조회한다. (상세 화면 전용)
	 * [getGathering]과 달리 참가자를 함께 싣는다 — 체크아웃 등 로스터가 불필요한 경로는 [getGathering]을 쓴다.
	 */
	fun getGatheringWithParticipants(id: Long): GatheringDetailView

	/** 상품 한 건의 식별 정보(모임·일정·성별)를 id로 조회한다. 없으면 404(GATHERING-006). */
	fun getProduct(productId: Long): GatheringProductIdentity
}
