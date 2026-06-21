package com.org.meeple.api.popup

import com.org.meeple.api.popup.response.PopupResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.popup.query.service.port.`in`.GetPopupsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 팝업 엔드포인트. (인증 필요)
 * - GET /: 현재 노출 대상(노출 ON + 기간 내)인 팝업 목록을 display_order 오름차순으로 조회한다.
 *   노출 목록에 일일 보상(DAILY_REWARD) 팝업이 있으면 로그인 사용자에게 출석 코인을 적립한다. (하루 1회)
 */
@Tag(name = "팝업", description = "팝업 엔드포인트. 현재 노출 대상인 팝업 목록을 조회하고 출석 보상 팝업이 있으면 코인을 적립한다.")
@RestController
@RequestMapping("/popups/v1")
class PopupController(
	private val getPopupsUseCase: GetPopupsUseCase,
) {

	/** 현재 노출 중인 팝업 목록을 노출 순서대로 조회한다. (출석 보상 팝업이 있으면 출석 코인 적립) */
	@Operation(summary = "노출 팝업 목록 조회", description = "현재 노출 대상(노출 ON + 기간 내)인 팝업 목록을 display_order 오름차순으로 조회한다. 일일 보상 팝업이 포함되면 출석 코인을 적립한다(하루 1회).")
	@GetMapping
	fun visiblePopups(
		@LoginUser user: AuthUser,
	): ApiResponse<List<PopupResponse>> =
		ApiResponse.success(PopupResponse.listOf(getPopupsUseCase.getVisiblePopups(user.id)))
}
