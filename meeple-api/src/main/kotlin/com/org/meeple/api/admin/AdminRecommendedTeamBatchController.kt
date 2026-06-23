package com.org.meeple.api.admin

import com.org.meeple.api.admin.response.RecommendedTeamBatchResponse
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.match.MatchErrorCode
import com.org.meeple.scheduler.match.command.adapter.RecommendedTeamBatchJob
import com.org.meeple.scheduler.match.command.domain.RecommendedTeamBatchResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 전용 팀 추천 배치 엔드포인트. (admin 경로는 SecurityConfig에서 ROLE_ADMIN으로 제한)
 * 크론과 동일한 진입점([RecommendedTeamBatchJob])을 호출해 팀 추천 배치를 즉시(동기) 실행한다.
 */
@Tag(name = "관리자 - 팀 추천 배치", description = "관리자 전용 팀 추천 배치 엔드포인트. 크론과 동일한 진입점을 호출해 팀 추천 배치를 즉시 실행한다.")
@RestController
@RequestMapping("/admin/v1")
class AdminRecommendedTeamBatchController(
	private val recommendedTeamBatchJob: RecommendedTeamBatchJob,
) {

	/**
	 * 팀 추천 배치를 즉시 실행하고 결과를 반환한다.
	 * 이미 실행 중이면 [MatchErrorCode.MATCH_BATCH_ALREADY_RUNNING](409)을 던진다. (배치 진행 중 의미를 공유)
	 */
	@Operation(summary = "팀 추천 배치 즉시 실행", description = "팀 추천 배치를 즉시(동기) 실행하고 결과를 반환한다. 이미 실행 중이면 409를 반환한다.")
	@PostMapping("/teams/recommend-batch")
	fun runRecommendTeamBatch(): ApiResponse<RecommendedTeamBatchResponse> {
		val result: RecommendedTeamBatchResult = recommendedTeamBatchJob.run()
			?: throw BusinessException(MatchErrorCode.MATCH_BATCH_ALREADY_RUNNING)
		return ApiResponse.success(RecommendedTeamBatchResponse.of(result))
	}
}
