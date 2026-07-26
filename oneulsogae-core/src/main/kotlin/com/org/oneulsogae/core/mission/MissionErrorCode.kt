package com.org.oneulsogae.core.mission

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/** 미션(mission) 도메인 에러 코드. */
enum class MissionErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	/** 미션을 찾지 못함(없거나 비활성). */
	MISSION_NOT_FOUND("MISSION-001", "미션을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

	/** 아직 미션 자격(조건)을 충족하지 않음. */
	MISSION_NOT_ELIGIBLE("MISSION-002", "아직 미션 조건을 충족하지 않았습니다.", HttpStatus.BAD_REQUEST),

	/** 이미 완료·보상 수령한 미션. */
	MISSION_ALREADY_COMPLETED("MISSION-003", "이미 완료한 미션입니다.", HttpStatus.CONFLICT),
}
