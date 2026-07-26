package com.org.oneulsogae.common.mission

/**
 * 미션 유형. 유형별 자격 판정 평가자([com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluator])를 고르는 자연키다.
 * 신규 미션은 값을 추가하고 대응 평가자를 붙여 확장한다.
 */
enum class MissionType {

	/** 자기소개를 일정 길이 이상 작성. */
	WRITE_INTRODUCTION,

	/** 학교(대학) 인증 완료. */
	VERIFY_UNIVERSITY,
}
