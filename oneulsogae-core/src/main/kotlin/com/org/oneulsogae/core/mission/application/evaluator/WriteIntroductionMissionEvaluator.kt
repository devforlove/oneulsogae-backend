package com.org.oneulsogae.core.mission.application.evaluator

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Component

/**
 * 자기소개 작성 미션 평가자. 프로필 자기소개가 [MIN_INTRODUCTION_LENGTH]자 이상(앞뒤 공백 제외)이면 자격이 있다.
 * 소개는 user 도메인 in-port([GetUserDetailUseCase])로 조회한다.
 */
@Component
class WriteIntroductionMissionEvaluator(
	private val getUserDetailUseCase: GetUserDetailUseCase,
) : MissionEvaluator {

	override fun supports(type: MissionType): Boolean = type == MissionType.WRITE_INTRODUCTION

	override fun isEligible(userId: Long): Boolean {
		val introduction: String? = getUserDetailUseCase.findByUserId(userId)?.introduction
		return (introduction?.trim()?.length ?: 0) >= MIN_INTRODUCTION_LENGTH
	}

	companion object {
		/** 자격을 얻는 최소 자기소개 길이. */
		const val MIN_INTRODUCTION_LENGTH: Int = 100
	}
}
