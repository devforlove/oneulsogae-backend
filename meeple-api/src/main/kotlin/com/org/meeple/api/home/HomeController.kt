package com.org.meeple.api.home

import com.org.meeple.api.home.response.HomeSummaryResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.alarm.query.service.port.`in`.GetUnreadAlarmCountUseCase
import com.org.meeple.core.coin.query.service.port.`in`.GetCoinBalanceUseCase
import com.org.meeple.core.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 메인 화면 진입 시 한 번에 조회하는 요약 엔드포인트. (인증 필요)
 * 코인·알람 각 도메인의 조회 유스케이스를 주입받아 응답으로 조립만 한다. (도메인 로직을 두지 않는다)
 */
@Tag(name = "홈", description = "메인 화면 진입 요약. 코인 잔액과 미수신 알람 개수를 한 번에 조회한다.")
@RestController
@RequestMapping("/home/v1")
class HomeController(
	private val getCoinBalanceUseCase: GetCoinBalanceUseCase,
	private val getUnreadAlarmCountUseCase: GetUnreadAlarmCountUseCase,
) {

	/** 현재 로그인 사용자의 코인 잔액과 미수신(읽지 않은) 알람 개수를 한 번에 조회한다. */
	@Operation(summary = "메인 진입 요약 조회", description = "현재 로그인 사용자의 코인 잔액과 미수신(읽지 않은) 알람 개수를 한 번에 조회한다.")
	@GetMapping("/summary")
	fun getSummary(
		@LoginUser user: AuthUser,
	): ApiResponse<HomeSummaryResponse> =
		ApiResponse.success(
			HomeSummaryResponse(
				coinBalance = getCoinBalanceUseCase.getBalance(user.id).balance,
				unreadAlarmCount = getUnreadAlarmCountUseCase.getUnreadCount(user.id),
			),
		)
}
