package com.org.oneulsogae.core.mission.application.evaluator

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Component

/**
 * 학교 인증 미션 평가자. 프로필에 학교명이 채워져 있으면 자격이 있다.
 * 인증(이메일·이미지 심사)이 끝나면 학교명이 채워지므로, 회사 인증([com.org.oneulsogae.core.user.query.service.port.in.CheckCompanyVerifiedUseCase])과
 * 같은 신호를 쓴다. 프로필은 user 도메인 in-port([GetUserDetailUseCase])로 조회한다.
 */
@Component
class VerifyUniversityMissionEvaluator(
	private val getUserDetailUseCase: GetUserDetailUseCase,
) : MissionEvaluator {

	override fun supports(type: MissionType): Boolean = type == MissionType.VERIFY_UNIVERSITY

	override fun isEligible(userId: Long): Boolean =
		!getUserDetailUseCase.findByUserId(userId)?.universityName.isNullOrBlank()
}
