package com.org.oneulsogae.core.mission.application.evaluator

import com.org.oneulsogae.common.mission.MissionType

/**
 * 미션 유형별 자격 판정기. 신규 미션 유형은 이 인터페이스 구현 빈을 추가해 확장한다.
 * (보상·문구·활성 여부는 DB missions 행이 담고, 자격 조건은 이 평가자가 코드로 판정한다)
 */
interface MissionEvaluator {

	/** 이 평가자가 [type]을 판정할 수 있는지 여부. */
	fun supports(type: MissionType): Boolean

	/** [userId]가 이 미션의 자격(조건)을 충족했는지 여부. */
	fun isEligible(userId: Long): Boolean
}
