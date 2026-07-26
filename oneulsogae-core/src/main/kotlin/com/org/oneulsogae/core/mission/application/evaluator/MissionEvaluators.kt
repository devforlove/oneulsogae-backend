package com.org.oneulsogae.core.mission.application.evaluator

import com.org.oneulsogae.common.mission.MissionType
import org.springframework.stereotype.Component

/**
 * 미션 유형에 맞는 [MissionEvaluator]를 고르는 resolver. 주입된 평가자 중 [MissionEvaluator.supports]인 것을 반환한다.
 * 대응 평가자가 없으면 missions 행에 평가자가 붙지 않은 배포 오류이므로 [IllegalStateException]을 던진다(조용히 부적격 처리하지 않는다).
 */
@Component
class MissionEvaluators(
	private val evaluators: List<MissionEvaluator>,
) {

	fun resolve(type: MissionType): MissionEvaluator =
		evaluators.firstOrNull { evaluator: MissionEvaluator -> evaluator.supports(type) }
			?: throw IllegalStateException("미션 유형에 대응하는 평가자가 없습니다: $type")
}
