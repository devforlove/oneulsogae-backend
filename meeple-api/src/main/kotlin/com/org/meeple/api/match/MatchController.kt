package com.org.meeple.api.match

import com.org.meeple.api.match.response.MatchResponse
import com.org.meeple.api.match.response.MatchStatusResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.match.query.service.port.`in`.GetMatchesUseCase
import com.org.meeple.core.match.command.service.port.`in`.SendInterestUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 남녀 1:1 매칭 엔드포인트. (모두 인증 필요)
 * - GET  /: 내 매칭 목록을 조회한다. 온보딩 직후이면서 오늘 할당된 매칭이 없으면 한 명을 자동 소개한 뒤 목록을 반환한다.
 * - POST /{matchId}/interest: 소개받은 매칭에 관심을 보낸다. 상대가 이미 관심을 보냈으면 수락이 되어 성사된다. (신청/수락 통합)
 */
@RestController
@RequestMapping("/matches/v1")
class MatchController(
	private val getMatchesUseCase: GetMatchesUseCase,
	private val sendInterestUseCase: SendInterestUseCase,
) {

	/**
	 * 내 매칭 목록을 조회한다.
	 * [isAfterOnboarding]가 true(온보딩 직후 첫 진입)이고 오늘 할당된 매칭이 없으면 반대 성별 사용자 1명을 자동 소개한다.
	 * 일반 조회(기본값 false)에서는 신규 소개 없이 기존 매칭만 반환한다.
	 */
	@GetMapping
	fun myMatches(
		@LoginUser user: AuthUser,
		@RequestParam(name = "isAfterOnboarding", defaultValue = "false") isAfterOnboarding: Boolean,
	): ApiResponse<List<MatchResponse>> =
		ApiResponse.success(getMatchesUseCase.getMatches(user.id, isAfterOnboarding).map { MatchResponse.of(it) })

	/**
	 * 소개받은 매칭에 관심을 보낸다. (신청/수락 통합)
	 * 상대가 아직 관심을 안 보냈으면 신청(PARTIALLY_ACCEPTED), 이미 보냈으면 수락이 되어 성사(MATCHED)된다.
	 * 차감 코인(신청/수락 비용)은 매칭 상태로 서버가 산출하므로 요청 본문은 받지 않는다. 결과 매칭 상태만 반환한다.
	 */
	@PostMapping("/{matchId}/interest")
	fun sendInterest(
		@LoginUser user: AuthUser,
		@PathVariable matchId: Long,
	): ApiResponse<MatchStatusResponse> =
		ApiResponse.success(MatchStatusResponse.of(sendInterestUseCase.sendInterest(user.id, matchId)))
}
