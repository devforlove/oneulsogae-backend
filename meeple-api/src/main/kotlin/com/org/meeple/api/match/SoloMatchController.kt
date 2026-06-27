package com.org.meeple.api.match

import com.org.meeple.api.match.response.MatchResponse
import com.org.meeple.api.match.response.MatchStatusResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.query.service.port.`in`.GetMatchesUseCase
import com.org.meeple.core.match.command.application.port.`in`.EndMatchUseCase
import com.org.meeple.core.match.command.application.port.`in`.SendInterestUseCase
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RestController

/**
 * 남녀 1:1 매칭 엔드포인트. (모두 인증 필요)
 * - GET  /: 내 매칭 목록을 조회한다. (부수효과 없는 순수 조회 — 온보딩 직후 첫 소개는 회사 이메일 인증 완료 시점에 처리된다)
 * - POST /{matchId}/interest: 소개받은 매칭에 관심을 보낸다. 상대가 이미 관심을 보냈으면 수락이 되어 성사된다. (신청/수락 통합)
 * - DELETE /{matchId}: 성사된 매칭을 종료한다. 매칭을 제거하고 채팅방에서 본인을 내보낸 뒤 상대에게 나감을 알린다.
 */
@Tag(name = "매칭", description = "남녀 1:1 매칭 엔드포인트. 매칭 목록 조회 및 관심 보내기(신청/수락 통합)를 제공한다.")
@RestController
@RequestMapping("/matches/v1")
class SoloMatchController(
	private val getMatchesUseCase: GetMatchesUseCase,
	private val sendInterestUseCase: SendInterestUseCase,
	private val endMatchUseCase: EndMatchUseCase,
	private val timeGenerator: TimeGenerator,
) {

	/**
	 * 내 매칭 목록을 조회한다. (부수효과 없는 순수 조회)
	 * 온보딩 직후 첫 매칭 자동 소개는 더 이상 이 조회가 아니라 회사 이메일 인증 완료 시점에 생성된다.
	 */
	@Operation(summary = "내 매칭 목록 조회", description = "내 매칭 목록을 반환한다.")
	@GetMapping
	fun myMatches(
		@LoginUser user: AuthUser,
	): ApiResponse<List<MatchResponse>> =
		ApiResponse.success(MatchResponse.listOf(getMatchesUseCase.getMatches(user.id), timeGenerator.today()))

	/**
	 * 소개받은 매칭에 관심을 보낸다. (신청/수락 통합)
	 * 상대가 아직 관심을 안 보냈으면 신청(PARTIALLY_ACCEPTED), 이미 보냈으면 수락이 되어 성사(MATCHED)된다.
	 * 차감 코인(신청/수락 비용)은 매칭 상태로 서버가 산출하므로 요청 본문은 받지 않는다. 결과 매칭 상태만 반환한다.
	 */
	@Operation(summary = "매칭 관심 보내기 (신청/수락 통합)", description = "소개받은 매칭에 관심을 보낸다. 상대가 아직 관심을 보내지 않았으면 신청(PARTIALLY_ACCEPTED), 이미 보냈으면 수락이 되어 성사(MATCHED)된다.")
	@PostMapping("/{matchId}/interest")
	fun sendInterest(
		@LoginUser user: AuthUser,
		@PathVariable matchId: Long,
	): ApiResponse<MatchStatusResponse> =
		ApiResponse.success(MatchStatusResponse.of(sendInterestUseCase.sendInterest(user.id, matchId)))

	/**
	 * 성사된 매칭을 종료한다. (요청 사용자는 그 매칭의 참가자여야 하고, 매칭이 성사(MATCHED) 상태여야 한다)
	 * 매칭(헤더+참가자)을 종료(CLOSED)·소프트 삭제하고, 연결된 채팅방에서 본인 참가만 비활성화하면서 상대에게 "상대방이 매칭을 종료했어요" 안내를 남긴다.
	 * 채팅방은 닫지 않아 상대는 그대로 방을 유지한다. 관계(매칭)를 끝내는 의미이므로 DELETE로 둔다.
	 */
	@Operation(summary = "매칭 종료", description = "성사된 매칭을 종료한다. 매칭을 제거하고 연결된 채팅방에서 본인을 내보낸 뒤, 방에 남는 상대에게 '상대방이 매칭을 종료했어요' 안내 메세지를 남긴다.")
	@DeleteMapping("/{matchId}")
	fun endMatch(
		@LoginUser user: AuthUser,
		@PathVariable matchId: Long,
	): ApiResponse<Unit> {
		endMatchUseCase.endMatch(user.id, matchId)
		return ApiResponse.success()
	}
}
