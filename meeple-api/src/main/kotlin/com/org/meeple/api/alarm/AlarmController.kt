package com.org.meeple.api.alarm

import com.org.meeple.api.alarm.response.AlarmResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.alarm.query.service.port.`in`.GetAlarmsUseCase
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 알람 엔드포인트. (모두 인증 필요)
 * - GET /: 현재 로그인 사용자의 알람 목록을 최신순으로 조회한다.
 */
@Tag(name = "알람", description = "알람 엔드포인트. 로그인 사용자의 알람 목록을 조회한다.")
@RestController
@RequestMapping("/alarms/v1")
class AlarmController(
	private val getAlarmsUseCase: GetAlarmsUseCase,
) {

	/** 현재 로그인 사용자의 알람 목록을 최신순으로 조회한다. (각 알람에 발신자 프로필 froms 포함) */
	@Operation(summary = "내 알람 목록 조회", description = "현재 로그인 사용자의 알람 목록을 최신순으로 조회한다. 각 알람에 발신자 프로필(froms)이 포함된다.")
	@GetMapping
	fun myAlarms(
		@LoginUser user: AuthUser,
	): ApiResponse<List<AlarmResponse>> =
		ApiResponse.success(AlarmResponse.listOf(getAlarmsUseCase.getAlarms(user.id)))
}
