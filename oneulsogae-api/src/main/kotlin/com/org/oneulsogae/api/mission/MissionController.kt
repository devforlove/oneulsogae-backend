package com.org.oneulsogae.api.mission

import com.org.oneulsogae.api.mission.response.ClaimMissionResponse
import com.org.oneulsogae.api.mission.response.MissionResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.mission.command.application.port.`in`.ClaimMissionUseCase
import com.org.oneulsogae.core.mission.query.service.port.`in`.GetMissionsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 미션 엔드포인트. (인증 필요)
 * - GET /missions/v1: 활성 미션 목록을 사용자별 상태(completed·eligible)와 함께 조회한다.
 * - POST /missions/v1/{missionId}/claim: 자격이 되면 보상 코인을 수령한다. (회원당 미션 1회)
 */
@Tag(name = "미션", description = "미션 목록 조회·보상 수령 엔드포인트 (인증 필요)")
@RestController
@RequestMapping("/missions/v1")
class MissionController(
	private val getMissionsUseCase: GetMissionsUseCase,
	private val claimMissionUseCase: ClaimMissionUseCase,
) {

	/** 활성 미션 목록을 조회한다. 각 미션에 completed(수령 완료)·eligible(지금 수령 가능) 상태가 실린다. */
	@Operation(
		summary = "미션 목록 조회",
		description = "활성 미션을 노출 순서대로 내려준다. 각 항목은 missionId·type·title·description·rewardCoin과, 요청 사용자의 completed(이미 수령)·eligible(미완료이면서 지금 자격 충족) 상태를 담는다.",
	)
	@GetMapping
	fun getMissions(
		@LoginUser user: AuthUser,
	): ApiResponse<List<MissionResponse>> =
		ApiResponse.success(MissionResponse.listOf(getMissionsUseCase.getMissions(user.id)))

	/** 미션 보상을 수령한다. 서버가 자격을 재검증하고 코인을 적립한다. */
	@Operation(
		summary = "미션 보상 수령",
		description = "자격을 서버가 재검증한 뒤 보상 코인을 적립한다. 없거나 비활성 미션이면 404(MISSION-001), 자격 미충족이면 400(MISSION-002), 이미 수령했으면 409(MISSION-003). 성공 시 지급 코인(rewardedCoin)과 적립 후 잔액(balance)을 반환한다.",
	)
	@PostMapping("/{missionId}/claim")
	fun claim(
		@LoginUser user: AuthUser,
		@PathVariable("missionId") missionId: Long,
	): ApiResponse<ClaimMissionResponse> =
		ApiResponse.success(ClaimMissionResponse.of(claimMissionUseCase.claim(user.id, missionId)))
}
